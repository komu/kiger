package kiger.target.x64

import kiger.assem.Instr
import kiger.assem.Instr.Oper
import kiger.frame.Frame
import kiger.target.CodeGen
import kiger.target.mips.TooManyArgsException
import kiger.temp.Label
import kiger.temp.Temp
import kiger.tree.BinaryOp.*
import kiger.tree.RelOp
import kiger.tree.RelOp.*
import kiger.tree.TreeExp
import kiger.tree.TreeExp.*
import kiger.tree.TreeStm
import kiger.tree.TreeStm.Branch.CJump
import kiger.tree.TreeStm.Branch.Jump
import kiger.tree.TreeStm.Move
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

    private fun emit(assem: String, dst: List<Temp> = emptyList(), src: List<Temp> = emptyList(), jump: List<Label> = emptyList()) {
        emit(Oper(assem, dst, src, jump))
    }

    private fun emit(assem: String, dst: Temp, src: List<Temp> = emptyList()) {
        emit(Oper(assem, listOf(dst), src))
    }

    private fun emitMove(src: Temp, dst: Temp) {
        emit(Instr.Move("movq 's0, 'd0", src = src, dst = dst))
    }

    private inline fun withResult(gen: (Temp) -> Unit): Temp {
        val t = Temp.gen()
        gen(t)
        return t
    }

    // TODO: use lea where applicable [eax + edx*4 -4] -> -4(%eax, %edx, 4)

    fun munchStm(stm: TreeStm) {
        emit("# $stm")

        when (stm) {
            is TreeStm.Seq -> {
                munchStm(stm.lhs)
                munchStm(stm.rhs)
            }

            is TreeStm.Labeled ->
                emit(Instr.Lbl("${stm.label.name}:", stm.label))

            // data movement
            is Move ->
                munchMove(stm.target, stm.source)

            is TreeStm.Branch -> when (stm) {
                is Jump -> munchJump(stm.exp, stm.labels)
                is CJump -> munchCJump(stm.relop, stm.lhs, stm.rhs, stm.trueLabel, stm.falseLabel)
            }

            is TreeStm.Exp ->
                if (stm.exp is Call) {
                    munchCall(stm.exp)
                } else {
                    munchExp(stm.exp)
                }
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
                    exp.lhs is Const    -> emitBinOp("addq", munchExp(exp.rhs), exp.lhs.value)
                    exp.rhs is Const    ->
                        if (exp.rhs.value == 1) {
                            val r = munchExp(exp.lhs)
                            emit("incq 's0", src=listOf(r), dst=r)
                            r
                        } else
                            emitBinOp("addq", munchExp(exp.lhs), exp.rhs.value)
                    else                -> emitBinOp("addq", munchExp(exp.lhs), munchExp(exp.rhs))
                }
                MINUS -> when {
                    exp.rhs is Const    -> emitBinOp("subq", munchExp(exp.lhs), exp.rhs.value)
                    else                -> emitBinOp("subq", munchExp(exp.lhs), munchExp(exp.rhs))
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

        /*

          (* and *)

          | munchExp (T.BINOP (T.AND, e1, T.CONST n)) =
            result(fn r => emit(A.OPER{
                               assem="andi 'd0, 's0, " ^ int2str n,
                               src=[munchExp e1],
                               dst=[r],
                               jump=NONE}))

          | munchExp (T.BINOP (T.AND, T.CONST n, e1)) =
            result(fn r => emit(A.OPER{
                               assem="andi 'd0, 's0, " ^ int2str n,
                               src=[munchExp e1],
                               dst=[r],
                               jump=NONE}))

          | munchExp (T.BINOP (T.AND, e1, e2)) =
            result(fn r => emit(A.OPER{
                               assem="and 'd0, 's0, 's1",
                               src=[munchExp e1],
                               dst=[r],
                               jump=NONE}))

          (* or *)

          | munchExp (T.BINOP (T.OR, e1, T.CONST n)) =
            result(fn r => emit(A.OPER{
                               assem="ori 'd0, 's0, " ^ int2str n,
                               src=[munchExp e1],dst=[r],jump=NONE}))

          | munchExp (T.BINOP (T.OR, T.CONST n, e1)) =
            result(fn r => emit(A.OPER{
                               assem="ori 'd0, 's0, " ^ int2str n,
                               src=[munchExp e1],dst=[r],jump=NONE}))

          | munchExp (T.BINOP (T.OR, e1, e2)) =
            result(fn r => emit(A.OPER{
                               assem="or 'd0, 's0, 's1",
                               src=[munchExp e1],dst=[r],jump=NONE}))

          (* shift *)

          | munchExp (T.BINOP (T.LSHIFT, e, T.CONST n)) =
            result (fn r => emit (A.OPER {
                                  assem="sll 'd0, 's0, " ^ int2str n,
                                  src=[munchExp e],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.LSHIFT, e1, e2)) =
            result (fn r => emit (A.OPER {
                                  assem="sllv 'd0, 's0, 's1",
                                  src=[munchExp e1, munchExp e2],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.RSHIFT, e, T.CONST n)) =
            result (fn r => emit (A.OPER {
                                  assem="srl 'd0, 's0, " ^ int2str n,
                                  src=[munchExp e],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.RSHIFT, e1, e2)) =
            result (fn r => emit (A.OPER {
                                  assem="srlv 'd0, 's0, 's1",
                                  src=[munchExp e1, munchExp e2],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.ARSHIFT, e, T.CONST n)) =
            result (fn r => emit (A.OPER {
                                  assem="sra 'd0, 's0, " ^ int2str n,
                                  src=[munchExp e],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.ARSHIFT, e1, e2)) =
            result (fn r => emit (A.OPER {
                                  assem="srav 'd0, 's0, 's1",
                                  src=[munchExp e1, munchExp e2],
                                  dst=[r],
                                  jump=NONE}))

        (* generate code to move all arguments to their correct positions.
         * In SPIM MIPS, we use a0-a3 to store first four parameters, and
         * others go to frame. The result of this function is a list of
         * temporaries that are to be passed to the machine's CALL function. *)
        and munchArgs (_, nil) = nil
          | munchArgs (i, exp :: rest) =
            let val len = List.length Frame.argregs in
              if i < len then
                let val dst = List.nth(Frame.argregs,i)
                    val src = munchExp(exp) in
                  munchStm(T.MOVE(T.TEMP dst,T.TEMP src));
                  dst :: munchArgs(i+1,rest)
                end
              else raise TooManyArgs("too many arguments!") (* TODO: spilling *)
            end

         */
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

    private fun munchStore(addr: TreeExp, src: TreeExp) = when {
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
    // constant binary operations
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
        if (target is Name) {
            emit("jmp 'j0", jump = listOf(target.label))
        } else {
            emit("jmp 's0", src = listOf(munchExp(target)), jump = labels)
        }
    }

    private fun munchCJump(relop: RelOp, lhs: TreeExp, rhs: TreeExp, trueLabel: Label, falseLabel: Label) {
        // TODO: add special cases for comparison to 0
        var op = relop
//        if (lhs is Const) {
//            emit(Oper("cmpq \$${lhs.value}, 's0", src = listOf(munchExp(rhs))))
//            op = relop.commute()
//        } else if (rhs is Const) {
//            emit(Oper("cmpq \$${rhs.value}, 's0", src = listOf(munchExp(lhs))))
        if (rhs is Const) {
            emit("cmpq \$${rhs.value}, 's0", src = listOf(munchExp(lhs)))
        } else {
            emit("cmpq 's0, 's1", src = listOf(munchExp(rhs), munchExp(lhs)))
//            op = relop.commute()
        }

        val inst = when (op) {
            EQ -> "jne"
            NE -> "je"
            GE -> "jl"
            GT -> "jle"
            LE -> "jg"
            LT -> "jge"
            else    -> TODO("cjump $relop $lhs $rhs $trueLabel $falseLabel")
        }

        emit("$inst 'j1", jump = listOf(trueLabel, falseLabel))
    }
}
