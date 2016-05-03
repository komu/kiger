package kiger.regalloc

import kiger.temp.Temp

/**
 * Constructs interference graph from [FlowGraph].
 */
fun FlowGraph.interferenceGraph(): InterferenceGraph {
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
    val tempMap = mutableMapOf<Temp, INode>()

    for (t in nodes.asSequence().flatMap { it.def.asSequence() + it.use.asSequence() })
        if (t !in tempMap)
            tempMap[t] = INode(t)

    val allEdges = nodes.asSequence().flatMap { it.findEdges() }.map { Pair(tempMap[it.src]!!, tempMap[it.dst]!!)}
    val allMoves = nodes.asSequence().filter { it.isMove }.map { Move(tempMap[it.use.single()]!!, tempMap[it.def.single()]!!) }.toList()

//    for ((src, dst) in allEdges) {
//        // don't add duplicate edges
//
//        if (dst !in src.adjList || src !in dst.adjList) {
//            src.adjList += dst
//            dst.adjList += src
//            src.degree += 1
//            dst.degree += 1
//        }
//    }

    return InterferenceGraph(tempMap.values.toList(), allMoves)
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

