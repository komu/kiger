package kiger

import kiger.assem.Instr
import kiger.canon.createControlFlowGraph
import kiger.canon.linearize
import kiger.canon.traceSchedule
import kiger.escape.analyzeEscapes
import kiger.frame.Fragment
import kiger.parser.parseExpression
import kiger.regalloc.allocateRegisters
import kiger.target.CodeGen
import kiger.target.TargetArch
import kiger.target.mips.MipsTarget
import kiger.target.x64.X64Target
import kiger.translate.SemanticAnalyzer
import java.io.File
import java.io.OutputStreamWriter
import java.io.Writer

private fun compile(targetArch: TargetArch, code: String, filename: String): List<Fragment>? {
    val exp = parseExpression(code, filename)
    exp.analyzeEscapes()
    val analyzer = SemanticAnalyzer(targetArch)
    val result = analyzer.transProg(exp)
    return if (analyzer.diagnostics.errorCount == 0) result else null
}

fun Writer.emitProc(codeGen: CodeGen, fragment: Fragment.Proc) {
    val frame = fragment.frame
    val traces = fragment.body.linearize().createControlFlowGraph().traceSchedule()
    val instructions = traces.flatMap { codeGen.codeGen(frame, it) }
    val instructions2 = frame.procEntryExit2(instructions)

    val (instructions3, alloc) = instructions2.allocateRegisters(codeGen, frame)
    val (prologue, instructions4, epilogue) = frame.procEntryExit3(instructions3)

    write(prologue)
    for (instr in instructions4)
        if (instr !is Instr.Oper || instr.assem != "") {
//            writeLine(instr.format { alloc.name((it))}.padEnd(50) + " # " + instr.format { it.name })
            writeLine(instr.format { alloc.name(it) })
        }
    write(epilogue)
}

fun Writer.writeLine(line: String) {
    write(line)
    write("\n")
}

fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err?.println("usage: tiger FILE.tig [OUTPUT.s]")
        System.exit(1)
    }

    val target = if (System.getProperty("arch") == "x86_64") X64Target else MipsTarget
    val input = File(args[0])
    val output = args.getOrNull(1)?.let { File(it) }
    val fragments = compile(target, input.readText(), input.toString())
    if (fragments == null) {
        System.exit(1)
        return
    }

    if (output != null) {
        output.parentFile.mkdirs()
        output.writer().use {
            target.writeOutput(fragments, it)
        }

    } else {
        target.writeOutput(fragments, OutputStreamWriter(System.out))
    }
}
