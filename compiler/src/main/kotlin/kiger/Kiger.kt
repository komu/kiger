package kiger

import kiger.codegen.MipsGen
import kiger.frame.Fragment
import kiger.parser.parseExpression
import kiger.translate.Translator
import java.io.File
import java.io.Writer

class Kiger(val writer: Writer) {
    fun dumpCode(code: String) {
        val exp = parseExpression(code)
        val fragments = Translator.transProg(exp)

        for (fragment in fragments) {
            when (fragment) {
                is Fragment.Proc -> dumpProc(fragment)
                is Fragment.Str -> dumpStr(fragment)
            }
        }
    }

    private fun dumpProc(fragment: Fragment.Proc) {
        writer.write("${fragment.frame.name}:\n")

        val instructions = MipsGen.codeGen(fragment.frame, fragment.body)
        for (instr in instructions) {
            writer.write("$instr\n")
        }
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
