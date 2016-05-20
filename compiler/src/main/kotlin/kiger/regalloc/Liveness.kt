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
    val liveoutMap = profile("buildLiveOutMap") { FlowGraphLiveOutCalculator.buildLiveOutMap(flowGraph.nodes) }

    val instrOuts = IdentityHashMap<Instr, Set<Temp>>()
    for (node in flowGraph.nodes)
        instrOuts[node.instr] = liveoutMap[node]

    val result = IdentityHashMap<InstrBasicBlock, Set<Temp>>()
    for (block in blocks)
        result[block] = instrOuts[block.toInstrs().last()]!!

    return result
}

/**
 * Computes live-out information for nodes of type T when given
 * [uses], [defs] and [successors].
 */
private abstract class LiveOutCalculator<T> {

    /**
     * Creates an array which contain the liveout set for every node in the graph.
     */
    fun buildLiveOutMap(nodes: List<T>): Map<T, Set<Temp>> {
        val liveIn = IdentityHashMap<T, Set<Temp>>()
        val liveOut = IdentityHashMap<T, Set<Temp>>()

        for (n in nodes) {
            liveIn[n] = emptySet()
            liveOut[n] = emptySet()
        }

        do {
            var changed = false

            for (n in nodes.asReversed()) {
                val oldIn = liveIn[n]!!
                val oldOut = liveOut[n]!!
                val newIn = uses(n) + (oldOut - defs(n))
                val newOut = n.computeOut(liveIn)

                if (newIn != oldIn || newOut != oldOut) {
                    changed = true
                    liveIn[n] = newIn
                    liveOut[n] = newOut
                }
            }
        } while (changed)

        return liveOut
    }

    /**
     * Compute liveout set for a node, given a livein map.
     */
    private fun T.computeOut(liveinMap: Map<T, Set<Temp>>): Set<Temp> {
        val set = TreeSet<Temp>()
        for (s in successors(this))
            set += liveinMap[s]!!
        return set
    }

    protected abstract fun uses(node: T): Set<Temp>
    protected abstract fun defs(node: T): Set<Temp>
    protected abstract fun successors(node: T): Collection<T>
}

private object FlowGraphLiveOutCalculator : LiveOutCalculator<FlowGraph.Node>() {
    override fun uses(node: FlowGraph.Node) = node.use
    override fun defs(node: FlowGraph.Node) = node.def
    override fun successors(node: FlowGraph.Node) = node.succ
}
