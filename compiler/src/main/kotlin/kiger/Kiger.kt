package kiger

import kiger.assem.Instr
import kiger.codegen.MipsGen
import kiger.frame.Fragment
import kiger.parser.parseExpression
import kiger.regalloc.createFlowGraph
import kiger.translate.SemanticAnalyzer
import java.io.File
import java.io.Writer

private fun compile(code: String): List<Fragment> =
    SemanticAnalyzer.transProg(parseExpression(code))

fun File.writeFragments(fragments: List<Fragment>) {
    writer().use { it.writeFragments(fragments) }
}

fun Writer.writeFragments(fragments: List<Fragment>) {
    val strs = fragments.filterIsInstance<Fragment.Str>()
    val procs = fragments.filterIsInstance<Fragment.Proc>()

    if (strs.any()) {
        writeLine("    .data")
        for (fragment in strs)
            writeStr(fragment)
    }

    if (procs.any()) {
        writeLine("    .text")
        for (fragment in procs)
            writeProc(fragment)
    }
}

private fun Writer.writeProc(fragment: Fragment.Proc) {
    val (prologue, instructions, epilogue) = fragment.frame.procEntryExit3(MipsGen.codeGen(fragment.frame, fragment.body))

    val flowGraph = instructions.createFlowGraph()
    println(flowGraph)

    write(prologue)
    for (instr in instructions)
        if (instr !is Instr.Oper || instr.assem != "")
            writeLine("$instr")
    write(epilogue)
}

private fun Writer.writeStr(fragment: Fragment.Str) {
    writeLine("${fragment.label}: .asciiz \"${fragment.value.replace("\"", "\\\"")}\"")
}

private fun Writer.writeLine(line: String) {
    write(line)
    write("\n")
}

fun main(args: Array<String>) {
    val fragments = compile("let function square(n: int): int = n * n in square(4)")

    File("output.s").writeFragments(fragments)
}
