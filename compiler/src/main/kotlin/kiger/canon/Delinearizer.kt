package kiger.canon

import kiger.ir.tree.TreeBasicBlock
import kiger.ir.tree.TreeControlFlowGraph

/**
 * Delinearize all basic blocks of the graph.
 */
fun TreeControlFlowGraph.delinearize(): TreeControlFlowGraph =
    TreeControlFlowGraph(blocks.map { it.delinearize() }, exitLabel)

/**
 * Performs selective delinearization of statements.
 */
private fun TreeBasicBlock.delinearize(): TreeBasicBlock {
    // TODO: to delinearize, we need live-out information on basic blocks

    // Sketch:
    //
    // - Count uses of all defs (live-out counts as use)
    // - Consider inlining defs with single use
    //   - Inlining is safe to next instruction
    //   - Or as long as the def commutes with all instructions between it and target (optional improvement)
    //
    // Note: remember to consider ending branch as well even though it's not in the instruction-stream

    // This is certainly not optimal, but it's valid.
    return this;
}

