package kiger.regalloc

import kiger.assem.Instr
import kiger.assem.InstrControlFlowGraph
import kiger.ir.tree.TreeExp.Temporary
import kiger.ir.tree.TreeStm.Move
import kiger.target.CodeGen
import kiger.target.Frame
import kiger.temp.Temp

/**
 * Given a list of instructions and [frame], allocate registers. Returns
 * rewritten list of instructions and a coloring that maps [Temp]-values
 * to registers.
 */
fun InstrControlFlowGraph.allocateRegisters(codeGen: CodeGen, frame: Frame): Pair<List<Instr>, Coloring> {
    return toInstrs().allocateRegisters(codeGen, frame)
}

private tailrec fun List<Instr>.allocateRegisters(codeGen: CodeGen, frame: Frame): Pair<List<Instr>, Coloring> {
    val (colors, spills) = color(this, frame.type)

    fun Instr.isRedundant() =
        this is Instr.Move && colors[dst] == colors[src]

    return if (spills.isEmpty())
        Pair(filterNot { it.isRedundant() }, colors)
    else
        rewrite(codeGen, this, frame, spills).allocateRegisters(codeGen, frame)
}

/**
 * Rewrite instructions to spill of temporaries defined in [spills].
 */
private fun rewrite(codeGen: CodeGen, instrs: List<Instr>, frame: Frame, spills: Collection<Temp>): List<Instr> =
    spills.fold(instrs) { i, t -> rewrite1(codeGen, i, frame, t) }

/**
 * Rewrite instructions to spill [spill].
 */
private fun rewrite1(codeGen: CodeGen, instrs: List<Instr>, frame: Frame, spill: Temp): List<Instr> {
    val varInFrame = frame.type.exp(frame.allocLocal(true), Temporary(frame.type.FP))

    /**
     * Generate instruction for load/store between frame and given temp.
     */
    fun genInstrs(store: Boolean, t: Temp) =
        codeGen.codeGen(frame, if (store) Move(varInFrame, Temporary(t)) else Move(Temporary(t), varInFrame))

    /**
     * If spilled register is in given def/use set, allocate a new temp and
     * generate instructions for storing/loading the value from frame to temp.
     * Replace all uses of spill value with the new temp.
     */
    fun allocDu(store: Boolean, dus: List<Temp>): Pair<List<Instr>, List<Temp>> =
        if (spill in dus) {
            val nt = Temp.gen()
            Pair(genInstrs(store, nt), dus.map { if (spill == it) nt else it })
        } else {
            Pair(emptyList(), dus)
        }

    /**
     * Transform instruction if it contains the spilled temporary.
     */
    fun transInstr(instr: Instr): List<Instr> {
        // If the instruction doesn't define or use t, it does not need to be transformed
        if (!instr.references(spill)) return listOf(instr)

        return when (instr) {
            is Instr.Oper -> {
                val (store, dst2) = allocDu(true, instr.dst)
                val (fetch, src2) = allocDu(false, instr.src)

                fetch + instr.rewriteRegisters(dst2, src2) + store
            }
            is Instr.Move -> {
                val (store, dst2) = allocDu(true, listOf(instr.dst))
                val (fetch, src2) = allocDu(false, listOf(instr.src))

                fetch + instr.rewriteRegisters(dst2.single(), src2.single()) + store
            }
            is Instr.Lbl ->
                listOf(instr)
        }
    }

    return instrs.flatMap { transInstr(it) }
}
