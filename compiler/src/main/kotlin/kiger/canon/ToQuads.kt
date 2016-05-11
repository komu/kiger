package kiger.canon

import kiger.temp.Temp
import kiger.tree.TreeExp
import kiger.tree.TreeExp.*
import kiger.tree.TreeStm

/**
 * Linearizes arbitrary [TreeStm] into a list of quadruples.
 *
 * Quadruples are a simple form where every nested expression is replaced by
 * a temporary.
 *
 * @see linearize
 */
fun TreeStm.toQuads(): List<TreeStm> =
    convertNestedExpsToTemps().linearize()

private fun TreeStm.convertNestedExpsToTemps(): TreeStm = mapExps { it.convertNestedExpsToTemps() }

/**
 * Convert all complex nested expressions with (ESeq (Move temp exp) temp). These
 * can then be lifted up the tree with [linearize].
 */
private fun TreeExp.convertNestedExpsToTemps(): TreeExp = when (this) {
    is BinOp     -> BinOp(binop, lhs.convertNestedExpsToTemps().tempForComplex(), rhs.convertNestedExpsToTemps().tempForComplex())
    is Mem       -> Mem(exp.convertNestedExpsToTemps().tempForComplex())
    is Temporary -> this
    is ESeq      -> ESeq(stm.convertNestedExpsToTemps(), exp.convertNestedExpsToTemps().tempForComplex())
    is Name      -> this
    is Const     -> this
    is Call      -> Call(func.convertNestedExpsToTemps().tempForComplex(), args.map { it.convertNestedExpsToTemps().tempForComplex() })
}

private fun TreeExp.tempForComplex(): TreeExp = when (this) {
    is BinOp,
    is Mem,
    is Call -> {
        val temp = Temporary(Temp.gen())
        ESeq(TreeStm.Move(temp, this), temp)
    }
    is ESeq,
    is Temporary,
    is Name,
    is Const -> this
}
