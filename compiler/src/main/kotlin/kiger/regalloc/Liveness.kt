package kiger.regalloc

import kiger.temp.Temp

sealed class IStatus {

    object Removed : IStatus() {
        override fun toString() = "Removed"
    }

    class InGraph(val degree: Int) : IStatus() {
        override fun toString() = "InGraph($degree)"
    }

    class Colored(val color: String) : IStatus() {
        override fun toString() = "Colored($color)"
    }
}

data class INode(val temp: Temp, var adj: List<INode>, var status: IStatus) {
    override fun toString() =
        "$temp: $status - adj: ${adj.joinToString(", ") { it.temp.toString() }}"
}

data class IGraph(val graph: List<INode>, val moves: List<Pair<INode, INode>>) {
    override fun toString() = graph.joinToString("\n")
}

fun FlowGraph.interferenceGraph(): IGraph {
    initializeLiveOuts()

    // now for each node n in the flow graph, suppose
    // there is a newly define temp d, and temporaries
    // t1, ..., tn are in liveout set of node n. Then,
    // we add edge (d,t1), ..., (d,tn) to the igraph.
    // Mappings between temps and igraph nodes are also recorded.
    // The rules for adding interference edges are:
    // 1. At any non-move instruction that defines a variable a, where the
    //   live-out variables are b1,...bj, add interference edges
    //   (a,b1),...,(a,bj).
    // 2. At a move instruction a <- c, where variables b1,...,bj are live-out,
    //   add interference edges (a,b1),...,(a,bj) for any bi that is not
    //   the same as c. *)

    val tempMap = mutableMapOf<Temp,INode>()

    for (t in allUsesAndDefs)
        if (t !in tempMap)
            tempMap[t] = INode(t, emptyList(), IStatus.InGraph(0))

    val allMoves = allMoves.map { Pair(tempMap[it.src]!!, tempMap[it.dst]!!) }.toList()

    for ((srcTemp, dstTemp) in allEdges) {
        // don't add duplicate edges

        val src = tempMap[srcTemp]!!
        val dst = tempMap[dstTemp]!!

        val d1 = (src.status as IStatus.InGraph).degree
        val d2 = (dst.status as IStatus.InGraph).degree

        if (dst !in src.adj || src !in dst.adj) {
            src.adj += dst
            dst.adj += src
            src.status = IStatus.InGraph(d1 + 1)
            dst.status = IStatus.InGraph(d2 + 1)
        }
    }

    return IGraph(tempMap.values.toList(), allMoves)
}

private fun FlowGraph.initializeLiveOuts() {
    val liveoutMap = buildLiveOutMap()

    // set liveout for each node
    for (node in nodes) {
        val s = liveoutMap[node.id]
        if (s != null)
            node.liveOut = s.toList()
        else
            error("liveout map is not one-to-one")
    }
}

// Given livein map, compute liveout set for node n
private fun FlowGraph.Node.computeOut(liveMap: LiveMap): Set<Temp> {
    val set = mutableSetOf<Temp>()
    for (n in succ)
        set += liveMap[n.id]!!
    return set
}

private fun FlowGraph.buildLiveOutMap(): LiveMap {
    val liveinMap = LiveMap(this)
    val liveoutMap = LiveMap(this)

    // recursively compute liveSet, until it reaches a fixpoint
    do {
        var changed = false

        for (node in nodes) {
            val oldIn = liveinMap[node.id]!!
            val oldOut = liveoutMap[node.id]!!
            val newIn = node.use.toSet() + (oldOut - node.def.toSet())
            val newOut = node.computeOut(liveinMap)

            if (newIn != oldIn || newOut != oldOut) {
                changed = true
                liveinMap[node.id] = newIn
                liveoutMap[node.id] = newOut
            }
        }
    } while (changed)

    return liveoutMap
}

private class LiveMap(graph: FlowGraph) {

    private val map = mutableMapOf<Int, Set<Temp>>()

    init {
        for (node in graph.nodes)
            map[node.id] = emptySet()
    }

    operator fun get(key: Int) = map[key]

    operator fun set(key: Int, value: Set<Temp>) {
        map[key] = value
    }
}
