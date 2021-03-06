package kiger.target

import kiger.assem.Instr
import kiger.assem.InstrControlFlowGraph
import kiger.ir.tree.TreeStm
import kiger.temp.Label

abstract class Frame(val name: Label) {

    abstract val formals: List<FrameAccess>

    abstract fun allocLocal(escape: Boolean): FrameAccess

    /**
     * Move each incoming register parameter to its correct place in the function.
     */
    abstract fun procEntryExit1(body: TreeStm): TreeStm

    abstract fun procEntryExit2(body: InstrControlFlowGraph): InstrControlFlowGraph

    abstract fun procEntryExit3(body: List<Instr>): Triple<String, List<Instr>, String>

    abstract val type : FrameType
}

