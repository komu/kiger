package kiger.codegen

import kiger.canon.createControlFlowGraph
import kiger.canon.linearize
import kiger.canon.traceSchedule
import kiger.frame.Fragment
import kiger.parser.parseExpression
import kiger.target.mips.MipsGen
import kiger.target.mips.MipsTarget
import kiger.translate.SemanticAnalyzer
import org.junit.Test

class CodeGenTest {

    @Test
    fun simpleGe() {
        dumpCode("let function fib(n: int): int = if n < 2 then n else fib(n-1) + fib(n-2) in fib(3) end")
    }

    private fun dumpCode(code: String) {
        val exp = parseExpression(code)
        val fragments = SemanticAnalyzer(MipsTarget).transProg(exp)

        for (fragment in fragments) {
            when (fragment) {
                is Fragment.Proc -> dumpProc(fragment)
                is Fragment.Str -> dumpStr(fragment)
            }
        }
    }

    private fun dumpProc(fragment: Fragment.Proc) {

        val traces = fragment.body.linearize().createControlFlowGraph().traceSchedule()

        val instructions = traces.flatMap { MipsGen.codeGen(fragment.frame, it) }
        for (instr in instructions) {
            println(instr)
        }
    }

    private fun dumpStr(fragment: Fragment.Str) {
        println("${fragment.label}: \"${fragment.value.replace("\"", "\\\"")}\"")
    }
}
