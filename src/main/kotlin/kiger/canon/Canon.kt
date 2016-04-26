package kiger.canon

import kiger.temp.Label
import kiger.temp.Temp
import kiger.tree.TreeExp
import kiger.tree.TreeStm

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
 * Also produce the "label" to which control will be passed upon exit.
 */
fun basicBlocks(stmts: List<TreeStm>): BasicBlockGraph = TODO()

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
fun traceSchedule(blocks: BasicBlockGraph): List<TreeStm> = TODO()

data class BasicBlock(val statements: List<TreeStm>)
data class BasicBlockGraph(val blocks: List<BasicBlock>, val exitLabel: Label)

