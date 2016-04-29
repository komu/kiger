package kiger.regalloc

class Graph {

    private val nodeList = mutableListOf<Node>()
    private val edges = mutableListOf<Edge>()
    private var nodeIdSeq = 0

    val nodes: List<Node>
        get() = nodeList

    fun newNode(): Node {
        val node = Node(this, nodeIdSeq++)
        nodeList += node
        return node
    }

    fun addEdge(from: Node, to: Node) {
        edges += Edge(from, to)
    }

    fun removeEdge(from: Node, to: Node) {
        edges.removeAll { from == it.from && to == it.to }
    }

    private class Edge(val from: Node, val to: Node)

    class Node internal constructor(val g: Graph, val i: Int) : Comparable<Node> {
        val succ: List<Node>
            get() = g.edges.asSequence().filter { it.from == this }.map { it.to }.toList()

        val pred: List<Node>
            get() = g.edges.asSequence().filter { it.to == this }.map { it.from }.toList()

        val adj: List<Node>
            get() = succ + pred

        override fun compareTo(other: Node) = i.compareTo(other.i)

        override fun toString() = "n$i"
    }
}
