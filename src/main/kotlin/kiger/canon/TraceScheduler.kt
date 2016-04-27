package kiger.canon

import kiger.temp.Label
import kiger.tree.TreeExp
import kiger.tree.TreeStm
import kiger.utils.splitFirst
import kiger.utils.splitLast

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

    tailrec fun getNext(rest: List<BasicBlock>): List<TreeStm> {
        if (rest.isEmpty()) return emptyList()

        val (head, tail) = rest.splitFirst()

        val block = unprocessedBlocks[head.label]
        return if (block != null) {
            trace(block, tail)
        } else {
            getNext(tail)
        }
    }

    fun trace(block: BasicBlock, rest: List<BasicBlock>): List<TreeStm> {
        unprocessedBlocks.remove(block.label) // mark the block as processed

        val (most, last) = block.statements.splitLast()
        return when (last) {
            is TreeStm.Jump  -> {
                if (last.exp is TreeExp.Name) {
                    val b2 = unprocessedBlocks[last.exp.label]
                    if (b2 != null) {
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
            else             ->
                error("invalid last node of basic block: $last")
        }
    }
}
