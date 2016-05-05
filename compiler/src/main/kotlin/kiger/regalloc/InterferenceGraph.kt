package kiger.regalloc

import kiger.temp.Temp
import java.util.Objects.hash

data class InterferenceGraph(val nodes: List<INode>, val moves: List<Move>) {

    private val adjSet = mutableSetOf<Pair<INode,INode>>() // TODO: use bitset

    fun addEdge(u: INode, v: INode) {
        if (!contains(u, v) && u != v) {
            adjSet += Pair(v, u)
            adjSet += Pair(u, v)

            if (!u.precolored) {
                u.adjList += v
                u.degree += 1
            }

            if (!v.precolored) {
                v.adjList += u
                v.degree += 1
            }
        }
    }


    fun contains(u: INode, v: INode) = Pair(u, v) in adjSet

    fun nodeForTemp(t: Temp): INode =
        nodes.find { it.temp == t } ?: error("could not find node for $t")

    @Suppress("unused")
    fun check() {
        val precolored = nodes.filter { it.precolored }
        check(precolored.all { it.adjList.isEmpty() }) { "precolored nodes with adj-lists" }
        check(precolored.all { v -> precolored.all { u -> v == u || contains(u, v) }}) { "no edges for precolored" }
        check(nodes.all { v -> v.adjList.all { u -> contains(v,u) && contains(u, v) }}) { "no set for adjList item" }
        check(adjSet.all { (it.second in precolored || it.first in it.second.adjList) && (it.first in precolored || it.second in it.first.adjList) })

        check(nodes.all { it !in it.adjList}) { "node is in its own adjacency set"}
        check(nodes.all { !contains(it, it) }) { "self-edge on adjacency set" }
    }

    override fun toString(): String {
        val nodes = nodes.sortedBy { if (it.precolored) "z${it.temp.name}" else it.temp.name }

        val sb = StringBuilder()
        for (row in nodes) {
            sb.append(row.temp.name.padStart(10) + " [${row.degree}/${row.adjList.size}]: ")
            for (col in nodes) {
                if (contains(row, col))
                    sb.append("${col.temp} ")
            }
            sb.appendln()
        }

        return sb.toString()
    }

    class INode(val temp: Temp) {

        val adjList = mutableSetOf<INode>()
        var degree = 0
            get() = field
            set(v) {
                check(!precolored)
                field = v
            }

        var precolored = false

        /** Mapping from node to moves it's associated with */
        val moveList = mutableListOf<Move>()

        /** When move `(u,v)` has been coalesced, and `v` is put in coalescedNodes, then `alias(v) == u` */
        var alias: INode? = null

        override fun toString() = temp.name
    }

    class Move(val src: INode, val dst: INode) {
        override fun toString() = "${src.temp} -> ${dst.temp}"
        override fun equals(other: Any?) = other is Move && src == other.src && dst == other.dst
        override fun hashCode() = hash(src, dst)
    }
}
