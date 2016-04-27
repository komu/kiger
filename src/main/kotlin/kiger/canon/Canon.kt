package kiger.canon

import kiger.temp.Label
import kiger.temp.Temp
import kiger.tree.TreeExp
import kiger.tree.TreeStm
import kiger.utils.cons
import kiger.utils.splitFirst
import kiger.utils.splitLast
import kiger.utils.tail
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
            val rest = exps.tail()
            reorder(cons(TreeExp.ESeq(TreeStm.Move(TreeExp.Temporary(t), exps[0]), TreeExp.Temporary(t)), rest))
        }

        else -> {
            val (head, tail) = exps.splitFirst()
            val (stms, e) = doExp(head)
            val (stms2, el) = reorder(tail)

            if (stms2.commute(e)) {
                Pair(stms % stms2, cons(e, el))
            } else {
                val t = Temp()
                Pair(stms % TreeStm.Move(TreeExp.Temporary(t), e) % stms2, cons(TreeExp.Temporary(t), el))
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
        is TreeStm.Jump    -> reorderStm(listOf(stm.exp)) { TreeStm.Jump(it.single(), stm.labels) }
        is TreeStm.CJump   -> reorderStm(listOf(stm.lhs, stm.rhs)) { val (l, r) = it; TreeStm.CJump(stm.relop, l, r, stm.trueLabel, stm.falseLabel) }
        is TreeStm.Move    -> when (stm.target) {
            is TreeExp.Temporary ->
                if (stm.source is TreeExp.Call)
                    reorderStm(cons(stm.source.func, stm.source.args)) { val (h, t) = it.splitFirst(); TreeStm.Move(TreeExp.Temporary(stm.target.temp), TreeExp.Call(h, t)) }
                else
                    reorderStm(listOf(stm.source)) { TreeStm.Move(TreeExp.Temporary(stm.target.temp), it.single()) }
            is TreeExp.Mem -> reorderStm(listOf(stm.target.exp, stm.source)) { val (e, b) = it; TreeStm.Move(TreeExp.Mem(e), b) }
            is TreeExp.ESeq -> doStm(TreeStm.Seq(stm.target.stm, TreeStm.Move(stm.target.exp, stm.source)))
            else -> error("invalid target: ${stm.target}")
        }
        is TreeStm.Exp     ->
            if (stm.exp is TreeExp.Call)
                reorderStm(cons(stm.exp.func, stm.exp.args)) { val (h, t) = it.splitFirst(); TreeStm.Exp(TreeExp.Call(h, t)) }
            else
                reorderStm(listOf(stm.exp)) { TreeStm.Exp(it.single()) }
        is TreeStm.Labeled -> reorderStm(emptyList()) { stm }
    }

    fun doExp(exp: TreeExp): Pair<TreeStm, TreeExp> = when (exp) {
        is TreeExp.BinOp    -> reorderExp(listOf(exp.lhs, exp.rhs)) { val (a, b) = it; TreeExp.BinOp(exp.binop, a, b) }
        is TreeExp.Mem      -> reorderExp(listOf(exp.exp)) { TreeExp.Mem(it.single()) }
        is TreeExp.ESeq     -> {
            val stms = doStm(exp.stm)
            val (stms2, e) = doExp(exp.exp)
            Pair(stms % stms2, e)
        }
        is TreeExp.Call     -> reorderExp(cons(exp.func, exp.args)) { val (h, t) = it.splitFirst(); TreeExp.Call(h, t) }
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
fun BasicBlockGraph.traceSchedule(): List<TreeStm> =
    TraceScheduler(blocks).getNext(blocks) + TreeStm.Labeled(exitLabel)

private class TraceScheduler(blocks: List<BasicBlock>) {

    private val unprocessedBlocks = mutableMapOf<Label, BasicBlock>().apply {
        for (b in blocks)
            this[b.label] = b
    }

    fun getNext(rest: List<BasicBlock>): List<TreeStm> {
        if (rest.isEmpty()) return emptyList()

        val (head, tail) = rest.splitFirst()

        val block = unprocessedBlocks[head.label]
        return if (block != null && block.statements.size > 0) {
            trace(block, tail)
        } else {
            getNext(tail)
        }
    }

    fun trace(block: BasicBlock, rest: List<BasicBlock>): List<TreeStm> {
        unprocessedBlocks.remove(block.label) // mark the block as processed

        val (most, last) = block.statements.splitLast()
        return when (last) {
            is TreeStm.Jump -> {
                if (last.exp is TreeExp.Name) {
                    val b2 = unprocessedBlocks[last.exp.label]
                    if (b2 != null && b2.statements.size > 0) {
                        most + trace(b2, rest)
                    } else {
                        block.statements + getNext(rest)
                    }

                } else {
                    block.statements + getNext(rest)
                }
            }
            is TreeStm.CJump -> {
                val trueBlock = unprocessedBlocks[last.trueLabel]
                val falseBlock = unprocessedBlocks[last.trueLabel]

                if (falseBlock != null && !falseBlock.isEmpty()) {
                    block.statements + trace(falseBlock, rest)
                } else if (trueBlock != null && !trueBlock.isEmpty()) {
                    most + TreeStm.CJump(last.relop.not(), last.lhs, last.rhs, last.falseLabel, last.trueLabel) + trace(trueBlock, rest)
                } else {
                    val f = Label()
                    most + TreeStm.CJump(last.relop, last.lhs, last.rhs, last.trueLabel, f) +
                            TreeStm.Labeled(f) + TreeStm.Jump(TreeExp.Name(f), listOf(f)) +
                            getNext(rest)
                }
            }
            else ->
                error("invalid last node of basic block: $last")
        }
    }
}

data class BasicBlock(val statements: List<TreeStm>) {
    init {
        require(statements.any()) { "empty basic block" }
        require(statements.first() is TreeStm.Labeled) { "basic block without start label: $statements" }
        require(statements.last().isBranch) { "basic block without ending branch: $statements" }
    }

    val label: Label
        get() = (statements.first() as TreeStm.Labeled).label

    fun isEmpty() = statements.isEmpty()
}
data class BasicBlockGraph(val blocks: List<BasicBlock>, val exitLabel: Label)

