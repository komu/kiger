package kiger.canon

import kiger.ir.tree.TreeBasicBlock
import kiger.ir.tree.TreeControlFlowGraph
import kiger.ir.tree.TreeExp.Name
import kiger.ir.tree.TreeStm.Branch.CJump
import kiger.ir.tree.TreeStm.Branch.Jump
import kiger.temp.Label

/**
 * Builds a trace from [TreeControlFlowGraph].
 *
 * The trace satisfies the following conditions:
 *
 *   1. No SEQ's or ESEQ's (guarantee from [linearize])
 *   2. The parent of every CALL is an EXP(..) or a MOVE(TEMP t,..) (guarantee from [linearize])
 *   3. Every CJUMP(_,t,f) is immediately followed by LABEL f.
 *
 * Basic blocks are reordered as necessary to satisfy property 3.
 *
 * In addition to this, the scheduler also tries to arrange possibilities for eliminating
 * `Jump(Name(lab))` statements by trying to write the target immediately after the jump,
 * so that the peephole optimizer may later eliminate the jump in favor of simply falling through.
 */
fun TreeControlFlowGraph.traceSchedule(): TreeControlFlowGraph {
    val scheduler = TraceScheduler(blocks)
    scheduler.schedule()
    return TreeControlFlowGraph(scheduler.output, exitLabel)
}

private class TraceScheduler(private val blocks: List<TreeBasicBlock>) {

    /**
     * Mapping from labels to all untraced blocks. Initially will contain all blocks to schedule.
     */
    private val untracedBlocks = mutableMapOf<Label, TreeBasicBlock>().apply {
        for (b in blocks)
            this[b.label] = b
    }

    /** The result of the processing */
    val output = mutableListOf<TreeBasicBlock>()

    /**
     * Processes all blocks from the work-queue to build a trace.
     *
     * Most we'll go through the block in order, but [schedule] will attempt to trace the
     * branches immediately after blocks, which means that when we process a block, we'll
     * have to check if it actually has already been processed and skip it if if has been.
     */
    fun schedule() {
        for (block in blocks)
            if (block.label in untracedBlocks)
                trace(block)
    }

    /**
     * Traces one or more blocks. Will always write the trace of [block] to output,
     * but also tries to trace the following blocks near this block unless they have
     * already been traced.
     */
    private tailrec fun trace(block: TreeBasicBlock) {
        val b = untracedBlocks.remove(block.label)
        check(b != null) { "attempted to re-trace a block: ${block.label}" }

        val br = block.branch
        when (br) {
            is Jump  -> {
                output += block
                if (br.target is Name) {
                    // If we see an unconditional jump at the end of block, try to write
                    // the target immediately after this block so that later peephole
                    // optimization can eliminate the jump.

                    // TODO: if the label is not used elsewhere, merge the blocks (is this even possible?)
                    val targetBlock = untracedBlocks[br.target.name]
                    if (targetBlock != null)
                        trace(targetBlock)
                }
            }
            is CJump -> {
                val trueTarget = untracedBlocks[br.trueLabel]
                val falseTarget = untracedBlocks[br.trueLabel]

                // Try to get the false-block immediately after condition. If that fails, negate
                // the condition and try to get the true block immediately after the condition.
                // If both true and false blocks have already been traced, write a new false-branch
                // immediately after condition that just jumps to the real false-block.
                when {
                    falseTarget != null -> {
                        output += block
                        trace(falseTarget)
                    }
                    trueTarget != null -> {
                        output += TreeBasicBlock(block.label, block.body, CJump(br.op.not(), br.lhs, br.rhs, br.falseLabel, br.trueLabel))
                        trace(trueTarget)
                    }
                    else -> {
                        val f = Label.gen("false")
                        output += TreeBasicBlock(block.label, block.body, CJump(br.op, br.lhs, br.rhs, br.trueLabel, f))
                        output += TreeBasicBlock(f, emptyList(), Jump(br.falseLabel))
                    }
                }
            }
        }
    }
}
