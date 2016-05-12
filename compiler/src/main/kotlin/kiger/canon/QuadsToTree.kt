package kiger.canon

import kiger.ir.quad.QExp
import kiger.ir.quad.Quad
import kiger.tree.TreeExp
import kiger.tree.TreeExp.Temporary
import kiger.tree.TreeStm

/**
 * Converts quadruples to [TreeStm]s.
 *
 * The converted tree-statements have the same shallow structure as the original quads.
 * One can use [delinearize] to inline temporaries that are used only once to build a
 * tree that is better suited for instruction selection.
 *
 * @see linearize
 */
fun List<Quad>.toTree(): List<TreeStm> =
    map { it.toTree() }

/**
 * Convert [Quad] to corresponding [TreeStm].
 */
private fun Quad.toTree(): TreeStm = when (this) {
    is Quad.BinOp   -> TreeStm.Move(Temporary(target), TreeExp.BinOp(op, lhs.toTree(), rhs.toTree()))
    is Quad.Labeled -> TreeStm.Labeled(label)
    is Quad.Move    -> TreeStm.Move(Temporary(target), source.toTree())
    is Quad.Jump    -> TreeStm.Branch.Jump(target.toTree(), labels)
    is Quad.CJump   -> TreeStm.Branch.CJump(op, lhs.toTree(), rhs.toTree(), trueLabel, falseLabel)
    is Quad.Load    -> TreeStm.Move(Temporary(target), TreeExp.Mem(address.toTree()))
    is Quad.Store   -> TreeStm.Move(TreeExp.Mem(address.toTree()), value.toTree())
    is Quad.Call    -> {
        val call = TreeExp.Call(func.toTree(), args.map { it.toTree() })
        if (result != null)
            TreeStm.Move(Temporary(result), call)
        else
            TreeStm.Exp(call)
    }
}

/**
 * Convert [QExp] to corresponding [TreeExp].
 */
private fun QExp.toTree(): TreeExp = when (this) {
    is QExp.Temporary   -> TreeExp.Temporary(temp)
    is QExp.Const       -> TreeExp.Const(value)
    is QExp.Name        -> TreeExp.Name(name)
}
