package kiger.regalloc

import kiger.assem.Instr
import kiger.assem.InstrControlFlowGraph
import kiger.temp.Label
import kiger.temp.Temp

fun InstrControlFlowGraph.createFlowGraph(): FlowGraph {
    return toInstrs().createFlowGraph()
}

/**
 * Create a data flow graph from list of instructions.
 */
fun List<Instr>.createFlowGraph(): FlowGraph {
    val compList = FlowGraphBuilder(this)

    compList.createEdges()

    return compList.build()
}

class FlowGraph(val nodes: List<Node>) {

    val size: Int
        get() = nodes.size

    class Node(val id: Int, val def: Set<Temp>, val use: Set<Temp>, val isMove: Boolean, val instr: Instr) {
        val succ = mutableListOf<Node>()
        val prev = mutableListOf<Node>()
        var liveOut = emptySet<Temp>()

        override fun toString() = format { it.toString() }

        fun format(tempFormat: (Temp) -> String): String =
            "${id.toString().padStart(4)}: ${instr.format(tempFormat).padEnd(30)} ; liveout: $liveOut; def=${def.map(tempFormat)}, use=${use.map(tempFormat)}, succ: ${succ.joinToString(", ") { it.id.toString() }}, prev: ${prev.joinToString(", ") { it.id.toString() }}"
    }

    fun format(tempFormat: (Temp) -> String): String =
        nodes.joinToString("\n") { it.format(tempFormat) }

    override fun toString() = format { it.toString() }
}

private class FlowGraphBuilder(private val instructions: List<Instr>) {

    /**
     * Nodes corresponding to instructions.
     *
     * Each node in this list has same index as corresponding instruction in [instructions].
     */
    private val nodes = instructions.mapIndexed { i, inst -> makeNode(inst, i)}

    /**
     * Create edges between instructions.
     *
     * Go through instructions:
     *   - for every jump, create edge to all jump targets
     *   - for non-jumps, create edge to next instruction, if there is one
     */
    fun createEdges() {
        val nodesByLabels = createLabelMap()

        for (i in instructions.indices) {
            val inst = instructions[i]
            val node = nodes[i]

            if (inst is Instr.Oper && inst.jump != null) {
                for (label in inst.jump)
                    makeEdge(node, nodesByLabels[label] ?: error("undefined label $label"))

            } else if (i < instructions.lastIndex) {
                makeEdge(nodes[i], nodes[i + 1])
            }
        }
    }


    fun build() = FlowGraph(nodes)

    private fun makeEdge(from: FlowGraph.Node, to: FlowGraph.Node) {
        if (to !in from.succ) {
            from.succ += to
            to.prev += from
        }
    }

    private fun makeNode(inst: Instr, id: Int): FlowGraph.Node = when (inst) {
        is Instr.Oper   -> FlowGraph.Node(id, inst.dst.toSet(), inst.src.toSet(), false, inst)
        is Instr.Lbl    -> FlowGraph.Node(id, emptySet(), emptySet(), false, inst)
        is Instr.Move   -> FlowGraph.Node(id, setOf(inst.dst), setOf(inst.src), true, inst)
    }

    private fun createLabelMap(): Map<Label, FlowGraph.Node> {
        val labelMap = mutableMapOf<Label, FlowGraph.Node>()
        instructions.forEachIndexed { i, inst ->
            if (inst is Instr.Lbl)
                labelMap[inst.label] = nodes[i]
        }
        return labelMap
    }
}

