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
import java.io.OutputStreamWriter
import java.io.Writer

private fun compile(code: String, filename: String): List<Fragment> {
    val exp = parseExpression(code, filename)
    println(exp)
    exp.analyzeEscapes()
    return SemanticAnalyzer.transProg(exp)
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
        if (instr !is Instr.Oper || instr.assem != "") {
//            writeLine(instr.format { alloc.name((it))}.padEnd(50) + " # " + instr.format { it.name })
            writeLine(instr.format { alloc.name(it) })
        }
    write(epilogue)
}

private fun Writer.emitStr(fragment: Fragment.Str) {
    writeLine("${fragment.label}:\n    .asciiz \"${fragment.value.replace("\"", "\\\"")}\"")
}

private fun Writer.writeLine(line: String) {
    write(line)
    write("\n")
}

fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err?.println("usage: tiger FILE.tig [OUTPUT.s]")
        System.exit(1)
    }

    val input = File(args[0])
    val output = args.getOrNull(1)?.let { File(it) }
    val fragments = compile(input.readText(), input.toString())
    val runtime = Fragment::class.java.classLoader.getResourceAsStream("runtime.s")?.use { it.reader().readText() } ?: error("could not load runtime.s")

    if (output != null) {
        output.parentFile.mkdirs()
        output.writer().use {
            it.emitFragments(fragments)
            it.write(runtime)
        }

    } else {
        val writer = OutputStreamWriter(System.out)
        writer.emitFragments(fragments)
        writer.write(runtime)
    }
}
