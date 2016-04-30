package kiger.regalloc

import kiger.temp.Temp

class FlowGraph(val nodes: List<Node>) {

    val allEdges: Sequence<TempEdge>
        get() = nodes.asSequence().flatMap { it.findEdges().asSequence() }

    val allMoves: Sequence<TempEdge>
        get() = nodes.asSequence()
                .filter { it.isMove }
                .map { TempEdge(it.use.single(), it.def.single()) }

    val allUsesAndDefs: Sequence<Temp>
        get() = nodes.asSequence().flatMap { it.def.asSequence() + it.use.asSequence() }

    data class Node(val id: Int,
                    val def: List<Temp>,
                    val use: List<Temp>,
                    val isMove: Boolean) {
        var succ: List<Node> = emptyList()
        var prev: List<Node> = emptyList()
        var liveOut: List<Temp> = emptyList()

        fun findEdges(): Sequence<TempEdge> =
            def.asSequence().flatMap { t -> liveOut.asSequence().filter { it != t }.map { TempEdge(t, it) } }
    }

    fun format(tempFormat: (Temp) -> String): String {
        val sb = StringBuilder()

        for (n in nodes) {
            sb.appendln("n${n.id}")
            sb.appendln("  def: ${n.def.map(tempFormat)}")
            sb.appendln("  use: ${n.use.map(tempFormat)}")
            sb.appendln("  succ: ${n.succ.joinToString(", ") { it.id.toString() }}")
            sb.appendln("  prev: ${n.prev.joinToString(", ") { it.id.toString() }}")
            sb.appendln("  liveout: ${n.liveOut}")
        }

        return sb.toString()
    }

    override fun toString() = format { it.toString() }
}

data class TempEdge(val src: Temp, val dst: Temp)
