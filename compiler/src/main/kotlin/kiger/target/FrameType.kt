package kiger.target

import kiger.ir.tree.TreeExp
import kiger.temp.Label
import kiger.temp.Temp

interface FrameType {
    fun newFrame(name: Label, formalEscapes: List<Boolean>): Frame
    val FP: Temp
    val SP: Temp
    val RV: Temp
    val wordSize: Int

    val argumentRegisters: List<Temp>
    val callerSaves: List<Temp>
    val calleeSaves: List<Temp>

    fun exp(access: FrameAccess, fp: TreeExp): TreeExp

    fun externalCall(name: String, args: List<TreeExp>): TreeExp

    val tempMap: Map<Temp, Register>
    val assignableRegisters: List<Register>
}
