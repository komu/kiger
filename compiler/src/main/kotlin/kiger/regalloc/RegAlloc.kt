package kiger.regalloc

import kiger.assem.Instr
import kiger.codegen.MipsGen
import kiger.frame.Frame
import kiger.frame.Register
import kiger.temp.Temp
import kiger.tree.TreeExp.Temporary
import kiger.tree.TreeStm.Move

data class Allocation(val registerAssignments: Map<Temp, Register>) {
    operator fun get(t: Temp): Register? = registerAssignments[t]
    fun name(t: Temp): String =
        registerAssignments[t]?.name ?: t.name

    fun enter(t: Temp, r: Register): Allocation =
        Allocation(registerAssignments + (t to r))

    companion object {
        fun empty() = Allocation(emptyMap())
    }
}

tailrec fun List<Instr>.allocateRegisters(frame: Frame): Pair<List<Instr>, Allocation> {
    val frameType = frame.type
    val graph = this.createFlowGraph()
    val igraph = graph.interferenceGraph()

//    println("\n---\n")
//    println(frame.name)

//    for (node in graph.nodes)
//        println("${this[node.id].toString().trim().padEnd(30)}: ${node.liveOut.sorted()}")
//    for (node in graph.nodes)
//        println("${this[node.id].toString().trim().padEnd(30)}: $node")
//
//    println("---")
//    println(igraph)

    fun spillCost(temp: Temp): Double {
        val numDu = graph.nodes.sumBy { n -> n.def.containsToInt(temp) + n.use.containsToInt(temp) }
        val node = igraph.graph.find { it.temp == temp } ?: error("could not find node for $temp")
        val interferes = node.adj.size

        return numDu.toDouble() / interferes.toDouble()
    }

    val (allocTable, spills) = color(igraph, frameType.tempMap, ::spillCost, frameType.registers)

    fun Instr.isRedundant() =
            this is Instr.Move && allocTable[dst] == allocTable[src]

    return if (spills.isEmpty())
        Pair(filterNot { it.isRedundant() }, allocTable)
    else
        rewrite(this, frame, spills).allocateRegisters(frame)
}

private fun rewrite(instrs: List<Instr>, frame: Frame, spills: List<Temp>): List<Instr> =
    spills.fold(instrs) { i, t -> rewrite1(i, frame, t) }

private fun rewrite1(instrs: List<Instr>, frame: Frame, t: Temp): List<Instr> {
    val ae = frame.type.exp(frame.allocLocal(true), Temporary(frame.type.FP))

    // generate fetch or store instruction
    fun genInstrs(isStore: Boolean, t: Temp) =
        MipsGen.codeGen(frame, if (isStore) Move(ae, Temporary(t)) else Move(Temporary(t), ae))

    // allocate new temp for each occurrence of t in dus,
    // replace the occurrence with the new temp.
    fun allocDu(isStore: Boolean, dus: List<Temp>, t: Temp):  Pair<List<Instr>, List<Temp>> =
        if (t in dus) {
            val nt = Temp()
            Pair(genInstrs(isStore, nt), dus.map { if (t == it) nt else it })
        } else {
            Pair(emptyList(), dus)
        }

    // transform one instruction for one spilled temp
    fun transInstr(instr: Instr): List<Instr> = when (instr) {
        is Instr.Oper -> {
            val (store, dst2) = allocDu(true, instr.dst, t)
            val (fetch, src2) = allocDu(false, instr.src, t)

            fetch + Instr.Oper(instr.assem, dst2, src2, instr.jump) + store
        }
        is Instr.Move -> {
            val (store, dst2) = allocDu(true, listOf(instr.dst), t)
            val (fetch, src2) = allocDu(false, listOf(instr.src), t)

            fetch + Instr.Move(instr.assem, dst2.single(), src2.single()) + store
        }
        is Instr.Lbl ->
            listOf(instr)
    }

    return instrs.flatMap { transInstr(it) }
}

private fun <T> Collection<T>.containsToInt(t: T) = if (t in this) 1 else 0
