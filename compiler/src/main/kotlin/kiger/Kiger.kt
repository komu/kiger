package kiger

import kiger.assem.Instr
import kiger.codegen.MipsGen
import kiger.escape.analyzeEscapes
import kiger.frame.Fragment
import kiger.parser.parseExpression
import kiger.translate.SemanticAnalyzer
import java.io.File
import java.io.Writer

class Kiger(val writer: Writer) {
    fun dumpCode(code: String) {
        val exp = parseExpression(code)
        exp.analyzeEscapes()
        val fragments = SemanticAnalyzer.transProg(exp)

        for (fragment in fragments) {
            when (fragment) {
                is Fragment.Proc -> dumpProc(fragment)
                is Fragment.Str -> dumpStr(fragment)
            }
        }
    }

    private fun dumpProc(fragment: Fragment.Proc) {
        val (prologue, instructions, epilogue) = fragment.frame.procEntryExit3(MipsGen.codeGen(fragment.frame, fragment.body))
        writer.write(prologue)
        for (instr in instructions)
            if (instr !is Instr.Oper || instr.assem != "")
                writer.write("$instr\n")
        writer.write(epilogue)
    }

    private fun dumpStr(fragment: Fragment.Str) {
        writer.write("${fragment.label}: \"${fragment.value.replace("\"", "\\\"")}\"\n")
    }
}

fun main(args: Array<String>) {
    File("output.s").writer().use { w ->
        Kiger(w).dumpCode("let function square(n: int): int = n * n in square(4)")
    }
}
