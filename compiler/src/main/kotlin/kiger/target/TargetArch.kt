package kiger.target

import kiger.assem.Instr
import kiger.regalloc.Coloring
import java.io.Writer

interface TargetArch {
    val frameType: FrameType
    val codeGen: CodeGen
    fun writeOutput(fragments: List<Fragment>, writer: Writer)
    fun peepholeOptimize(instructions: List<Instr>, coloring: Coloring): List<Instr>
}
