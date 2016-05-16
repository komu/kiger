package kiger.canon

import kiger.ir.quad.QExp
import kiger.ir.quad.Quad
import kiger.ir.tree.TreeExp
import kiger.ir.tree.TreeExp.*
import kiger.ir.tree.TreeStm
import kiger.temp.Temp

/**
 * Linearizes arbitrary [TreeStm] into a list of quadruples.
 *
 * Quadruples are a simple form where every nested expression is replaced by
 * a temporary.
 *
 * @see linearize
 */
fun TreeStm.toQuads(): List<Quad> =
    simplify().linearize().map { it.toQuad() }

/**
 * Converts a linearized statement to quad.
 *
 * Note that this function is not total: every [TreeStm] is not valid input,
 * but only those statement that have a 1-1 correspondence to quads. Therefore
 * one should call [linearize] on the original tree before calling this
 */
private fun TreeStm.toQuad(): Quad = when (this) {
    is TreeStm.Seq      -> error("unexpected seq in linearized tree: $this")
    is TreeStm.Labeled  -> Quad.Labeled(label)
    is TreeStm.Branch   -> toQuad()
    is TreeStm.Move     -> when {
        target is Mem   -> Quad.Store(target.exp.toQuad(), source.toQuad())
        source is Mem   -> Quad.Load(target.toTemp(), source.exp.toQuad())
        source is BinOp -> Quad.BinOp(source.binop, target.toTemp(), source.lhs.toQuad(), source.rhs.toQuad())
        source is Call  -> Quad.Call(source.func.toQuad(), source.args.map { it.toQuad() }, target.toTemp())
        else            -> Quad.Move(target.toTemp(), source.toQuad())
    }
    is TreeStm.Exp      ->
        if (exp is Call)
            Quad.Call(exp.func.toQuad(), exp.args.map { it.toQuad() }, null)
        else
            Quad.Move(Temp.gen(), exp.toQuad())
}
private fun TreeExp.toTemp(): Temp =
    if (this is Temporary)
        temp;
    else
        error("unexpected expression in linearized tree: $this is not a temporary")

private fun TreeExp.toQuad(): QExp = when (this) {
    is TreeExp.Temporary    -> QExp.Temporary(temp)
    is TreeExp.Name         -> QExp.Name(name)
    is TreeExp.Const        -> QExp.Const(value)
    else                    -> error("unexpected expression in linearized tree. expected temp/name/const, but got $this")
}

private fun TreeStm.Branch.toQuad(): Quad = when (this) {
    is TreeStm.Branch.Jump  -> Quad.Jump(target.toQuad(), labels)
    is TreeStm.Branch.CJump -> Quad.CJump(op, lhs.toQuad(), rhs.toQuad(), trueLabel, falseLabel)
}

/**
 * Convert all complex nested expressions with (ESeq (Move temp exp) temp). These
 * can then be lifted up the tree with [linearize].
 */
private fun TreeStm.simplify(): TreeStm = when (this) {
    is TreeStm.Seq      -> TreeStm.Seq(lhs.simplify(), rhs.simplify())
    is TreeStm.Labeled  -> this
    is TreeStm.Branch   -> simplifyBranch()
    is TreeStm.Move     -> simplifyMove()
    is TreeStm.Exp      -> TreeStm.Exp(exp.simplifyExp())
}

private fun TreeStm.Move.simplifyMove(): TreeStm.Move = when (target) {
    is Mem       -> TreeStm.Move(Mem(target.exp.simpleExp()), source.simpleExp())
    is Temporary -> TreeStm.Move(target, source.simplifyExp())
    else         -> error("invalid target for move: $target")
}

private fun TreeStm.Branch.simplifyBranch(): TreeStm = when (this) {
    is TreeStm.Branch.Jump  -> TreeStm.Branch.Jump(target.simpleExp(), labels)
    is TreeStm.Branch.CJump -> TreeStm.Branch.CJump(op, lhs.simpleExp(), rhs.simpleExp(), trueLabel, falseLabel)
}

private fun TreeExp.simplifyExp(): TreeExp = when (this) {
    is BinOp     -> BinOp(binop, lhs.simpleExp(), rhs.simpleExp())
    is Mem       -> Mem(exp.simpleExp())
    is Temporary -> this
    is ESeq      -> ESeq(stm.simplify(), exp.simpleExp())
    is Name      -> this
    is Const     -> this
    is Call      -> Call(func.simpleExp(), args.map { it.simpleExp() })
}

/**
 * Returns given expression as "simple" expression (temporary, name or const).
 *
 * If the expression is already simple, returns it as it is. Otherwise, assign
 * the expression to temporary and return [ESeq] containing the assignment and
 * the temporary as the value of the expression. The assignment will be lifted
 * from [ESeq] later by [linearize].
 */
private fun TreeExp.simpleExp(): TreeExp {
    val simplified = simplifyExp()
    return if (simplified is Temporary || simplified is Name || simplified is Const) {
        simplified
    } else {
        val temp = Temporary(Temp.gen())
        ESeq(kiger.ir.tree.TreeStm.Move(temp, simplified), temp)
    }
}
