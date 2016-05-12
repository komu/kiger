package kiger.target.x64

import kiger.emitProc
import kiger.target.CodeGen
import kiger.target.Fragment
import kiger.target.TargetArch
import kiger.writeLine
import java.io.Writer

object X64Target : TargetArch {
    override val frameType = X64Frame
    override val codeGen = X64Gen

    override fun writeOutput(fragments: List<Fragment>, it: Writer) {
        it.emitFragments(codeGen, fragments)
    }

    private fun Writer.emitFragments(codeGen: CodeGen, fragments: List<Fragment>) {
        val strs = fragments.filterIsInstance<Fragment.Str>()
        val procs = fragments.filterIsInstance<Fragment.Proc>()

        if (strs.any()) {
            writeLine("    .section        __TEXT,__cstring,cstring_literals")
            for (fragment in strs)
                emitStr(fragment)
        }

        if (procs.any()) {
            writeLine("    .section        __TEXT,__text,regular,pure_instructions")
            writeLine("    .globl _main")
            for (fragment in procs)
                emitProc(codeGen, fragment)
        }
    }

    private fun Writer.emitStr(fragment: Fragment.Str) {
        writeLine("${fragment.label}:\n    .asciz \"${fragment.value.replace("\"", "\\\"").replace("\n", "\\n")}\"")
    }
}
