package kiger.regalloc

import kiger.assem.Instr
import kiger.assem.InstrBasicBlock
import kiger.assem.InstrControlFlowGraph
import kiger.temp.Temp
import kiger.utils.profile
import java.util.*

/**
 * Constructs an interference graph from [FlowGraph].
 */
fun InstrControlFlowGraph.interferenceGraph(): InterferenceGraph {
    val liveout = buildLiveOutsForBasicBlocks()

    val graph = InterferenceGraph(allTemporaries())

    for (b in blocks) {
        val live = liveout[b]!!.toMutableSet()
        for (i in b.body.asReversed()) {
            val defs = i.defs
            val uses = i.uses

            if (i is Instr.Move) {
                live -= uses

                graph.addMove(i.src, i.dst)
            }

            live += defs
            
            for (d in defs)
                for (l in live)
                    graph.addEdge(l, d)

            live -= defs
            live += uses
        }
    }

    return graph
}

/**
 * Computers the liveout sets for all nodes in the graph.
 */
fun InstrControlFlowGraph.buildLiveOutsForBasicBlocks(): Map<InstrBasicBlock, Set<Temp>> {
    // TODO: don't flatten the original CFG, but leverage it for calculation
    val flowGraph = toInstrs().createFlowGraph()
    val liveoutMap = profile("buildLiveOutMap") { flowGraph.buildLiveOutMap() }

    val instrOuts = IdentityHashMap<Instr, Set<Temp>>()
    for (node in flowGraph.nodes)
        instrOuts[node.instr] = liveoutMap[node.id]

    val result = IdentityHashMap<InstrBasicBlock, Set<Temp>>()
    for (block in blocks)
        result[block] = instrOuts[block.toInstrs().last()]!!

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
