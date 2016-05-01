package kiger.regalloc

import kiger.temp.Temp

data class INode(val temp: Temp, var adj: List<INode>, var status: IStatus) {
    override fun toString() =
        "${temp.name.padEnd(10)}: ${adj.map { it.temp.name }.sorted().joinToString(", ")}"
}
