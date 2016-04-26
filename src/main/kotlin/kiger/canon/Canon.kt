package kiger.canon

import kiger.temp.Label
import kiger.temp.Temp
import kiger.tree.TreeExp
import kiger.tree.TreeStm
import java.util.*

/**
 * From an arbitrary Tree statement, produce a list of cleaned trees satisfying the following properties:
 *
 * 1. No SEQ's or ESEQ's
 * 2. The parent of every CALL is an EXP(..) or a MOVE(TEMP t,..)
 **/
fun TreeStm.linearize(): List<TreeStm> =
    Linearizer.doStm(this).linearizeTopLevel().toList()

private object Linearizer {
    val nop = TreeStm.Exp(TreeExp.Const(0))

    fun reorder(exps: List<TreeExp>): Pair<TreeStm, List<TreeExp>> = when {
        exps.isEmpty() ->
            Pair(nop, emptyList())

        exps[0] is TreeExp.Call -> {
            val t = Temp()
            val rest = exps.subList(1, exps.size)
            reorder(listOf(TreeExp.ESeq(TreeStm.Move(TreeExp.Temporary(t), exps[0]), TreeExp.Temporary(t))) + rest)
        }

        else -> {
            val (stms, e) = doExp(exps[0])
            val (stms2, el) = reorder(exps.subList(1, exps.size))

            if (stms2.commute(e)) {
                Pair(stms % stms2, listOf(e) + el)
            } else {
                val t = Temp()
                Pair(stms % TreeStm.Move(TreeExp.Temporary(t), e) % stms2, listOf(TreeExp.Temporary(t)) + el)
            }
        }
    }

    fun reorderExp(el: List<TreeExp>, build: (List<TreeExp>) -> TreeExp): Pair<TreeStm, TreeExp> {
        val (stms, el2) = reorder(el)
        return Pair(stms, build(el2))
    }

    fun reorderStm(el: List<TreeExp>, build: (List<TreeExp>) -> TreeStm): TreeStm {
        val (stms, el2) = reorder(el)
        return stms % build(el2)
    }

    fun doStm(stm: TreeStm): TreeStm = when (stm) {
        is TreeStm.Seq     -> doStm(stm.lhs) % doStm(stm.rhs)
        is TreeStm.Jump    -> reorderStm(listOf(stm.exp)) { TreeStm.Jump(it[0], stm.labels) }
        is TreeStm.CJump   -> reorderStm(listOf(stm.lhs, stm.rhs)) { TreeStm.CJump(stm.relop, it[0], it[1], stm.trueLabel, stm.falseLabel) }
        is TreeStm.Move    -> when (stm.target) {
            is TreeExp.Temporary ->
                if (stm.source is TreeExp.Call)
                    reorderStm(listOf(stm.source.func) + stm.source.args) { TreeStm.Move(TreeExp.Temporary(stm.target.temp), TreeExp.Call(it[0], it.subList(1, it.size))) }
                else
                    reorderStm(listOf(stm.source)) { TreeStm.Move(TreeExp.Temporary(stm.target.temp), it[0]) }
            is TreeExp.Mem -> reorderStm(listOf(stm.target.exp, stm.source)) { val (e, b) = it; TreeStm.Move(TreeExp.Mem(e), b) }
            is TreeExp.ESeq -> doStm(TreeStm.Seq(stm.target.stm, TreeStm.Move(stm.target.exp, stm.source)))
            else -> error("invalid target: ${stm.target}")
        }
        is TreeStm.Exp     ->
            if (stm.exp is TreeExp.Call)
                reorderStm(listOf(stm.exp.func) + stm.exp.args) { TreeStm.Exp(TreeExp.Call(it[0], it.subList(1, it.size))) }
            else
                reorderStm(listOf(stm.exp)) { TreeStm.Exp(it[0]) }
        is TreeStm.Labeled -> reorderStm(emptyList()) { stm }
    }

    fun doExp(exp: TreeExp): Pair<TreeStm, TreeExp> = when (exp) {
        is TreeExp.BinOp    -> reorderExp(listOf(exp.lhs, exp.rhs)) { val (a, b) = it; TreeExp.BinOp(exp.binop, a, b) }
        is TreeExp.Mem      -> reorderExp(listOf(exp.exp)) { TreeExp.Mem(it[0]) }
        is TreeExp.ESeq     -> {
            val stms = doStm(exp.stm)
            val (stms2, e) = doExp(exp.exp)
            Pair(stms % stms2, e)
        }
        is TreeExp.Call     -> reorderExp(listOf(exp.func) + exp.args) { TreeExp.Call(it[0], it.subList(0, it.size)) }
        else                -> reorderExp(emptyList()) { exp }
    }
}

/**
 * Merge two statements into sequence, removing constant expressions.
 */
private operator fun TreeStm.mod(rhs: TreeStm): TreeStm = when {
    this is TreeStm.Exp && exp is TreeExp.Const -> rhs
    rhs is TreeStm.Exp && rhs.exp is TreeExp.Const -> this
    else -> TreeStm.Seq(this, rhs)
}

/**
 * Calculates conservative approximation to whether expressions commute.
 */
