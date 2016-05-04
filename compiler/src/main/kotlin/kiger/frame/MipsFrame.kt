package kiger.frame

import kiger.assem.Instr
import kiger.temp.Label
import kiger.temp.Temp
import kiger.translate.seq
import kiger.tree.BinaryOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm

class MipsFrame private constructor(name: Label, formalEscapes: List<Boolean>) : Frame(name) {

    override val type: FrameType
        get() = Type

    var locals = 0

    override val formals: List<FrameAccess> = formalEscapes.mapIndexed { index, escape ->
        allocLocal(escape)
//        if (escape)
//            FrameAccess.InFrame(firstFormalOffset + index * wordSize)
//        else
//            FrameAccess.InReg(Temp())
    }

    val shiftInstructions: List<TreeStm> = run {
        fun viewShift(acc: FrameAccess, r: Temp) = TreeStm.Move(exp(acc, TreeExp.Temporary(FP)), TreeExp.Temporary(r))

        if (formals.size > argumentRegisters.size)
            error("passed ${formals.size} arguments, but only ${argumentRegisters.size} arguments are supported")

        formals.mapIndexed { i, access -> viewShift(access, argumentRegisters[i]) }
    }

    override fun allocLocal(escape: Boolean): FrameAccess =
        if (escape)
            FrameAccess.InFrame(++locals * wordSize + firstLocalOffset)
        else
            FrameAccess.InReg(Temp.gen())

    override fun procEntryExit1(body: TreeStm): TreeStm {
        val pairs = (calleeSaves + RA).map { Pair(Temp.gen(), it) }

        val saves = pairs.map { TreeStm.Move(TreeExp.Temporary(it.first), TreeExp.Temporary(it.second)) }
        val restores = pairs.asReversed().map { TreeStm.Move(TreeExp.Temporary(it.second), TreeExp.Temporary(it.first)) }

        return seq(shiftInstructions + saves + body + restores)
    }

    override fun procEntryExit2(body: List<Instr>): List<Instr> {
        val enter = Instr.Oper("", src = listOf(ZERO, RA, SP, FP) + calleeSaves + argumentRegisters, jump = emptyList())
        val exit = Instr.Oper("", src = listOf(ZERO, RA, SP, FP, RV) + calleeSaves, jump = emptyList())
        return listOf(enter) + body + exit
    }

    override fun procEntryExit3(body: List<Instr>): Triple<String, List<Instr>, String> {
        // TODO: offset calculation is wrong since we still do "sw $a0, 4($fp)" at the start to save the link to frame
        val frameSize = locals * wordSize


        // TODO: use virtual frame pointer
        if (frameSize != 0) {
            val offset = frameSize + firstLocalOffset // add extra word for stored frame pointer
            val prologue = listOf(
                    "$name:",
                    "    sw \$fp, 0(\$sp)", // save old fp
                    "    move \$fp, \$sp", // make sp to be new fp
                    "    addiu \$sp, \$sp, -$offset")     // make new sp
                    .joinToString("\n", postfix = "\n")

            val epilogue = listOf(
                    "    move \$sp, \$fp", // restore old sp
                    "    lw \$fp, 0(\$sp)", // restore old fp
                    "    jr \$ra")                      // jump to return address
                    .joinToString("\n", postfix = "\n")

            return Triple(prologue, body, epilogue)
        } else {
            val prologue = "$name\n"
            return Triple(prologue, body, "    jr \$ra\n")
        }
    }

    companion object Type : FrameType {

        // expression evaluation and results of a functioin
        val v0 = Temp("\$v0")
//        val v1 = Temp("\$v1")

        // arguments
        val a0 = Temp("\$a0")
        val a1 = Temp("\$a1")
        val a2 = Temp("\$a2")
        val a3 = Temp("\$a3")

        // temporary - not preserved across call
        val t0 = Temp("\$t0")
        val t1 = Temp("\$t1")
        val t2 = Temp("\$t2")
        val t3 = Temp("\$t3")
        val t4 = Temp("\$t4")
        val t5 = Temp("\$t5")
        val t6 = Temp("\$t6")
        val t7 = Temp("\$t7")
        val t8 = Temp("\$t8")
        val t9 = Temp("\$t9")

        // saved temporary - preserved across call
        val s0 = Temp("\$s0")
        val s1 = Temp("\$s1")
        val s2 = Temp("\$s2")
        val s3 = Temp("\$s3")
        val s4 = Temp("\$s4")
        val s5 = Temp("\$s5")
        val s6 = Temp("\$s6")
        val s7 = Temp("\$s7")

        val ZERO = Temp("\$zero") // constant 0
        //val GP = Temp("\$gp") // pointer for global area
        override val FP = Temp("\$fp") // frame pointer
        override val SP = Temp("\$sp") // stack pointer
        override val RA = Temp("\$ra") // return address
        override val RV = v0 // return value
        override val wordSize = 4
        override fun newFrame(name: Label, formalEscapes: List<Boolean>) = MipsFrame(name, formalEscapes)
        override fun exp(access: FrameAccess, exp: TreeExp) = when (access) {
            is FrameAccess.InFrame -> TreeExp.Mem(TreeExp.BinOp(BinaryOp.PLUS, exp, TreeExp.Const(access.offset)))
            is FrameAccess.InReg -> TreeExp.Temporary(access.reg)
        }
        override fun externalCall(name: String, args: List<TreeExp>): TreeExp =
                TreeExp.Call(TreeExp.Name(Label(name)), args) // TODO

        private val specialRegisters = listOf(RV, FP, SP, RA, ZERO)
        override val argumentRegisters = listOf(a0, a1, a2, a3)
        override val calleeSaves = listOf(s0, s1, s2, s3, s4, s5, s6, s7)
        override val callerSaves = listOf(t0, t1, t2, t3, t4, t5, t6, t7, t8, t9)
        private val firstLocalOffset = wordSize // fp is stored at 0, locals/params start at fp + wordSize

        private val registerList = argumentRegisters + calleeSaves + callerSaves + specialRegisters

        override val tempMap: Map<Temp, Register> = registerList.map { it -> Pair(it, Register(it.name)) }.toMap()

        override val registers: List<Register> = tempMap.values.toList()
    }
}
