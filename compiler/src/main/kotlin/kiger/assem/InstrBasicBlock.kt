package kiger.assem

import kiger.temp.Label
import kiger.temp.Temp
import java.util.*

data class InstrControlFlowGraph(val blocks: List<InstrBasicBlock>, val exitLabel: Label) {

    private val successors = run {
        val map = IdentityHashMap<InstrBasicBlock, List<InstrBasicBlock>>()
        for (block in blocks) {
            val labels = block.body.filterIsInstance<Instr.Oper>().mapNotNull { it.jump }.flatten().toSet()
            map[block] = labels.mapNotNull { label -> blocks.find { it.label == label } }
        }
        map
    }

    fun toInstrs(): List<Instr> =
        blocks.flatMap { it.toInstrs() } + Instr.Lbl("$exitLabel:", exitLabel)

    fun rewriteInstructions(f: (Instr) -> List<Instr>): InstrControlFlowGraph =
        InstrControlFlowGraph(blocks.map { it.rewriteInstructions(f) }, exitLabel)

    fun count(predicate: (Instr) -> Boolean): Int =
        blocks.sumBy { it.count(predicate) }

    fun allTemporaries(): Set<Temp> {
        val result = mutableSetOf<Temp>()

        for (b in blocks)
            for (i in b.body) {
                result += i.defs
                result += i.uses
            }

        return result
    }

    fun successors(block: InstrBasicBlock): Collection<InstrBasicBlock> = successors[block]!!
}

class InstrBasicBlock(val label: Label, val body: List<Instr>) {

    // Identity of instructions is important because they are used as keys for liveout maps etc.
    // Therefore we must use same instruction for label.
    private val labelInst = Instr.Lbl("$label:", label)

    fun toInstrs(): List<Instr> = listOf(labelInst) + body

    fun rewriteInstructions(f: (Instr) -> List<Instr>): InstrBasicBlock =
        InstrBasicBlock(label, body.flatMap { f(it) })

    fun count(predicate: (Instr) -> Boolean): Int =
        body.count(predicate)
}
