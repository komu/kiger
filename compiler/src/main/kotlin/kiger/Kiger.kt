package kiger

import kiger.assem.Instr
import kiger.canon.createControlFlowGraph
import kiger.canon.linearize
import kiger.canon.traceSchedule
import kiger.codegen.MipsGen
import kiger.escape.analyzeEscapes
import kiger.frame.Fragment
import kiger.parser.parseExpression
import kiger.regalloc.allocateRegisters
import kiger.translate.SemanticAnalyzer
import java.io.File
import java.io.Writer

private fun compile(code: String): List<Fragment> {
    val exp = parseExpression(code)
    exp.analyzeEscapes()
    return SemanticAnalyzer.transProg(exp)
}

fun File.emitFragments(fragments: List<Fragment>) {
    writer().use { it.emitFragments(fragments) }
}

fun Writer.emitFragments(fragments: List<Fragment>) {
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
            emitProc(fragment)
    }
}

private fun Writer.emitProc(fragment: Fragment.Proc) {
    val frame = fragment.frame
    val traces = fragment.body.linearize().createControlFlowGraph().traceSchedule()
    val instructions = traces.flatMap { MipsGen.codeGen(frame, it) }
    val instructions2 = frame.procEntryExit2(instructions)

    val (instructions3, alloc) = instructions2.allocateRegisters(frame)
    val (prologue, instructions4, epilogue) = frame.procEntryExit3(instructions3)

    write(prologue)
    for (instr in instructions4)
        if (instr !is Instr.Oper || instr.assem != "")
            writeLine(instr.format { alloc.name(it) })
    write(epilogue)
}

private fun Writer.emitStr(fragment: Fragment.Str) {
    writeLine("${fragment.label}: .asciiz \"${fragment.value.replace("\"", "\\\"")}\"")
}

private fun Writer.writeLine(line: String) {
    write(line)
    write("\n")
}

fun main(args: Array<String>) {
//    val fragments = compile("let function square(n: int): int = n * n in square(4)")
    val fragments = compile("let function fib(n: int): int = if n < 2 then n else fib(n - 1) + fib(n - 2) in fib(20)")
//    val fragments = compile("let function fib(n: int): int = if n < 2 then n else fib(n - 1) in fib(4)")

    File("output.s").emitFragments(fragments)
}
