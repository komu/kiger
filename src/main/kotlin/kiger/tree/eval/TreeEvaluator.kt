package kiger.tree.eval

import kiger.canon.basicBlocks
import kiger.canon.linearize
import kiger.canon.traceSchedule
import kiger.frame.Fragment
import kiger.frame.FrameType
import kiger.frame.JouletteFrame
import kiger.parser.parseExpression
import kiger.temp.Label
import kiger.temp.Temp
import kiger.translate.Translator
import kiger.tree.BinaryOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm


/**
 * Evaluator for normalized trees. Useful for testing.
 */
class TreeEvaluator(fragments: List<Fragment>) {

    private val frameType: FrameType = JouletteFrame

    /** strings by their label */
    private val strings = fragments.asSequence().filterIsInstance<Fragment.Str>().map { Pair(it.label, it.value) }.toMap()

    private val code = fragments.asSequence().filterIsInstance<Fragment.Proc>().flatMap { proc ->
        sequenceOf(TreeStm.Labeled(proc.frame.name)) + proc.body.linearize().asSequence()
    }.toList()

    private val codeLabels: Map<Label, Int> = run {
        val labels = mutableMapOf<Label, Int>()
        code.forEachIndexed { i, stm ->
            if (stm is TreeStm.Labeled)
                labels[stm.label] = i
        }
        labels
    }

    fun evaluate(): Int {
        dumpCode()
        val state = EvalState()
        println(codeLabels)

        state.pc = codeLabels[Translator.mainLabel]!!

        while (state.pc < code.size) {
            val op = code[state.pc++]
            eval(op, state)
        }

        return state.getValue(frameType.RV)
    }

    private fun eval(op: TreeStm, state: EvalState) {
        println(op)
        when (op) {
            is TreeStm.Labeled -> {}
            is TreeStm.Move -> evalMove(op, state)
            else -> error("unsupported op $op")
        }
    }

    private fun evalMove(op: TreeStm.Move, state: EvalState) {
        val value = evalExp(op.source, state)
        when (op.target) {
            is TreeExp.Mem -> state.store(evalExp(op.target.exp, state), value)
            is TreeExp.Temporary -> state.setValue(op.target.temp, value)
            else -> error("unknown target: ${op.target}")
        }
    }

    private fun evalExp(exp: TreeExp, state: EvalState): Int = when (exp) {
        is TreeExp.Const        -> exp.value
        is TreeExp.Temporary    -> state.getValue(exp.temp)
        is TreeExp.BinOp        -> applyBinOp(exp.binop, evalExp(exp.lhs, state), evalExp(exp.rhs, state))
        is TreeExp.Mem          -> state.load(evalExp(exp, state))
        is TreeExp.Name         -> lookupName(exp.label)
        is TreeExp.Call         -> evalCall(exp, state)
        is TreeExp.ESeq         -> error("unexpected ESeq: $exp")
    }

    private fun applyBinOp(binop: BinaryOp, lhs: Int, rhs: Int): Int = when (binop) {
        BinaryOp.PLUS   -> lhs + rhs
        BinaryOp.MINUS  -> lhs - rhs
        BinaryOp.MUL    -> lhs * rhs
        BinaryOp.DIV    -> lhs / rhs
        BinaryOp.AND    -> lhs and rhs
        BinaryOp.OR     -> lhs or rhs
        BinaryOp.LSHIFT -> lhs shl rhs
        BinaryOp.RSHIFT -> lhs shl rhs
        BinaryOp.XOR    -> lhs xor rhs
    }

    private fun evalCall(call: TreeExp.Call, state: EvalState): Int {
        val f = evalExp(call.func, state)
        val args = call.args.map { evalExp(it, state) }

        return applyFunc(f, args, state)
    }

    private fun applyFunc(f: Int, args: List<Int>, state: EvalState): Int {
        TODO("apply")
    }

    private fun lookupName(label: Label): Int {
        // TODO: get strings to work as well
        return codeLabels[label]!!
    }

    @Suppress("unused")
    private fun dumpCode() {
        println(code.basicBlocks().traceSchedule().joinToString("\n"))
    }

    companion object {
        fun evaluate(code: String): Int {
            val exp = parseExpression(code)
            val fragments = Translator.transProg(exp)
            val evaluator = TreeEvaluator(fragments)
            return evaluator.evaluate()
        }
    }
}
class EvalState {
    var pc = 0
    private val regs = mutableMapOf<Temp, Int>()
    private val memory = mutableMapOf<Int, Int>()

    fun dump() {
        println("pc = $pc")
    }

    fun getValue(temp: Temp): Int = regs[temp] ?: 0

    fun setValue(temp: Temp, value: Int) {
        regs[temp] = value
    }

    fun load(address: Int): Int = memory[address] ?: 0

    fun store(address: Int, value: Int) {
        memory[address] = value
    }
}
