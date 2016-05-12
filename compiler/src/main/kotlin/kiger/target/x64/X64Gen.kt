package kiger.target.x64

import kiger.assem.Instr
import kiger.assem.Instr.Oper
import kiger.frame.Frame
import kiger.ir.BinaryOp.*
import kiger.ir.RelOp
import kiger.ir.RelOp.*
import kiger.ir.tree.TreeExp
import kiger.ir.tree.TreeExp.*
import kiger.ir.tree.TreeStm
import kiger.ir.tree.TreeStm.Branch.CJump
import kiger.ir.tree.TreeStm.Branch.Jump
import kiger.ir.tree.TreeStm.Move
import kiger.target.CodeGen
import kiger.target.mips.TooManyArgsException
import kiger.temp.Label
import kiger.temp.Temp
import kiger.utils.cons

object X64Gen : CodeGen {
    override val frameType = X64Frame

    override fun codeGen(frame: Frame, stm: TreeStm): List<Instr> {
        val generator = X64CodeGenerator(frame as X64Frame)

        generator.munchStm(stm)
        return generator.instructions
    }
}

private class X64CodeGenerator(val frame: X64Frame) {

    val frameType = X64Gen.frameType
    val instructions = mutableListOf<Instr>()

    // TODO: do we need to save args?
    val callDefs = X64Frame.callerSaves + X64Frame.argumentRegisters + X64Frame.RV

    private fun emit(instr: Instr) {
        instructions += instr
    }

    private fun emit(assem: String, dst: List<Temp> = emptyList(), src: List<Temp> = emptyList(), jump: List<Label>? = null) {
        emit(Oper(assem, dst, src, jump))
    }

    private fun emit(assem: String, dst: Temp, src: List<Temp> = emptyList()) {
        emit(Oper(assem, listOf(dst), src))
    }

    private fun emitMove(src: Temp, dst: Temp) {
        if (src == frameType.FP)
            emit("leaq ${frameExp(0)}, 'd0", src = listOf(frameType.SP), dst = dst)
        else
            emit(Instr.Move("movq 's0, 'd0", src = src, dst = dst))
    }

    private inline fun withResult(gen: (Temp) -> Unit): Temp {
        val t = Temp.gen()
        gen(t)
        return t
    }

    // TODO: use lea where applicable [eax + edx*4 -4] -> -4(%eax, %edx, 4)

    fun munchStm(stm: TreeStm) {
        // emit("# $stm")

        when (stm) {
            is TreeStm.Seq -> {
                munchStm(stm.lhs)
                munchStm(stm.rhs)
            }

            is TreeStm.Labeled ->
                emit(Instr.Lbl("${stm.label.name}:", stm.label))

            is Move ->
                munchMove(stm.target, stm.source)

            is TreeStm.Branch -> when (stm) {
                is Jump     -> munchJump(stm.exp, stm.labels)
                is CJump    -> munchCJump(stm.relop, stm.lhs, stm.rhs, stm.trueLabel, stm.falseLabel)
            }

            is TreeStm.Exp ->
                munchExp(stm.exp)
        }
    }

    private fun munchCall(exp: Call): Temp {
        if (exp.func is Name)
            emit("callq ${exp.func.label}", src = munchArgs(exp.args), dst = callDefs)
        else
            emit("callq 's0", src = cons(munchExp(exp.func), munchArgs(exp.args)), dst = callDefs)

        return withResult { r -> emitMove(frameType.RV, r) }
    }

    private fun munchArgs(args: List<TreeExp>): List<Temp> {
        val argumentRegisters = frameType.argumentRegisters

        if (args.size > argumentRegisters.size)
            throw TooManyArgsException("support only ${argumentRegisters.size} arguments, but got ${args.size}")

        // First evaluate all expression to temporaries
        val temps = args.map { munchExp(it) }

        // ... and then generate moves to final locations. It's important not to create
        // the moves to soon, because otherwise registers could be trashed. The register
        // allocator will try to get rid of the moves.
        temps.mapIndexed { i, temp ->
            emitMove(temp, argumentRegisters[i])
        }

        return argumentRegisters.take(args.size)
    }


    private fun emitBinOp(name: String, lhs: Temp, rhs: Temp): Temp =
        withResult { r ->
            emitMove(lhs, r)
            emit("$name 's0, 'd0", dst = r, src = listOf(rhs, lhs, r))
        }

