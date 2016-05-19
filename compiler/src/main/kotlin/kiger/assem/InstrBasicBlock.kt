package kiger.assem

import kiger.temp.Label

data class InstrControlFlowGraph(val blocks: List<InstrBasicBlock>, val exitLabel: Label) {
    fun toInstrs(): List<Instr> =
        blocks.flatMap { it.toInstrs() } + Instr.Lbl("$exitLabel:", exitLabel)

    fun rewriteInstructions(f: (Instr) -> List<Instr>): InstrControlFlowGraph =
        InstrControlFlowGraph(blocks.map { it.rewriteInstructions(f) }, exitLabel)

    fun count(predicate: (Instr) -> Boolean): Int =
        blocks.sumBy { it.count(predicate) }
}

class InstrBasicBlock(val label: Label, val body: List<Instr>) {
    fun toInstrs(): List<Instr> = listOf(Instr.Lbl("$label:", label)) + body

    fun rewriteInstructions(f: (Instr) -> List<Instr>): InstrBasicBlock =
        InstrBasicBlock(label, body.flatMap { f(it) })

    fun count(predicate: (Instr) -> Boolean): Int =
        body.count(predicate)
}
