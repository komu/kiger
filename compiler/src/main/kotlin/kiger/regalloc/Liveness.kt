package kiger.regalloc

import kiger.assem.Instr
import kiger.assem.InstrBasicBlock
import kiger.assem.InstrControlFlowGraph
import kiger.temp.Temp
import java.util.*

/**
 * Constructs an interference graph from [InstrControlFlowGraph].
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
fun InstrControlFlowGraph.buildLiveOutsForBasicBlocks(): Map<InstrBasicBlock, Set<Temp>> =
    BasicBlockLiveOutCalculator(this).buildLiveOutMap(this.blocks)

/**
 * Computes live-out information for nodes of type T when given
 * [uses], [defs] and [successors].
 */
abstract class LiveOutCalculator<T> {

    /**
     * Creates an array which contain the liveout set for every node in the graph.
     */
    fun buildLiveOutMap(nodes: List<T>): Map<T, Set<Temp>> {
        val uses = IdentityHashMap<T, Set<Temp>>()
        val defs = IdentityHashMap<T, Set<Temp>>()
        val liveIn = IdentityHashMap<T, Set<Temp>>()
        val liveOut = IdentityHashMap<T, Set<Temp>>()

        for (n in nodes) {
            liveIn[n] = emptySet()
            liveOut[n] = emptySet()
            uses[n] = uses(n)
            defs[n] = defs(n)
        }

        do {
            var changed = false

            for (n in nodes.asReversed()) {
                val oldIn = liveIn[n]!!
                val oldOut = liveOut[n]!!
                val newIn = uses[n]!! + (oldOut - defs[n]!!)
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

private class BasicBlockLiveOutCalculator(val cfg: InstrControlFlowGraph) : LiveOutCalculator<InstrBasicBlock>() {

    override fun uses(node: InstrBasicBlock): Set<Temp> {
        val uses = mutableSetOf<Temp>()
        for (i in node.body.asReversed()) {
            uses -= i.defs
            uses += i.uses
        }
        return uses
    }

    override fun defs(node: InstrBasicBlock): Set<Temp> =
        node.body.flatMap { it.defs }.toSet()

    override fun successors(node: InstrBasicBlock): Collection<InstrBasicBlock> = cfg.successors(node)
}