    private fun emitBinOp(name: String, lhs: Temp, rhs: Int): Temp =
        withResult { r ->
            emitMove(lhs, r)
            emit("$name \$$rhs, 'd0", dst = r, src = listOf(lhs, r))
        }

    private fun munchExp(exp: TreeExp): Temp {
        return when (exp) {
            is Temporary -> exp.temp
            is Const ->
                if (exp.value == 0)
                    withResult { r -> emit("xorq 'd0, 'd0", dst = r) }
                else
                    withResult { r -> emit("movq \$${exp.value}, 'd0", dst = r) }
            is Name -> withResult { r -> emit("leaq ${exp.label}(%rip), 'd0", dst = r) }
            is Call -> munchCall(exp)
            is Mem -> munchLoad(exp.exp)
            is BinOp -> when (exp.binop) {
                PLUS -> when {
                    exp.lhs is Const && exp.lhs.value == 1 -> {
                        val r = munchExp(exp.rhs)
                        emit("incq 's0", src=listOf(r), dst=r)
                        r
                    }
                    exp.lhs is Const ->
                        emitBinOp("addq", munchExp(exp.rhs), exp.lhs.value)
                    exp.rhs is Const && exp.rhs.value == 1 -> {
                        val e = munchExp(exp.lhs)
                        withResult { r ->
                            emitMove(e, r)
                            emit("incq 's0", src = listOf(r), dst = r)
                        }
                    }
                    exp.rhs is Const ->
                        emitBinOp("addq", munchExp(exp.lhs), exp.rhs.value)
                    else ->
                        emitBinOp("addq", munchExp(exp.lhs), munchExp(exp.rhs))
                }
                MINUS -> when {
                    exp.rhs is Const && exp.rhs.value == 1 -> {
                        val e = munchExp(exp.lhs)
                        withResult { r ->
                            emitMove(e, r)
                            emit("decq 's0", src=listOf(r), dst=r)
                        }
                    }
                    exp.rhs is Const ->
                        emitBinOp("subq", munchExp(exp.lhs), exp.rhs.value)
                    else ->
                        emitBinOp("subq", munchExp(exp.lhs), munchExp(exp.rhs))
                }
                DIV -> {
                    val rhs = munchExp(exp.rhs)
                    val lhs = munchExp(exp.lhs)
                    emitMove(lhs, X64Frame.rax)
                    emit("xorq %rdx, %rdx", dst = X64Frame.rdx)
                    emit("idivq 's0", src = listOf(rhs, rhs, X64Frame.rdx), dst = listOf(X64Frame.rax, X64Frame.rdx))
                    withResult { r -> emitMove(X64Frame.rax, r) }
                }
                MUL -> {
                    val lhs = munchExp(exp.lhs)
                    val rhs = munchExp(exp.rhs)
                    emitMove(rhs, X64Frame.rax)
                    emit("mulq 's0", src = listOf(lhs, rhs, X64Frame.rax), dst = listOf(X64Frame.rax, X64Frame.rdx))
                    withResult { r -> emitMove(X64Frame.rax, r) }
                }
                else -> TODO("unsupported binop ${exp.binop}")
            }
            else ->
                TODO("$exp")
        }
    }

    private fun munchMove(dst: TreeExp, src: TreeExp) = when {
        dst is Mem                          -> munchStore(dst.exp, src)
        dst is Temporary && src is Const    -> munchConst(dst, src.value)
        dst is Temporary                    -> emitMove(munchExp(src), dst.temp)
        else                                -> error("unsupported move: $dst $src")
    }

    private fun munchConst(dst: Temporary, value: Int) {
        if (value == 0)
            emit("xorq 'd0, 'd0", dst = dst.temp)
        else
            emit("movq \$$value, 'd0", dst = dst.temp)
    }

    private fun frameExp(offset: Int) =
        "${frame.name}_frameSize+$offset('s0)"

