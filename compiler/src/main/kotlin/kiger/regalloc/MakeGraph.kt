package kiger.regalloc

import kiger.assem.Instr
import kiger.temp.Label

/**
 * Create a data flow graph from list of instructions.
 *
 * Algorithm:
 * first pass: make node for each instr,
 * second pass: for each OPER instr, search in the instr list
 *  for a label that it jumps to, and make a new edge from the
 *  node of this instr to the node of the label.
 * third pass: connect all the nodes in sequential order (which
 *  are not connected by explicit jump in the original instr)
 *
 * This is probably too inefficient, as it requires O(n^3) time.
 * But, I'll leave improvement for future.
 */
fun List<Instr>.createFlowGraph(): FlowGraph {
    // we have to maintain the order of node-list wrt instrs
    val nodeList = mapIndexed { i, instr -> instr.makeNode(i) }

    val compList = this.zip(nodeList)

    for ((instr, node) in compList)
        compList.doJump(instr, node)

    compList.connect()

    return FlowGraph(nodeList)
}

private fun Instr.makeNode(id: Int): FlowGraph.Node = when (this) {
    is Instr.Oper   -> FlowGraph.Node(id, dst, src, false)
    is Instr.Lbl    -> FlowGraph.Node(id, emptyList(), emptyList(), false)
    is Instr.Move   -> FlowGraph.Node(id, listOf(dst), listOf(src), true)
}

private fun makeEdge(from: FlowGraph.Node, to: FlowGraph.Node) {
    if (to !in from.succ) {
        from.succ += to
        to.prev += from
    }
}

// only connect x with y if x doesn't jump
private fun List<Pair<Instr, FlowGraph.Node>>.connect() {

    for (i in 0..lastIndex-1) {
        val (op, node) = this[i]
        if (!op.isJump)
            makeEdge(node, this[i+1].second)
    }
}

private fun List<Pair<Instr, FlowGraph.Node>>.findNodeByLabel(label: Label): FlowGraph.Node =
    find {
        var instr = it.first
        instr is Instr.Lbl && instr.label == label
    }!!.second

private fun List<Pair<Instr, FlowGraph.Node>>.doJump(instr: Instr, node: FlowGraph.Node) {
    if (instr is Instr.Oper && instr.jump != null)
        for (label in instr.jump)
            makeEdge(node, findNodeByLabel(label))
}
