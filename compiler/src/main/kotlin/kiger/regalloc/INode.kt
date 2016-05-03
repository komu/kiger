package kiger.regalloc

import kiger.temp.Temp

class INode(val temp: Temp, val adjList: MutableSet<INode> = mutableSetOf(), var degree: Int = 0) {

    /** Mapping from node to moves it's associated with */
    val moveList = mutableListOf<Move>()

    /** When move `(u,v)` has been coalesced, and `v` is put in coalescedNodes, then `alias(v) == u` */
    var alias: INode? = null

    override fun toString() =
        "${temp.name.padEnd(10)}: ${adjList.map { it.temp.name }.sorted().joinToString(", ")}"
}
