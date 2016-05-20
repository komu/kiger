package kiger.regalloc

import kiger.assem.Instr
import kiger.assem.InstrControlFlowGraph
import kiger.regalloc.InterferenceGraph.INode
import kiger.regalloc.InterferenceGraph.Move
import kiger.temp.Temp
import kiger.utils.profile
import java.util.*

/**
 * Constructs an interference graph from [FlowGraph].
 */
fun InstrControlFlowGraph.interferenceGraph(): InterferenceGraph {
    val liveout = buildLiveOuts()

    val nodesByTemp = allTemporaries().map { Pair(it, INode(it)) }.toMap()

    val moves = moves().map { Move(nodesByTemp[it.src]!!, nodesByTemp[it.dst]!!) }.toList()

    for (m in moves) {
        m.src.moveList += m
        m.dst.moveList += m
    }

    val gr = InterferenceGraph(nodesByTemp.values.toList(), moves)
    for (i in toInstrs())
        for (d in i.defs)
            for (l in liveout[i]!!)
                gr.addEdge(gr.nodeForTemp(l), gr.nodeForTemp(d))

    return gr
}

/**
 * Computers the liveout sets for all nodes in the graph.
 */
fun InstrControlFlowGraph.buildLiveOuts(): Map<Instr, Set<Temp>> {
    // TODO: don't flatten the original CFG, but leverage it for calculation
    val flowGraph = toInstrs().createFlowGraph()
    val liveoutMap = profile("buildLiveOutMap") { flowGraph.buildLiveOutMap() }

    val result = IdentityHashMap<Instr, Set<Temp>>()
    for (node in flowGraph.nodes)
        result[node.instr] = liveoutMap[node.id]
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
