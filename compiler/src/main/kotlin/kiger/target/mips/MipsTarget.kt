package kiger.target.mips

import kiger.emitProc
import kiger.frame.Fragment
import kiger.target.CodeGen
import kiger.target.TargetArch
import kiger.writeLine
import java.io.Writer

object MipsTarget : TargetArch {
    override val frameType = MipsFrame
    override val codeGen: CodeGen = MipsGen

    override fun writeOutput(fragments: List<Fragment>, it: Writer) {
        val runtime = javaClass.classLoader.getResourceAsStream("mips-runtime.s")?.use { it.reader().readText() } ?: error("could not load mips-runtime.s")

        it.emitFragments(codeGen, fragments)
        it.write(runtime)
    }

    private fun Writer.emitFragments(codeGen: CodeGen, fragments: List<Fragment>) {
        val strs = fragments.filterIsInstance<Fragment.Str>()
        val procs = fragments.filterIsInstance<Fragment.Proc>()

        if (strs.any()) {
            writeLine("    .data")
            for (fragment in strs)
                emitStr(fragment)
        }

        if (procs.any()) {
            writeLine("    .text")
            for (fragment in procs)
                emitProc(codeGen, fragment)
        }
    }

    private fun Writer.emitStr(fragment: Fragment.Str) {
        writeLine("${fragment.label}:\n    .asciiz \"${fragment.value.replace("\"", "\\\"").replace("\n", "\\n")}\"")
    }
}
