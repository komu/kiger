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

    for (t in allUsesAndDefs)
        if (t !in tempMap)
            tempMap[t] = INode(t, emptyList(), IStatus.InGraph(0))

    val allMoves = allMoves.map { Move(tempMap[it.src]!!, tempMap[it.dst]!!) }.toList()

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

    return InterferenceGraph(tempMap.values.toList(), allMoves)
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

/**
 * Creates a map which contain the liveout set for every node of [FlowGraph].
 */
private fun FlowGraph.buildLiveOutMap(): Map<Int, Set<Temp>> {
    val liveinMap = mutableMapOf<Int, Set<Temp>>()
    val liveoutMap = mutableMapOf<Int, Set<Temp>>()

    for (node in nodes) {
        liveinMap[node.id] = emptySet()
        liveoutMap[node.id] = emptySet()
    }

    do {
        var changed = false

        for (node in nodes) {
            val oldIn = liveinMap[node.id]!!
            val oldOut = liveoutMap[node.id]!!
            val newIn = node.use.toSet() + (oldOut - node.def)
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

/**
 * Compute liveout set for a node, given a livein map.
  */
private fun FlowGraph.Node.computeOut(liveinMap: Map<Int, Set<Temp>>): Set<Temp> {
    val set = mutableSetOf<Temp>()
    for (s in succ)
        set += liveinMap[s.id]!!
    return set
}

