package kiger.codegen

import kiger.assem.Instr
import kiger.assem.Instr.Oper
import kiger.canon.basicBlocks
import kiger.canon.linearize
import kiger.canon.traceSchedule
import kiger.frame.Frame
import kiger.frame.MipsFrame
import kiger.temp.Label
import kiger.temp.Temp
import kiger.tree.BinaryOp.*
import kiger.tree.RelOp
import kiger.tree.RelOp.LT
import kiger.tree.TreeExp
import kiger.tree.TreeExp.*
import kiger.tree.TreeStm
import kiger.tree.TreeStm.Branch.CJump
import kiger.tree.TreeStm.Branch.Jump
import kiger.tree.TreeStm.Move
import kiger.utils.cons
import kiger.utils.splitFirst

object MipsGen : CodeGen {
    override val frameType = MipsFrame

    override fun codeGen(frame: Frame, stm: TreeStm): List<Instr> {
        val generator = MipsCodeGenerator(frame as MipsFrame)

        val trace = stm.linearize().basicBlocks().traceSchedule()
        for (st in trace)
            generator.munchStm(st)

        return generator.instructions
    }
}

private class MipsCodeGenerator(val frame: MipsFrame) {

    val frameType = MipsGen.frameType
    val instructions = mutableListOf<Instr>()

    /**
     * Calling a function will trash a particular set of registers:
     *  - argregs: they are defined to pass parameters;
     *  - callersaves: they may be redefined inside the call;
     *  - RV: it will be overwritten for function return.
     */
    val callDefs = listOf(MipsFrame.RV, MipsFrame.RA) + MipsFrame.argumentRegisters

    private fun emit(instr: Instr) {
        instructions += instr
    }

    private inline fun emitResult(gen: (Temp) -> Instr): Temp {
        val t = Temp()
        emit(gen(t))
        return t
    }

    private inline fun result(gen: (Temp) -> Unit): Temp {
        val t = Temp()
        gen(t)
        return t
    }

    fun munchStm(stm: TreeStm): Unit {
        when (stm) {
            is TreeStm.Seq -> {
                munchStm(stm.lhs)
                munchStm(stm.rhs)
            }

            is TreeStm.Labeled ->
                emit(Instr.Lbl("${stm.label.name}:", stm.label))

            // data movement
            is TreeStm.Move ->
                munchMove(stm.target, stm.source)

            is TreeStm.Branch -> when (stm) {
                is Jump  -> munchJump(stm.exp, stm.labels)
                is CJump -> munchCJump(stm.relop, stm.lhs, stm.rhs, stm.trueLabel, stm.falseLabel)
            }

            is TreeStm.Exp ->
                if (stm.exp is TreeExp.Call) {
                    munchStmtCall(stm.exp)
                } else {
                    munchExp(stm.exp)
                }
        }
    }

    private fun munchStmtCall(exp: TreeExp.Call) {
        val pairs = frameType.calleeSaves.map { r -> Pair(Temp(), r) }

        fun fetch(a: Temp, r: Temp) = Move(Temporary(r), Temporary(a))
        fun store(a: Temp, r: Temp) = Move(Temporary(a), Temporary(r))

        for ((a, r) in pairs)
            munchStm(store(a, r))

        emit(Oper("jalr `s0",
            src = cons(munchExp(exp.func), munchArgs(0, exp.args)),
            dst = callDefs))

        for ((a, r) in pairs.asReversed())
            munchStm(fetch(a, r))
    }

