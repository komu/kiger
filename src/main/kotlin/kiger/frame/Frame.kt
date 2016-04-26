package kiger.frame

import kiger.temp.Label
import kiger.temp.Temp
import kiger.translate.Access
import kiger.tree.BinaryOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm

abstract class Frame(val name: Label) {

    abstract val formals: List<FrameAccess>

    fun allocLocal(escape: Boolean): Access = TODO()

    fun procEntryExit1(body: TreeStm): TreeStm {
        // TODO: dummy impl
        return TreeStm.Exp(TreeExp.Const(0))
    }
}

class JouletteFrame private constructor(name: Label, formalEscapes: List<Boolean>) : Frame(name) {

    override val formals: List<FrameAccess> = formalEscapes.mapIndexed { index, escape ->
        if (escape)
            FrameAccess.InFrame(firstFormalOffset + index * wordSize)
        else
            FrameAccess.InReg(Temp())
    }

    val shiftInstructions: List<TreeStm> = run {
        fun viewShift(acc: FrameAccess, r: Temp) = TreeStm.Move(exp(acc, TreeExp.Temporary(FP)), TreeExp.Temporary(r))

        if (formals.size > argumentRegisters.size)
            error("passed ${formals.size} arguments, but only ${argumentRegisters.size} arguments are supported")

        formals.mapIndexed { i, access -> viewShift(access, argumentRegisters[i]) }
    }

    companion object : FrameType {

        // expression evaluation and results of a functioin
        val v0 = Temp()
        val v1 = Temp()

        // arguments
        val a0 = Temp()
        val a1 = Temp()
        val a2 = Temp()
        val a3 = Temp()

        // temporary - not preserved across call
        val t0 = Temp()
        val t1 = Temp()
        val t2 = Temp()
        val t3 = Temp()
        val t4 = Temp()
        val t5 = Temp()
        val t6 = Temp()
        val t7 = Temp()
        val t8 = Temp()
        val t9 = Temp()

        // saved temporary - preserved across call
        val s0 = Temp()
        val s1 = Temp()
        val s2 = Temp()
        val s3 = Temp()
        val s4 = Temp()
        val s5 = Temp()
        val s6 = Temp()
        val s7 = Temp()

        val ZERO = Temp() // constant 0
        val GP = Temp() // pointer for global area
        override val FP = Temp() // frame pointer
        override val SP = Temp() // stack pointer
        override val RA = Temp() // return address
        override val RV = Temp() // return value
        override val wordSize = 4
        override fun newFrame(name: Label, formalEscapes: List<Boolean>) = JouletteFrame(name, formalEscapes)
        override fun exp(access: FrameAccess, exp: TreeExp) = when (access) {
            is FrameAccess.InFrame -> TreeExp.Mem(TreeExp.BinOp(BinaryOp.PLUS, exp, TreeExp.Const(access.offset)))
            is FrameAccess.InReg   -> TreeExp.Temporary(access.reg)
        }
        override fun externalCall(name: String, args: List<TreeExp>): TreeExp {
            return TreeExp.Const(0) // TODO: dummy
        }
        override val argumentRegisters: List<Temp> = listOf(a0, a1, a2, a3)
        override val calleeSaves: List<Temp> = listOf(s0, s1, s2, s3, s4, s5, s6, s7)
        override val callerSaves: List<Temp> = listOf(t0, t1, t2, t3, t4, t5, t6, t7, t8, t9)

        private val firstFormalOffset = wordSize // fp is stored at 0, params start at fp + wordSize
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
