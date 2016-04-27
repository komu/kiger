package kiger.codegen

import kiger.frame.Fragment
import kiger.parser.parseExpression
import kiger.translate.Translator
import org.junit.Test

class CodeGenTest {

    @Test
    fun simpleGe() {
        dumpCode("let function fib(n: int) = if n < 2 then n else fib(n-1) + fib(n-2) in fib(3)")
    }

    private fun dumpCode(code: String) {
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

        val instructions = JouletteGen.codeGen(fragment.frame, fragment.body)
        for (instr in instructions) {
            println(instr)
        }
    }

    private fun dumpStr(fragment: Fragment.Str) {
        println("${fragment.label}: \"${fragment.value.replace("\"", "\\\"")}\"")
    }
}
