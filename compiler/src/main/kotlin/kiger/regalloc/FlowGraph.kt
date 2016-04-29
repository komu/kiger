package kiger.regalloc

import kiger.temp.Temp

class FlowGraph(val nodes: List<Node>) {

    data class Node(val id: Int,
                    val def: List<Temp>,
                    val use: List<Temp>,
                    val isMove: Boolean,
                    var succ: List<Node>,
                    var prev: List<Node>,
                    var liveOut: List<Temp>)
}
