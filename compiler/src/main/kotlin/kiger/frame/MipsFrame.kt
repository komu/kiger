package kiger.frame

import kiger.assem.Instr
import kiger.temp.Label
import kiger.temp.Temp
import kiger.translate.seq
import kiger.tree.BinaryOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm
import kiger.utils.cons

class MipsFrame private constructor(name: Label, formalEscapes: List<Boolean>) : Frame(name) {

    override val type: FrameType
        get() = Type

    var locals = 0

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

    override fun allocLocal(escape: Boolean): FrameAccess =
        if (escape)
            FrameAccess.InFrame(locals++ * wordSize + firstLocalOffset)
        else
            FrameAccess.InReg(Temp())

    override fun procEntryExit1(body: TreeStm): TreeStm {
        val pairs = cons(RA, calleeSaves).map { Pair(allocLocal(false), it) }
        val saves = pairs.map { TreeStm.Move(exp(it.first, TreeExp.Temporary(FP)), TreeExp.Temporary(it.second)) }
        val restores = pairs.asReversed().map { TreeStm.Move(TreeExp.Temporary(it.second), exp(it.first, TreeExp.Temporary(FP))) }

        return seq(shiftInstructions + saves + body + restores)
    }

    override fun procEntryExit2(body: List<Instr>): List<Instr> =
        body + Instr.Oper("", src=listOf(ZERO, RA, SP) + calleeSaves, jump = emptyList())

    override fun procEntryExit3(body: List<Instr>): Triple<String, List<Instr>, String> {
        val offset = (locals + argumentRegisters.size) * wordSize

        val prologue = listOf(
                "$name:",
                "    sw \$fp, 0(\$sp)",              // save old fp
                "    move \$fp, \$sp",               // make sp to be new fp
                "    addiu \$sp, \$sp, -$offset")     // make new sp
                .joinToString("\n", postfix = "\n")

        val epilogue = listOf(
                "    move \$sp, \$fp",               // restore old sp
                "    lw \$fp, 0(\$sp)",              // restore old fp
                "    jr \$ra")                      // jump to return address
                .joinToString("\n", postfix = "\n")

        return Triple(prologue, body, epilogue)
    }

    companion object Type : FrameType {

        // expression evaluation and results of a functioin
        val v0 = Temp("\$v0")
        val v1 = Temp("\$v1")

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
//        val t8 = Temp("\$t8")
//        val t9 = Temp("\$t9")

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
        val GP = Temp("\$gp") // pointer for global area
        override val FP = Temp("\$fp") // frame pointer
        override val SP = Temp("\$sp") // stack pointer
        override val RA = Temp("\$ra") // return address
        override val RV = Temp("\$v0") // return value
        override val wordSize = 4
        override fun newFrame(name: Label, formalEscapes: List<Boolean>) = MipsFrame(name, formalEscapes)
        override fun exp(access: FrameAccess, exp: TreeExp) = when (access) {
            is FrameAccess.InFrame -> TreeExp.Mem(TreeExp.BinOp(BinaryOp.PLUS, exp, TreeExp.Const(access.offset)))
            is FrameAccess.InReg -> TreeExp.Temporary(access.reg)
        }
        override fun externalCall(name: String, args: List<TreeExp>): TreeExp =
                TreeExp.Call(TreeExp.Name(Label(name)), args) // TODO

        private val registerList =
            listOf(("\$a0" to a0),("\$a1" to a1),("\$a2" to a2),("\$a3" to a3),
                    ("\$t0" to t0),("\$t1" to t1),("\$t2" to t2),("\$t3" to t3),
                    ("\$t4" to t4),("\$t5" to t5),("\$t6" to t6),("\$t7" to t7),
                    ("\$s0" to s0),("\$s1" to s1),("\$s2" to s2),("\$s3" to s3),
                    ("\$s4" to s4),("\$s5" to s5),("\$s6" to s6),("\$s7" to s7),
                    ("\$fp" to FP),("\$v0" to RV),("\$sp" to SP),("\$ra" to RA))

        override val tempMap: Map<Temp, Register> = registerList.map { it -> Pair(it.second, Register(it.first)) }.toMap()
        
        val specialArguments = listOf(RV, FP, SP, RA)
        override val argumentRegisters: List<Temp> = listOf(a0, a1, a2, a3)
        override val calleeSaves: List<Temp> = listOf(s0, s1, s2, s3, s4, s5, s6, s7)
        override val callerSaves: List<Temp> = listOf(t0, t1, t2, t3, t4, t5, t6, t7)
        private val firstLocalOffset = wordSize // fp is stored at 0, locals/params start at fp + wordSize
        private val firstFormalOffset = firstLocalOffset

        // a list of all register name, which can be used for coloring
        override val registers: List<Register> = (argumentRegisters + calleeSaves + callerSaves + specialArguments).map { x -> tempMap[x]!! }

    }
}
