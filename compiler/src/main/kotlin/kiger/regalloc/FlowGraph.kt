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
                    val isMove: Boolean,
                    var succ: List<Node>,
                    var prev: List<Node>,
                    var liveOut: List<Temp>) {

        fun findEdges(): Sequence<TempEdge> =
            def.asSequence().flatMap { t -> liveOut.asSequence().filter { it != t }.map { TempEdge(t, it) } }
    }
}

data class TempEdge(val src: Temp, val dst: Temp)
