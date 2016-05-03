package kiger.regalloc

import kiger.regalloc.InterferenceGraph.INode
import kiger.temp.Temp

data class InterferenceGraph(val nodes: List<INode>, val moves: List<Move>) {

    private val adjSet = AdjSet()
    fun addEdge(u: INode, v: INode) = adjSet.addEdge(u, v)

    fun contains(u: INode, v: INode) = adjSet.contains(u, v)

    override fun toString() = nodes.sortedBy { it.temp.name }.joinToString("\n")

    fun check(precolored: Set<INode>) {
        check(precolored.all { it.adjList.isEmpty() }) { "precolored nodes with adj-lists" }
        check(precolored.all { v -> precolored.all { u -> v == u || contains(u, v) }}) { "no edges for precolored" }
        check(nodes.all { v -> v.adjList.all { u -> contains(v,u) && contains(u, v) }}) { "no set for adjList item" }
        check(adjSet.all { (it.second in precolored || it.first in it.second.adjList) && (it.first in precolored || it.second in it.first.adjList) })
    }

    fun dump(precolored: Set<INode>) {
        val nodes = nodes.sortedBy { if (it in precolored) "z${it.temp.name}" else it.temp.name }

        for (row in nodes) {
            print(row.temp.name.padStart(10) + " ")
            for (col in nodes) {
                if (col in precolored) continue
                if (contains(row, col)) {
                    print("x")
                } else {
                    print(" ")
                }
            }
            println()
        }
    }

    class INode(val temp: Temp, val adjList: MutableSet<INode> = mutableSetOf(), var degree: Int = 0) {

        /** Mapping from node to moves it's associated with */
        val moveList = mutableListOf<Move>()

        /** When move `(u,v)` has been coalesced, and `v` is put in coalescedNodes, then `alias(v) == u` */
        var alias: INode? = null

        override fun toString() =
                "${temp.name.padEnd(10)}: ${adjList.map { it.temp.name }.sorted().joinToString(", ")}"
    }
}

private class AdjSet : Iterable<Pair<INode, INode>> {

    private val set = mutableSetOf<Pair<INode,INode>>() // TODO: use bitset

    fun contains(u: INode, v: INode) =
            Pair(u, v) in set

    fun addEdge(u: INode, v: INode) {
        set += Pair(u, v)
    }

    override fun iterator() = set.iterator()
}
