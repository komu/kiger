package kiger.regalloc

data class InterferenceGraph(val graph: List<INode>, val moves: List<Pair<INode, INode>>) {
    override fun toString() = graph.sortedBy { it.temp.name }.joinToString("\n")
}
