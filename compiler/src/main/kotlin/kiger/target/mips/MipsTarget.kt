package kiger.target.mips

import kiger.assem.Instr
import kiger.emitProc
import kiger.regalloc.Coloring
import kiger.target.CodeGen
import kiger.target.Fragment
import kiger.target.TargetArch
import kiger.writeLine
import java.io.Writer

object MipsTarget : TargetArch {
    override val frameType = MipsFrame
    override val codeGen: CodeGen = MipsGen

    override fun writeOutput(fragments: List<Fragment>, writer: Writer) {
        val runtime = javaClass.classLoader.getResourceAsStream("mips-runtime.s")?.use { it.reader().readText() } ?: error("could not load mips-runtime.s")

        emitFragments(writer, fragments)
        writer.write(runtime)
    }

    override fun peepholeOptimize(instructions: List<Instr>, coloring: Coloring) = instructions

    private fun emitFragments(writer: Writer, fragments: List<Fragment>) {
        val strs = fragments.filterIsInstance<Fragment.Str>()
        val procs = fragments.filterIsInstance<Fragment.Proc>()

        if (strs.any()) {
            writer.writeLine("    .data")
            for (fragment in strs)
                writer.emitStr(fragment)
        }

        if (procs.any()) {
            writer.writeLine("    .text")
            for (fragment in procs)
                writer.emitProc(this, fragment)
        }
    }

    private fun Writer.emitStr(fragment: Fragment.Str) {
        writeLine("${fragment.label}:\n    .asciiz \"${fragment.value.replace("\"", "\\\"").replace("\n", "\\n")}\"")
    }
}
