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
        override val RV = Temp("rv")
        override val wordSize = 4
        override fun newFrame(name: Label, formalEscapes: List<Boolean>) = JouletteFrame(name, formalEscapes)
        override fun exp(access: FrameAccess, exp: TreeExp) = TODO()
        override fun externalCall(name: String, args: List<TreeExp>) = TODO()
    }
}

interface FrameType {
    val FP: Temp
    val RV: Temp
    val wordSize: Int
    fun newFrame(name: Label, formalEscapes: List<Boolean>): Frame
    fun exp(access: FrameAccess, exp: TreeExp): TreeExp
    fun externalCall(name: String, args: List<TreeExp>): TreeExp
}
