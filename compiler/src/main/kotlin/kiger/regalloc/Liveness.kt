package kiger.regalloc

import kiger.regalloc.InterferenceGraph.INode
import kiger.regalloc.InterferenceGraph.Move
import kiger.temp.Temp

/**
 * Constructs interference graph from [FlowGraph].
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

fun FlowGraph.initializeLiveOuts() {
    val liveoutMap = buildLiveOutMap()

    // set liveout for each node
    for (node in nodes) {
        val s = liveoutMap[node.id]
        if (s != null)
            node.liveOut = s
        else
            error("liveout map is not one-to-one")
    }
}

/**
 * Creates a map which contain the liveout set for every node of [FlowGraph].
 */
private fun FlowGraph.buildLiveOutMap(): Map<Int, Set<Temp>> {
    val liveinMap = mutableMapOf<Int, Set<Temp>>()
    val liveoutMap = mutableMapOf<Int, Set<Temp>>()

    for (n in nodes) {
        liveinMap[n.id] = emptySet()
        liveoutMap[n.id] = emptySet()
    }

    do {
        var changed = false

        for (n in nodes.asReversed()) {
            val oldIn = liveinMap[n.id]!!
            val oldOut = liveoutMap[n.id]!!
            val newIn = n.use + (oldOut - n.def)
            val newOut = n.computeOut(liveinMap)

            if (newIn != oldIn || newOut != oldOut) {
                changed = true
                liveinMap[n.id] = newIn
                liveoutMap[n.id] = newOut
            }
        }
    } while (changed)

    return liveoutMap
}

/**
 * Compute liveout set for a node, given a livein map.
  */
private fun FlowGraph.Node.computeOut(liveinMap: Map<Int, Set<Temp>>): Set<Temp> {
    val set = mutableSetOf<Temp>()
    for (s in succ)
        set += liveinMap[s.id]!!
    return set
}

