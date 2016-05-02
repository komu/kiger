package kiger.regalloc

import kiger.temp.Temp

class INode(val temp: Temp, var adj: List<INode>, var status: IStatus) {

    val adjList: List<INode>
        get() = adj

    val degree: Int
        get() = status.degree

    val isInGraph: Boolean
        get() = status is IStatus.InGraph

    /** Mapping from node to moves it's associated with */
    val moveList = mutableListOf<Move>()

    /** When move `(u,v)` has been coalesced, and `v` is put in coalescedNodes, then `alias(v) == u` */
    var alias: INode? = null

    override fun toString() =
        "${temp.name.padEnd(10)}: ${adj.map { it.temp.name }.sorted().joinToString(", ")}"


}
