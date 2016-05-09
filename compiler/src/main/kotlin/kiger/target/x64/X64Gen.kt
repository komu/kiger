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
import kiger.utils.splitFirst

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

    /**
     * Calling a function will trash a particular set of registers:
     *  - argument registers: they are defined to pass parameters;
     *  - caller-saves: they may be redefined inside the call;
     *  - RV: it will be overwritten for function return.
     *  - RA: it will be overwritten for function return.
     */
    // TODO: do we need to save args?
    val callDefs = X64Frame.callerSaves + X64Frame.argumentRegisters + X64Frame.RV

    private fun emit(instr: Instr) {
        instructions += instr
    }

    private inline fun emitResult(gen: (Temp) -> Instr): Temp {
        val t = Temp.gen()
        emit(gen(t))
        return t
    }

    // TODO: use lea where applicable [eax + edx*4 -4] -> -4(%eax, %edx, 4)

    fun munchStm(stm: TreeStm) {
        emit(Instr.Oper("# $stm"))

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
        if (exp.func is Name) {
            emit(Oper("callq ${exp.func.label}", src = munchArgs(0, exp.args), dst = callDefs))
        } else {
            emit(Oper("callq 's0", src = cons(munchExp(exp.func), munchArgs(0, exp.args)), dst = callDefs))
        }

        return frameType.RV
    }

    /**
     * generate code to move all arguments to their correct positions.
     * We use a0-a3 to store first four parameters, and
     * others go to frame. The result of this function is a list of
     * temporaries that are to be passed to the machine's CALL function.
     */
    private fun munchArgs(i: Int, args: List<TreeExp>): List<Temp> {
        if (args.isEmpty()) return emptyList()

        val (exp, rest) = args.splitFirst()

        val argumentRegisters = frameType.argumentRegisters
        if (i < argumentRegisters.size) {
            val dst = argumentRegisters[i]
            val src = munchExp(exp)
            munchStm(Move(Temporary(dst), Temporary(src)))

            return cons(dst, munchArgs(i + 1, rest))
        } else {
            throw TooManyArgsException("support only ${argumentRegisters.size} arguments, but got more")
        }
    }

    private fun munchExp(exp: TreeExp): Temp {
        return when (exp) {
            is Temporary -> exp.temp
            is Const ->
                if (exp.value == 0)
                    emitResult { r -> Oper("xorq 'd0, 'd0", dst = listOf(r)) }
                else
                    emitResult { r -> Oper("movq \$${exp.value}, 'd0", dst = listOf(r)) }
            is Name -> emitResult { r -> Oper("leaq ${exp.label}(%rip), 'd0", dst = listOf(r)) }
            is Call -> munchCall(exp)
            is Mem -> munchLoad(exp.exp)
            is BinOp -> when (exp.binop) {
                PLUS -> when {
//                    exp.rhs is Const -> emitResult { r -> Oper("addi 'd0, 's0, ${exp.rhs.value}", dst = listOf(r), src = listOf(munchExp(exp.lhs))) }
//                    exp.lhs is Const -> emitResult { r -> Oper("addi 'd0, 's0, ${exp.lhs.value}", dst = listOf(r), src = listOf(munchExp(exp.rhs))) }
                    else             -> emitResult { r -> Oper("addq 's0, 'd0", dst = listOf(r), src = listOf(munchExp(exp.lhs), munchExpTo(r, exp.rhs))) }
                }
                MINUS -> when {
                    exp.rhs is Const -> emitResult { r -> Oper("subq \$${exp.rhs.value}, 'd0", dst = listOf(r), src = listOf(munchExpTo(r, exp.lhs))) }
                    else             -> emitResult { r -> Oper("subq 's0, 'd0", dst = listOf(r), src = listOf(munchExp(exp.rhs), munchExpTo(r, exp.lhs))) }
                }
                DIV -> {
                    emit(Oper("divq 's1", src = listOf(munchExpTo(X64Frame.rax, exp.lhs), munchExp(exp.rhs)), dst = listOf(X64Frame.rax, X64Frame.rdx)))
                    X64Frame.rax
                }
                MUL -> {
                    emit(Oper("mulq 's1", src = listOf(munchExpTo(X64Frame.rax, exp.lhs), munchExp(exp.rhs)), dst = listOf(X64Frame.rax, X64Frame.rdx)))
                    X64Frame.rax
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

    private fun munchExpTo(t: Temp, exp: TreeExp): Temp {
        val r = munchExp(exp)
        emit(Instr.Move("movq 's0, 'd0", src=r, dst=t))
        return t
    }

    private fun munchMove(dst: TreeExp, src: TreeExp) {
        when {
            dst is Mem -> munchStore(dst.exp, src)
            dst is Temporary && src is Const && src.value == 0->
                emit(Oper("xorq 'd0, 'd0", dst = listOf(dst.temp)))
            dst is Temporary && src is Const ->
                emit(Oper("movq \$${src.value}, 'd0", dst = listOf(dst.temp)))
            dst is Temporary ->
                emit(Instr.Move("movq 's0, 'd0", src = munchExp(src), dst = dst.temp))
            else ->
                TODO("move: $dst $src")
        }
    }

    private fun munchStore(addr: TreeExp, src: TreeExp) = when {
        src is Const && addr is BinOp && addr.binop == PLUS && addr.rhs is Const ->
            emit(Oper("movq \$${src.value}, ${addr.rhs.value}('s0)", src = listOf(munchExp(addr.lhs))))
        src is Const && addr is BinOp && addr.binop == PLUS && addr.lhs is Const ->
            emit(Oper("movq \$${src.value}, ${addr.lhs.value}('s0)", src = listOf(munchExp(addr.rhs))))
        addr is BinOp && addr.binop == PLUS && addr.rhs is Const ->
            emit(Oper("movq 's1, ${addr.rhs.value}('s0)", src = listOf(munchExp(addr.lhs), munchExp(src))))
        addr is BinOp && addr.binop == PLUS && addr.lhs is Const ->
            emit(Oper("movq 's1, ${addr.lhs.value}('s0)", src = listOf(munchExp(addr.rhs), munchExp(src))))
        src is Const ->
            emit(Oper("movq \$${src.value}, ('s0)", src = listOf(munchExp(addr))))
        else ->
            emit(Oper("movq 's1, ('s0)", src = listOf(munchExp(addr), munchExp(src))))
    }

    private fun munchLoad(addr: TreeExp): Temp = when {
    // constant binary operations
        addr is BinOp && addr.binop == PLUS && addr.rhs is Const ->
            emitResult { r -> Oper("movq ${addr.rhs.value}('s0), 'd0", src = listOf(munchExp(addr.lhs)), dst = listOf(r)) }
        addr is BinOp && addr.binop == PLUS && addr.lhs is Const ->
            emitResult { r -> Oper("movq ${addr.lhs.value}('so), 'd0", src = listOf(munchExp(addr.rhs)), dst = listOf(r)) }
        addr is BinOp && addr.binop == PLUS && addr.rhs is BinOp && addr.rhs.binop == MUL && addr.rhs.rhs is Const ->
            emitResult { r -> Oper("movq ('s0, 's1, ${addr.rhs.rhs.value}), 'd0", src = listOf(munchExp(addr.lhs), munchExp(addr.rhs.lhs)), dst = listOf(r)) }
//        // TODO: add similar movqs for other constant loads
//        addr is BinOp && addr.binop == PLUS ->
//            emitResult { r -> Oper("movq ('s0, 's1), 'd0", src = listOf(munchExp(addr.lhs), munchExp(addr.rhs)), dst = listOf(r)) }
//        addr is BinOp && addr.binop == MINUS && addr.rhs is Const ->
//            emitResult { r -> Oper("movq ${-addr.rhs.value}('s0), 'd0", src = listOf(munchExp(addr.lhs)), dst = listOf(r)) }
        addr is Const ->
            emitResult { r -> Oper("movq (${addr.value}), 'd0", dst = listOf(r)) }
        else ->
            emitResult { r -> Oper("movq ('s0), 'd0", src = listOf(munchExp(addr)), dst = listOf(r)) }
    }


    private fun munchJump(target: TreeExp, labels: List<Label>) {
        if (target is Name) {
            emit(Oper("jmp 'j0", jump = listOf(target.label)))
        } else {
            emit(Oper("jmp 's0", src = listOf(munchExp(target)), jump = labels))
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
            emit(Oper("cmpq \$${rhs.value}, 's0", src = listOf(munchExp(lhs))))
        } else {
            emit(Oper("cmpq 's0, 's1", src = listOf(munchExp(rhs), munchExp(lhs))))
//            op = relop.commute()
        }

        when (op) {
            EQ -> emit(Oper("jne 'j1", jump = listOf(trueLabel, falseLabel)))
            NE -> emit(Oper("je 'j1", jump = listOf(trueLabel, falseLabel)))
            GE -> emit(Oper("jl 'j1", jump = listOf(trueLabel, falseLabel)))
            GT -> emit(Oper("jle 'j1", jump = listOf(trueLabel, falseLabel)))
            LE -> emit(Oper("jg 'j1", jump = listOf(trueLabel, falseLabel)))
            LT -> emit(Oper("jge 'j1", jump = listOf(trueLabel, falseLabel)))
            else    -> TODO("cjump $relop $lhs $rhs $trueLabel $falseLabel")
        }
    }

}
