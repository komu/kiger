package kiger.assem

import kiger.temp.Label

data class InstrControlFlowGraph(val blocks: List<InstrBasicBlock>, val exitLabel: Label) {
    fun toInstrs(): List<Instr> =
        blocks.flatMap { it.toInstrs() } + Instr.Lbl("$exitLabel:", exitLabel)
}

class InstrBasicBlock(val label: Label, val body: List<Instr>) {
    fun toInstrs(): List<Instr> = listOf(Instr.Lbl("$label:", label)) + body
}