    private fun munchExpCall(exp: TreeExp.Call): Temp {
        val pairs = frameType.calleeSaves.map { r -> Pair(Temp(), r) }

        fun fetch(a: Temp, r: Temp) = Move(Temporary(r), Temporary(a))
        fun store(a: Temp, r: Temp) = Move(Temporary(a), Temporary(r))

        for ((a, r) in pairs)
            munchStm(store(a, r))

        emitResult { r -> Oper("jalr `s0", src = cons(munchExp(exp.func), munchArgs(0, exp.args)), dst = callDefs) }

        for ((a, r) in pairs.asReversed())
            munchStm(fetch(a, r))

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

            return cons(dst, munchArgs(i+1, rest))
        } else {
            throw TooManyArgsException("support only ${argumentRegisters.size} arguments, but got more")
        }
    }

    private fun munchExp(exp: TreeExp): Temp {
        return when (exp) {
            is Temporary    -> exp.temp
            is Const        -> emitResult { r -> Oper("li `d0, ${exp.value}", dst = listOf(r)) }
            is Name         -> emitResult { r -> Oper("la `d0, ${exp.label}", dst = listOf(r)) }
            is Call         -> munchExpCall(exp)
            is Mem          -> when {
                // constant binary operations
                exp.exp is BinOp && exp.exp.binop == PLUS && exp.exp.rhs is Const ->
                    emitResult { r -> Oper("lw `d0, ${exp.exp.rhs.value}(`s0)", src = listOf(munchExp(exp.exp.lhs)), dst = listOf(r)) }
                exp.exp is BinOp && exp.exp.binop == PLUS && exp.exp.lhs is Const ->
                    emitResult { r -> Oper("lw `d0, ${exp.exp.lhs.value}(`so)", src = listOf(munchExp(exp.exp.rhs)), dst = listOf(r)) }
                exp.exp is BinOp && exp.exp.binop == MINUS && exp.exp.rhs is Const ->
                    emitResult { r -> Oper("lw `d0, ${-exp.exp.rhs.value}(`s0)", src = listOf(munchExp(exp.exp.lhs)), dst = listOf(r)) }
                exp.exp is Const ->
                    emitResult { r -> Oper("lw `d0, ${exp.exp.value}(\$zero)", dst = listOf(r)) }
                else ->
                    emitResult { r -> Oper("lw `d0, 0(`s0)", src = listOf(munchExp(exp.exp)), dst = listOf(r)) }
            }
            is BinOp -> when (exp.binop) {
                PLUS -> when {
                    exp.rhs is Const -> emitResult { r -> Oper("addi `d0, `s0, ${exp.rhs.value}", dst = listOf(r), src = listOf(munchExp(exp.lhs))) }
                    exp.lhs is Const -> emitResult { r -> Oper("addi `d0, `s0, ${exp.lhs.value}", dst = listOf(r), src = listOf(munchExp(exp.rhs))) }
                    else             -> emitResult { r -> Oper("add `d0, `s0, `s1", dst = listOf(r), src = listOf(munchExp(exp.lhs), munchExp(exp.rhs))) }
                }
                MINUS -> when {
                    exp.rhs is Const -> emitResult { r -> Oper("addi `d0, `s0, ${-exp.rhs.value}", dst = listOf(r), src = listOf(munchExp(exp.lhs))) }
                    else             -> emitResult { r -> Oper("sub `d0, `s0, `s1", src = listOf(munchExp(exp.lhs), munchExp(exp.rhs)), dst = listOf(r)) }
                }
                DIV -> emitResult { r -> Oper("div `d0, `s0, `s1", src = listOf(munchExp(exp.lhs), munchExp(exp.rhs)), dst = listOf(r)) }
                MUL -> emitResult { r -> Oper("mul `d0, `s0, `s1", src = listOf(munchExp(exp.lhs), munchExp(exp.rhs)), dst = listOf(r)) }
                else -> TODO("unsupported binop ${exp.binop}")
            }
            else ->
                TODO("$exp")
        }

        /*

          (* and *)

          | munchExp (T.BINOP (T.AND, e1, T.CONST n)) =
            result(fn r => emit(A.OPER{
                               assem="andi `d0, `s0, " ^ int2str n,
                               src=[munchExp e1],
                               dst=[r],
                               jump=NONE}))

          | munchExp (T.BINOP (T.AND, T.CONST n, e1)) =
            result(fn r => emit(A.OPER{
                               assem="andi `d0, `s0, " ^ int2str n,
                               src=[munchExp e1],
                               dst=[r],
                               jump=NONE}))

          | munchExp (T.BINOP (T.AND, e1, e2)) =
            result(fn r => emit(A.OPER{
                               assem="and `d0, `s0, `s1",
                               src=[munchExp e1],
                               dst=[r],
                               jump=NONE}))

          (* or *)

          | munchExp (T.BINOP (T.OR, e1, T.CONST n)) =
            result(fn r => emit(A.OPER{
                               assem="ori `d0, `s0, " ^ int2str n,
                               src=[munchExp e1],dst=[r],jump=NONE}))

          | munchExp (T.BINOP (T.OR, T.CONST n, e1)) =
            result(fn r => emit(A.OPER{
                               assem="ori `d0, `s0, " ^ int2str n,
                               src=[munchExp e1],dst=[r],jump=NONE}))

          | munchExp (T.BINOP (T.OR, e1, e2)) =
            result(fn r => emit(A.OPER{
                               assem="or `d0, `s0, `s1",
                               src=[munchExp e1],dst=[r],jump=NONE}))

          (* shift *)

          | munchExp (T.BINOP (T.LSHIFT, e, T.CONST n)) =
            result (fn r => emit (A.OPER {
                                  assem="sll `d0, `s0, " ^ int2str n,
                                  src=[munchExp e],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.LSHIFT, e1, e2)) =
            result (fn r => emit (A.OPER {
                                  assem="sllv `d0, `s0, `s1",
                                  src=[munchExp e1, munchExp e2],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.RSHIFT, e, T.CONST n)) =
            result (fn r => emit (A.OPER {
                                  assem="srl `d0, `s0, " ^ int2str n,
                                  src=[munchExp e],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.RSHIFT, e1, e2)) =
            result (fn r => emit (A.OPER {
                                  assem="srlv `d0, `s0, `s1",
                                  src=[munchExp e1, munchExp e2],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.ARSHIFT, e, T.CONST n)) =
            result (fn r => emit (A.OPER {
                                  assem="sra `d0, `s0, " ^ int2str n,
                                  src=[munchExp e],
                                  dst=[r],
                                  jump=NONE}))

          | munchExp (T.BINOP (T.ARSHIFT, e1, e2)) =
            result (fn r => emit (A.OPER {
                                  assem="srav `d0, `s0, `s1",
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

    private fun munchMove(dst: TreeExp, src: TreeExp) {
        when {
            dst is Mem && dst.exp is BinOp && dst.exp.binop == PLUS && dst.exp.rhs is Const ->
                emit(Oper("sw `s1, ${dst.exp.rhs.value}(`s0)", src = listOf(munchExp(dst.exp.lhs), munchExp(src))))
            dst is Mem && dst.exp is BinOp && dst.exp.binop == PLUS && dst.exp.lhs is Const ->
                emit(Oper("sw `s1, ${dst.exp.lhs.value}(`s0)", src = listOf(munchExp(dst.exp.rhs), munchExp(src))))
            dst is Mem && src is Mem ->
                emit(Oper("MOVE M[`s0] <- M[`s1]", src = listOf(munchExp(dst.exp), munchExp(src.exp))))
            dst is Mem && dst.exp is Const ->
                emit(Oper("STORE M[${dst.exp.value}] <- `s0", src = listOf(munchExp(src))))
            dst is Temporary ->
                emit(Oper("move `d0, `s0", src = listOf(munchExp(src)), dst = listOf(dst.temp)))
            else ->
                TODO("move: $dst $src")
        }

        // 1 store to memory (sw)
        /*
                  (* 1, store to memory (sw) *)

          (* e1+i <= e2 *)
          | munchStm (T.MOVE(T.MEM(T.BINOP(T.PLUS, e1, T.CONST i)), e2)) =
            emit(A.OPER{assem="sw `s0, " ^ int2str i ^ "(`s1)",
                        src=[munchExp e2, munchExp e1],
                        dst=[],jump=NONE})

          | munchStm (T.MOVE(T.MEM(T.BINOP(T.PLUS, T.CONST i, e1)), e2)) =
            emit(A.OPER{assem="sw `s0, " ^ int2str i ^ "(`s1)",
                        src=[munchExp e2, munchExp e1],
                        dst=[],jump=NONE})

          (* e1-i <= e2 *)
          | munchStm (T.MOVE(T.MEM(T.BINOP(T.MINUS, e1, T.CONST i)), e2)) =
            emit(A.OPER{assem="sw `s0, " ^ int2str (~i) ^ "(`s1)",
                        src=[munchExp e2, munchExp e1],
                        dst=[],jump=NONE})

          | munchStm (T.MOVE(T.MEM(T.BINOP(T.MINUS, T.CONST i, e1)), e2)) =
            emit(A.OPER{assem="sw `s0, " ^ int2str (~i) ^ "(`s1)",
                        src=[munchExp e2, munchExp e1],
                        dst=[],jump=NONE})

          (* i <= e2 *)
          (* | munchStm (T.MOVE(T.MEM(T.CONST i), e2)) = *)
          (*   emit(A.OPER{assem="sw `s0, " ^ int2str i ^ "($zero)", *)
          (*               src=[munchExp e2],dst=[],jump=NONE}) *)

          | munchStm (T.MOVE(T.MEM(e1), e2)) =
            emit(A.OPER{assem="sw `s0, 0(`s1)",
                        src=[munchExp e2, munchExp e1],
                        dst=[],jump=NONE})

          (* 2, load to register (lw) *)

          | munchStm (T.MOVE((T.TEMP i, T.CONST n))) =
            emit(A.OPER{assem="li `d0, " ^ int2str n,
                        src=[],dst=[i],jump=NONE})

          | munchStm (T.MOVE(T.TEMP i,
                             T.MEM(T.BINOP(T.PLUS, e1, T.CONST n)))) =
            emit(A.OPER{assem="lw `d0, " ^ int2str n ^ "(`s0)",
                        src=[munchExp e1],dst=[i],jump=NONE})

          | munchStm (T.MOVE(T.TEMP i,
                             T.MEM(T.BINOP(T.PLUS, T.CONST n, e1)))) =
            emit(A.OPER{assem="lw `d0, " ^ int2str n ^ "(`s0)",
                        src=[munchExp e1],dst=[i],jump=NONE})

          | munchStm (T.MOVE(T.TEMP i,
                             T.MEM(T.BINOP(T.MINUS, e1, T.CONST n)))) =
            emit(A.OPER{assem="lw `d0, " ^ int2str (~n) ^ "(`s0)",
                        src=[munchExp e1],dst=[i],jump=NONE})

          | munchStm (T.MOVE(T.TEMP i,
                             T.MEM(T.BINOP(T.MINUS, T.CONST n, e1)))) =
            emit(A.OPER{assem="lw `d0, " ^ int2str (~n) ^ "(`s0)",
                        src=[munchExp e1],dst=[i],jump=NONE})

          (* 3, move from register to register *)
          | munchStm (T.MOVE((T.TEMP i, e2))) =
            emit(A.MOVE{assem="move `d0, `s0",src=munchExp e2,dst=i})

         */
    }

    private fun munchJump(target: TreeExp, labels: List<Label>) {
        if (target is Name) {
            emit(Oper("j `j0", jump=listOf(target.label)))
        } else {
            emit(Oper("jr `s0", src=listOf(munchExp(target)), jump = labels))
        }
    }

    private fun munchCJump(relop: RelOp, lhs: TreeExp, rhs: TreeExp, trueLabel: Label, falseLabel: Label) {
        // TODO: add special cases for comparison to 0
        when (relop) {
            LT      -> emit(Oper("BGEZ `s0, `s1, `j0", src=listOf(munchExp(lhs), munchExp(rhs)), jump=listOf(falseLabel)))
            else    -> TODO("cjump $relop $lhs $rhs $trueLabel $falseLabel")
        }

        /*

          (* more general cases *)

          | munchStm (T.CJUMP(T.GE, e1, e2, l1, l2)) =
            emit(A.OPER{assem="bge `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

          | munchStm (T.CJUMP(T.UGE, e1, e2, l1, l2)) =
            emit(A.OPER{assem="bgeu `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

          | munchStm (T.CJUMP(T.GT, e1, e2, l1, l2)) =
            emit(A.OPER{assem="bgt `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

          | munchStm (T.CJUMP(T.UGT, e1, e2, l1, l2)) =
            emit(A.OPER{assem="bgtu `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

          | munchStm (T.CJUMP(T.LT, e1, e2, l1, l2)) =
            emit(A.OPER{assem="blt `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

          | munchStm (T.CJUMP(T.ULT, e1, e2, l1, l2)) =
            emit(A.OPER{assem="bltu `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

          | munchStm (T.CJUMP(T.LE, e1, e2, l1, l2)) =
            emit(A.OPER{assem="ble `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

          | munchStm (T.CJUMP(T.ULE, e1, e2, l1, l2)) =
            emit(A.OPER{assem="bleu `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

          | munchStm (T.CJUMP(T.EQ, e1, e2, l1, l2)) =
            emit(A.OPER{assem="beq `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

          | munchStm (T.CJUMP(T.NE, e1, e2, l1, l2)) =
            emit(A.OPER{assem="bne `s0, `s1, `j0\nb `j1",
                        dst=[],src=[munchExp e1, munchExp e2],
                        jump=SOME [l1,l2]})

         */
    }

}

class TooManyArgsException(message: String): RuntimeException(message)