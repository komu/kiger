package kiger.regalloc

import kiger.regalloc.InterferenceGraph.INode
import kiger.regalloc.InterferenceGraph.Move
import kiger.temp.Temp
import java.util.*

/**
 * Constructs an interference graph from [FlowGraph].
 */
fun FlowGraph.interferenceGraph(): InterferenceGraph {
    initializeLiveOuts()

    val nodeByTemp = mutableMapOf<Temp, INode>()

    val allDefs = nodes.asSequence().flatMap { it.def.asSequence() + it.use.asSequence() }

    for (t in allDefs)
        if (t !in nodeByTemp)
            nodeByTemp[t] = INode(t)

    val allMoves = nodes.asSequence().filter { it.isMove }.map { Move(nodeByTemp[it.use.single()]!!, nodeByTemp[it.def.single()]!!) }.toList()

    return InterferenceGraph(nodeByTemp.values.toList(), allMoves)
}

/**
 * Computers the liveout sets for all nodes in the graph.
 */
fun FlowGraph.initializeLiveOuts() {
    val liveoutMap = buildLiveOutMap()

    for (node in nodes)
        node.liveOut = liveoutMap[node.id]
}

/**
 * Creates an array which contain the liveout set for every node in the graph.
 */
private fun FlowGraph.buildLiveOutMap(): Array<Set<Temp>> {
    val liveIn = Array<Set<Temp>>(size) { emptySet() }
    val liveOut = Array<Set<Temp>>(size) { emptySet() }

    do {
        var changed = false

        for (n in nodes.asReversed()) {
            val oldIn = liveIn[n.id]
            val oldOut = liveOut[n.id]
            val newIn = n.use + (oldOut - n.def)
            val newOut = n.computeOut(liveIn)

            if (newIn != oldIn || newOut != oldOut) {
                changed = true
                liveIn[n.id] = newIn
                liveOut[n.id] = newOut
            }
        }
    } while (changed)

    return liveOut
}

/**
 * Compute liveout set for a node, given a livein map.
  */
private fun FlowGraph.Node.computeOut(liveinMap: Array<Set<Temp>>): Set<Temp> {
    val set = TreeSet<Temp>()
    for (s in succ)
        set += liveinMap[s.id]
    return set
}
