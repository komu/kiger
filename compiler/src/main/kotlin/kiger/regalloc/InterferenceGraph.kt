package kiger.regalloc

data class InterferenceGraph(val nodes: List<INode>, val moves: List<Move>) {
    override fun toString() = nodes.sortedBy { it.temp.name }.joinToString("\n")
}
