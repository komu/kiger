package kiger.frame

import kiger.temp.Label
import kiger.temp.Temp
import kiger.translate.Access
import kiger.tree.TreeExp
import kiger.tree.TreeStm

abstract class Frame(val name: Label, private val formalEscapes: List<Boolean>) {

    val formals: List<FrameAccess>
        get() = TODO()

    fun allocLocal(escape: Boolean): Access = TODO()

    fun procEntryExit1(body: TreeStm): TreeStm = TODO()
}

class JouletteFrame(name: Label, formalEscapes: List<Boolean>) : Frame(name, formalEscapes) {

    companion object : FrameType {
        override val FP = Temp("fp")
        override val SP = Temp("sp")
        override val RV = Temp("rv")
        override val RA = Temp("ra")
        override val wordSize = 4
        override fun newFrame(name: Label, formalEscapes: List<Boolean>) = JouletteFrame(name, formalEscapes)
        override fun exp(access: FrameAccess, exp: TreeExp) = TODO()
        override fun externalCall(name: String, args: List<TreeExp>) = TODO()
        override val argumentRegisters: List<Temp>
            get() = TODO()
        override val callerSaves: List<Temp>
            get() = TODO()
        override val calleeSaves: List<Temp>
            get() = TODO()
    }
}

interface FrameType {
    fun newFrame(name: Label, formalEscapes: List<Boolean>): Frame
    val FP: Temp
    val SP: Temp
    val RV: Temp
    val RA: Temp
    val wordSize: Int

    val argumentRegisters: List<Temp>
    val callerSaves: List<Temp>
    val calleeSaves: List<Temp>

    fun exp(access: FrameAccess, exp: TreeExp): TreeExp

    fun externalCall(name: String, args: List<TreeExp>): TreeExp
}
