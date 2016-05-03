package kiger.regalloc

import kiger.temp.Temp

class FlowGraph(val nodes: List<Node>) {

    data class Node(val id: Int,
                    val def: Set<Temp>,
                    val use: Set<Temp>,
                    val isMove: Boolean) {
        var succ: List<Node> = emptyList()
        var prev: List<Node> = emptyList()
        var liveOut: Set<Temp> = emptySet()

        fun findEdges(): Sequence<TempEdge> =
            def.asSequence().flatMap { t -> liveOut.asSequence().filter { it != t }.map { TempEdge(t, it) } }

        override fun toString() = format { it.toString() }

        fun format(tempFormat: (Temp) -> String): String {
            val sb = StringBuilder()
            sb.appendln("n$id")
            sb.appendln("  def: ${def.map(tempFormat)}")
            sb.appendln("  use: ${use.map(tempFormat)}")
            sb.appendln("  succ: ${succ.joinToString(", ") { it.id.toString() }}")
            sb.appendln("  prev: ${prev.joinToString(", ") { it.id.toString() }}")
            sb.appendln("  liveout: $liveOut")
            return sb.toString()
        }
    }

    fun format(tempFormat: (Temp) -> String): String =
        nodes.joinToString("") { it.format(tempFormat) }

    override fun toString() = format { it.toString() }
}

data class TempEdge(val src: Temp, val dst: Temp)
