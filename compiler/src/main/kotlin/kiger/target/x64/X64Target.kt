package kiger.target.x64

import kiger.assem.Instr
import kiger.emitProc
import kiger.regalloc.Coloring
import kiger.target.Fragment
import kiger.target.TargetArch
import kiger.writeLine
import java.io.Writer
import java.util.*

object X64Target : TargetArch {
    override val frameType = X64Frame
    override val codeGen = X64Gen

    override fun writeOutput(fragments: List<Fragment>, writer: Writer) {
        emitFragments(writer, fragments)
    }

    override fun peepholeOptimize(instructions: List<Instr>, coloring: Coloring): List<Instr> {
        val output = ArrayList<Instr>(instructions.size)

        instructions.forEachIndexed { i, inst ->
            val targets = (inst as? Instr.Oper)?.jump
            val next = instructions.getOrNull(i + 1)

            if (targets != null && targets.size == 1 && next is Instr.Lbl && next.label == targets[0]) {
                // skip this jump to next instruction
            } else {
                output += inst
            }
        }

        return output
    }

    private fun emitFragments(writer: Writer, fragments: List<Fragment>) {
        val strs = fragments.filterIsInstance<Fragment.Str>()
        val procs = fragments.filterIsInstance<Fragment.Proc>()

        if (strs.any()) {
            writer.writeLine("    .section        __TEXT,__cstring,cstring_literals")
            for (fragment in strs)
                writer.emitStr(fragment)
        }

        if (procs.any()) {
            writer.writeLine("    .section        __TEXT,__text,regular,pure_instructions")
            writer.writeLine("    .globl _main")
            for (fragment in procs)
                writer.emitProc(this, fragment)
        }
    }

    private fun Writer.emitStr(fragment: Fragment.Str) {
        writeLine("${fragment.label}:\n    .asciz \"${fragment.value.replace("\"", "\\\"").replace("\n", "\\n")}\"")
    }
}
