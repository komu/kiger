package kiger

import kiger.assem.Instr
import kiger.codegen.MipsGen
import kiger.frame.Fragment
import kiger.parser.parseExpression
import kiger.regalloc.allocateRegisters
import kiger.translate.SemanticAnalyzer
import java.io.File
import java.io.Writer

private fun compile(code: String): List<Fragment> =
    SemanticAnalyzer.transProg(parseExpression(code))

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
    val instructions = MipsGen.codeGen(frame, fragment.body)
    val (instructions2, alloc) = instructions.allocateRegisters(frame)
    val (prologue, instructions3, epilogue) = frame.procEntryExit3(instructions2)

    write(prologue)
    for (instr in instructions3)
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
    val fragments = compile("let function square(n: int): int = n * n in square(4)")

    File("output.s").emitFragments(fragments)
}
