package kiger.regalloc

import kiger.assem.InstrControlFlowGraph
import kiger.regalloc.InterferenceGraph.INode
import kiger.regalloc.InterferenceGraph.Move
import kiger.temp.Temp
import kiger.utils.profile
import java.util.*

// TODO: construct interference graph from original Cfg
fun InstrControlFlowGraph.interferenceGraph(): InterferenceGraph =
    toInstrs().createFlowGraph().interferenceGraph()

/**
 * Constructs an interference graph from [FlowGraph].
 */
fun FlowGraph.interferenceGraph(): InterferenceGraph {
    val liveout = buildLiveOuts()

    val nodeByTemp = mutableMapOf<Temp, INode>()

    val allDefs = nodes.asSequence().flatMap { it.def.asSequence() + it.use.asSequence() }

    for (t in allDefs)
        if (t !in nodeByTemp)
            nodeByTemp[t] = INode(t)

    val allMoves = nodes.asSequence().filter { it.isMove }.map { Move(nodeByTemp[it.use.single()]!!, nodeByTemp[it.def.single()]!!) }.toList()

    for (m in allMoves) {
        m.src.moveList += m
        m.dst.moveList += m
    }

    val gr = InterferenceGraph(nodeByTemp.values.toList(), allMoves)
    for (i in nodes)
        for (d in i.def)
            for (l in liveout[i]!!)
                gr.addEdge(gr.nodeForTemp(l), gr.nodeForTemp(d))

    return gr
}

/**
 * Computers the liveout sets for all nodes in the graph.
 */
fun FlowGraph.buildLiveOuts(): Map<FlowGraph.Node, Set<Temp>> {
    val liveoutMap = profile("buildLiveOutMap") { buildLiveOutMap() }

    val result = IdentityHashMap<FlowGraph.Node, Set<Temp>>()
    for (node in nodes)
        result[node] = liveoutMap[node.id]
    return result
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
