package kiger.codegen

import kiger.assem.Instr
import kiger.frame.Frame
import kiger.frame.FrameType
import kiger.tree.TreeStm

interface CodeGen {
    val frameType: FrameType
    fun codeGen(frame: Frame, stm: TreeStm): List<Instr>
}