private fun TreeStm.commute(rhs: TreeExp): Boolean = when {
    this is TreeStm.Exp && exp is TreeExp.Const -> true
    rhs is TreeExp.Name                         -> true
    rhs is TreeExp.Const                        -> true
    else                                        -> false
}

/**
 * Get rid of the top-level SEQ's
 */
private fun TreeStm.linearizeTopLevel(): Sequence<TreeStm> =
    if (this is TreeStm.Seq) {
        lhs.linearizeTopLevel() + rhs.linearizeTopLevel()
    } else {
        sequenceOf(this)
    }


/**
 * From a list of cleaned trees, produce a list of basic blocks satisfying the following properties:
 *
 * 1. as above;
 * 2. as above;
 * 3. Every block begins with a LABEL;
 * 4. A LABEL appears only at the beginning of a block;
 * 5. Any JUMP or CJUMP is the last stm in a block;
 * 6. Every block ends with a JUMP or CJUMP;
 *
 * Also produce the label to which control will be passed upon exit.
 */
fun List<TreeStm>.basicBlocks(): BasicBlockGraph {

    val blockBuilder = BasicBlockBuilder()

    for (stm in this) {
        // First make sure that each basic-block starts with a label
        if (blockBuilder.isCurrentEmpty() && stm !is TreeStm.Labeled)
            blockBuilder += TreeStm.Labeled(Label())

        when (stm) {
            is TreeStm.Jump,
            is TreeStm.CJump -> {
                blockBuilder += stm
                blockBuilder.finishBlock()
            }
            is TreeStm.Labeled -> {
                // Labels start a new block unless we are already at the beginning of a block
                if (!blockBuilder.isCurrentEmpty()) {
                    blockBuilder.addJumpTo(stm.label)
                    blockBuilder.finishBlock()
                }

                blockBuilder += stm
            }
            else -> {
                blockBuilder += stm
            }
        }
    }

    return blockBuilder.build()
}

private class BasicBlockBuilder {

    val exitLabel = Label()
    private val blocks = mutableListOf<BasicBlock>()
    private val currentBlock = mutableListOf<TreeStm>()

    operator fun plusAssign(stm: TreeStm) {
        currentBlock += stm
    }

    fun finishBlock() {
        blocks += BasicBlock(ArrayList(currentBlock))
        currentBlock.clear()
    }

    fun isCurrentEmpty(): Boolean = currentBlock.isEmpty()

    fun build(): BasicBlockGraph {
        if (!currentBlock.isEmpty()) {
            val last = currentBlock.last()
            if (last !is TreeStm.Jump && last !is TreeStm.CJump)
                addJumpTo(exitLabel)

            finishBlock()
        }

        return BasicBlockGraph(blocks, exitLabel)
    }

    fun addJumpTo(label: Label) {
        currentBlock += TreeStm.Jump(TreeExp.Name(label), listOf(label))
    }
}

/**
 * From a list of basic blocks satisfying properties 1-6, along with an "exit" label,
 * produce a list of stms such that:
 *
 * 1. and 2. as above;
 * 7. Every CJUMP(_,t,f) is immediately followed by LABEL f.
 *
 * The blocks are reordered to satisfy property 7; also in this reordering as many JUMP(T.NAME(lab)) statements
 * as possible are eliminated by falling through into T.LABEL(lab).
 */
fun BasicBlockGraph.traceSchedule(): List<TreeStm> {
    return blocks.flatMap { it.statements } // TODO
}

/*
  fun enterblock(b as (T.LABEL s :: _), table) = Symbol.enter(table,s,b)
    | enterblock(_, table) = table

  fun splitlast([x]) = (nil,x)
    | splitlast(h::t) = let val (t',last) = splitlast t in (h::t', last) end

  fun trace(table,b as (T.LABEL lab :: _),rest) =
   let val table = Symbol.enter(table, lab, nil)
    in case splitlast b
     of (most,T.JUMP(T.NAME lab, _)) =>
	  (case Symbol.look(table, lab)
            of SOME(b' as _::_) => most @ trace(table, b', rest)
	     | _ => b @ getnext(table,rest))
      | (most,T.CJUMP(opr,x,y,t,f)) =>
          (case (Symbol.look(table,t), Symbol.look(table,f))
            of (_, SOME(b' as _::_)) => b @ trace(table, b', rest)
             | (SOME(b' as _::_), _) =>
		           most @ [T.CJUMP(T.notRel opr,x,y,f,t)]
		                @ trace(table, b', rest)
             | _ => let val f' = Temp.newlabel()
		     in most @ [T.CJUMP(opr,x,y,t,f'),
				T.LABEL f', T.JUMP(T.NAME f,[f])]
			     @ getnext(table,rest)
                        end)
      | (most, T.JUMP _) => b @ getnext(table,rest)
     end

  and getnext(table,(b as (T.LABEL lab::_))::rest) =
           (case Symbol.look(table, lab)
             of SOME(_::_) => trace(table,b,rest)
              | _ => getnext(table,rest))
    | getnext(table,nil) = nil

  fun traceSchedule(blocks,done) =
       getnext(foldr enterblock Symbol.empty blocks, blocks)
         @ [T.LABEL done]


 */
data class BasicBlock(val statements: List<TreeStm>)
data class BasicBlockGraph(val blocks: List<BasicBlock>, val exitLabel: Label)

