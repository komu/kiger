package kiger.target.x64

import kiger.assem.Instr
import kiger.frame.Frame
import kiger.frame.FrameAccess
import kiger.frame.FrameType
import kiger.frame.Register
import kiger.temp.Label
import kiger.temp.Temp
import kiger.translate.seq
import kiger.tree.BinaryOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm

/**
 * http://eli.thegreenplace.net/2011/09/06/stack-frame-layout-on-x86-64/
 */
class X64Frame private constructor(name: Label, formalEscapes: List<Boolean>) : Frame(name) {

    override val type: FrameType
        get() = Type

    var locals = 0

    override val formals: List<FrameAccess> = formalEscapes.map { allocLocal(it) }

    /**
     * Instructions to copy all incoming arguments to correct places.
     */
    private val shiftInstructions: List<TreeStm> = run {
        fun viewShift(acc: FrameAccess, arg: Temp) = TreeStm.Move(exp(acc, TreeExp.Temporary(FP)), TreeExp.Temporary(arg))

        if (formals.size > argumentRegisters.size)
            error("passed ${formals.size} arguments, but only ${argumentRegisters.size} arguments are supported")

        formals.mapIndexed { i, access -> viewShift(access, argumentRegisters[i]) }
    }

    override fun allocLocal(escape: Boolean): FrameAccess =
        if (escape)
            FrameAccess.InFrame(-(locals++ * wordSize + firstLocalOffset))
        else
            FrameAccess.InReg(Temp.gen())

    override fun procEntryExit1(body: TreeStm): TreeStm {
        val pairs = (calleeSaves + RA).map { Pair(Temp.gen(), it) }

        val saves = pairs.map { TreeStm.Move(TreeExp.Temporary(it.first), TreeExp.Temporary(it.second)) }
        val restores = pairs.asReversed().map { TreeStm.Move(TreeExp.Temporary(it.second), TreeExp.Temporary(it.first)) }

        return seq(shiftInstructions + saves + body + restores)
    }

    override fun procEntryExit2(body: List<Instr>): List<Instr> {
        // TODO book does not have enter
//        val enter = Instr.Oper("", dst = listOf(ZERO, RA, SP, FP) + calleeSaves + argumentRegisters)
        val enter = Instr.Oper("", dst = listOf(RA, SP, FP) + calleeSaves + argumentRegisters)

        // Dummy instruction that simply tells register allocator what registers are live at the end
//        val sink = Instr.Oper("", src = listOf(ZERO, RA, SP, FP, RV) + calleeSaves, jump = emptyList())
        // TODO: book does not keep RV live
        val sink = Instr.Oper("", src = listOf(RA, SP, FP) + calleeSaves, jump = emptyList())

        return listOf(enter) + body + sink
    }

    override fun procEntryExit3(body: List<Instr>): Triple<String, List<Instr>, String> {
        // TODO: offset calculation is wrong since we still do "sw $a0, 4($fp)" at the start to save the link to frame
        val frameSize = locals * wordSize

        // TODO: use virtual frame pointer
        if (frameSize != 0) {
            val offset = frameSize + firstLocalOffset // add extra word for stored frame pointer
            val prologue = listOf(
                    "$name:",
                    "    pushq  %rbp",      // save old fp
                    "    movq %rsp, %rbp",  // make sp to be new fp
                    "    subq \$$offset, %rsp")     // make new sp
                    .joinToString("\n", postfix = "\n")

            val epilogue = listOf(
                    "    addq \$$offset, %rsp", // restore old sp
                    "    popq %rbp", // restore old fp
                    "    retq")             // jump to return address
                    .joinToString("\n", postfix = "\n")

            return Triple(prologue, body, epilogue)
        } else {
            val prologue = "$name\n"
            return Triple(prologue, body, "    jr \$ra\n")
        }
    }

    companion object Type : FrameType {

        // expression evaluation and results of a function
        val v0 = Temp("\$v0")
//        val v1 = Temp("\$v1")

        val rax = Temp("%rax")
        val rbx = Temp("%rbx")
        val rcx = Temp("%rcx")
        val rdx = Temp("%rdx")
        val rbp = Temp("%rbp")
        val rsp = Temp("%rsp")
        val rsi = Temp("%rsi")
        val rdi = Temp("%rdi")
        val r8 = Temp("%r8")
        val r9 = Temp("%r9")
        val r10 = Temp("%r10")
        val r11 = Temp("%r11")
        val r12 = Temp("%r12")
        val r13 = Temp("%r13")
        val r14 = Temp("%r14")
        val r15 = Temp("%r15")

        override val FP = rbp
        override val SP = rsp
        val RA = Temp("\$ra") // TODO: unused
        override val RV = rax
        override val wordSize = 8
        override fun newFrame(name: Label, formalEscapes: List<Boolean>) = X64Frame(name, formalEscapes)
        override fun exp(access: FrameAccess, fp: TreeExp) = when (access) {
            is FrameAccess.InFrame -> TreeExp.Mem(TreeExp.BinOp(BinaryOp.PLUS, fp, TreeExp.Const(access.offset)))
            is FrameAccess.InReg -> TreeExp.Temporary(access.reg)
        }
        override fun externalCall(name: String, args: List<TreeExp>): TreeExp =
                TreeExp.Call(TreeExp.Name(Label(name)), args) // TODO

        override val argumentRegisters = listOf(rdi, rsi, rdx, rcx, r8, r9)
        override val calleeSaves = listOf(rbp, rbx, r12, r13, r14, r15)
        override val callerSaves = listOf(rax, rcx, rdx, rsp, rsi, rdi, r8, r9, r10, r11)
        private val firstLocalOffset = wordSize // fp is stored at 0, locals/params start at fp + wordSize

        private val registerList = calleeSaves + callerSaves

        override val tempMap: Map<Temp, Register> = registerList.map { it -> Pair(it, Register(it.name)) }.toMap()

        override val assignableRegisters: List<Register> = tempMap.values.toList()
    }
}
