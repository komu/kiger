package kiger.regalloc

import kiger.temp.Temp

class INode(val temp: Temp, var adj: List<INode>, var status: IStatus) {

    val degree: Int
        get() = status.degree

    val isInGraph: Boolean
        get() = status is IStatus.InGraph

    override fun toString() =
        "${temp.name.padEnd(10)}: ${adj.map { it.temp.name }.sorted().joinToString(", ")}"
}
