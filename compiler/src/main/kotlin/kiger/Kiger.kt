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
    val instructions = MipsGen.codeGen(fragment.frame, fragment.body)
    val (instructions2, alloc) = instructions.allocateRegisters(fragment.frame)
    val (prologue, instructions3, epilogue) = fragment.frame.procEntryExit3(instructions2)

    write(prologue)
    for (instr in instructions3)
        if (instr !is Instr.Oper || instr.assem != "")
            writeLine(instr.format { alloc[it] })
    write(epilogue)
}

/*
fun emitproc out (F.PROC{body,frame}) =
    let val _ = print ("emit " ^ Symbol.name (Frame.name frame) ^ "\n")
              val stms = Canon.linearize body
        val stms' = Canon.traceSchedule(Canon.basicBlocks stms)
              val instrs = List.concat(map (MipsGen.codegen frame) stms')
        val instrs2 = Frame.procEntryExit2 (frame,instrs)
        val format1 = Assem.format(Frame.temp_name)
        val (instrs2',alloc) = RegAlloc.alloc(instrs2,frame)
        val {prolog,body,epilog} = Frame.procEntryExit3(frame,instrs2')
        val instrs'' = addtab body
        val format0 = Assem.format(tempname alloc)
    in
      TextIO.output(out,prolog);
      app (fn i => TextIO.output(out,(format0 i) ^ "\n")) instrs'';
      TextIO.output(out,epilog)
    end
 */

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