    private fun munchStore(addr: TreeExp, src: TreeExp) = when {
        addr is BinOp && addr.binop == PLUS && addr.lhs is Temporary && addr.rhs is Const && addr.lhs.temp == frameType.FP ->
            emit("movq 's1, ${frameExp(addr.rhs.value)}", src = listOf(frameType.SP, munchExp(src)))
        src is Const && addr is BinOp && addr.binop == PLUS && addr.rhs is Const ->
            emit("movq \$${src.value}, ${addr.rhs.value}('s0)", src = listOf(munchExp(addr.lhs)))
        src is Const && addr is BinOp && addr.binop == PLUS && addr.lhs is Const ->
            emit("movq \$${src.value}, ${addr.lhs.value}('s0)", src = listOf(munchExp(addr.rhs)))
        addr is BinOp && addr.binop == PLUS && addr.rhs is Const ->
            emit("movq 's1, ${addr.rhs.value}('s0)", src = listOf(munchExp(addr.lhs), munchExp(src)))
        addr is BinOp && addr.binop == PLUS && addr.lhs is Const ->
            emit("movq 's1, ${addr.lhs.value}('s0)", src = listOf(munchExp(addr.rhs), munchExp(src)))
        src is Const ->
            emit("movq \$${src.value}, ('s0)", src = listOf(munchExp(addr)))
        else ->
            emit("movq 's1, ('s0)", src = listOf(munchExp(addr), munchExp(src)))
    }

    private fun munchLoad(addr: TreeExp): Temp = when {
        addr is BinOp && addr.binop == PLUS && addr.lhs is Temporary && addr.rhs is Const && addr.lhs.temp == frameType.FP ->
            withResult { r -> emit("movq ${frameExp(addr.rhs.value)}, 'd0", src = listOf(frameType.SP), dst = r) }
        addr is BinOp && addr.binop == PLUS && addr.rhs is Const ->
            withResult { r -> emit("movq ${addr.rhs.value}('s0), 'd0", src = listOf(munchExp(addr.lhs)), dst = r) }
        addr is BinOp && addr.binop == PLUS && addr.lhs is Const ->
            withResult { r -> emit("movq ${addr.lhs.value}('so), 'd0", src = listOf(munchExp(addr.rhs)), dst = r) }
        addr is BinOp && addr.binop == PLUS && addr.rhs is BinOp && addr.rhs.binop == MUL && addr.rhs.rhs is Const ->
            withResult { r -> emit("movq ('s0, 's1, ${addr.rhs.rhs.value}), 'd0", src = listOf(munchExp(addr.lhs), munchExp(addr.rhs.lhs)), dst = r) }
//        // TODO: add similar movqs for other constant loads
//        addr is BinOp && addr.binop == PLUS ->
//            emitResult { r -> Oper("movq ('s0, 's1), 'd0", src = listOf(munchExp(addr.lhs), munchExp(addr.rhs)), dst = listOf(r)) }
//        addr is BinOp && addr.binop == MINUS && addr.rhs is Const ->
//            emitResult { r -> Oper("movq ${-addr.rhs.value}('s0), 'd0", src = listOf(munchExp(addr.lhs)), dst = listOf(r)) }
        addr is Const ->
            withResult { r -> emit("movq (${addr.value}), 'd0", dst = r) }
        else ->
            withResult { r -> emit("movq ('s0), 'd0", src = listOf(munchExp(addr)), dst = r) }
    }

    private fun munchJump(target: TreeExp, labels: List<Label>) {
        if (target is Name)
            emit("jmp 'j0", jump = listOf(target.label))
        else
            emit("jmp 's0", src = listOf(munchExp(target)), jump = labels)
    }

    private fun munchCJump(relop: RelOp, lhs: TreeExp, rhs: TreeExp, trueLabel: Label, falseLabel: Label) {
        val op = munchCompare(lhs, relop, rhs)

        emit("${op.not().toInstruction()} 'j1", jump = listOf(trueLabel, falseLabel))
    }

    private fun RelOp.toInstruction(): String = when (this) {
        EQ -> "je"
        NE -> "jne"
        GE -> "jge"
        GT -> "jg"
        LE -> "jle"
        LT -> "jl"
        else -> error("unsupported relop $this")
    }

    private fun munchCompare(lhs: TreeExp, relop: RelOp, rhs: TreeExp): RelOp = when {
        lhs is Const -> {
            emit("cmpq \$${lhs.value}, 's0", src = listOf(munchExp(rhs)))
            relop.commute()
        }
        rhs is Const -> {
            emit("cmpq \$${rhs.value}, 's0", src = listOf(munchExp(lhs)))
            relop
        }
        else -> {
            emit("cmpq 's0, 's1", src = listOf(munchExp(rhs), munchExp(lhs)))
            relop
        }
    }
}
