package kiger.translate

import kiger.temp.Label
import kiger.temp.Temp
import kiger.tree.RelOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm

sealed class TrExp {

    abstract fun asEx(): TreeExp
    abstract fun asNx(): TreeStm
    abstract fun asCx(): (Label, Label) -> TreeStm

    class Ex(val exp: TreeExp) : TrExp() {
        override fun equals(other: Any?) = other is Ex && exp == other.exp
        override fun hashCode() = exp.hashCode()
        override fun toString() = "Ex[$exp]"
        override fun asEx() = exp
        override fun asNx() = TreeStm.Exp(exp)
        override fun asCx(): (Label, Label) -> TreeStm = when {
            exp is TreeExp.Const && exp.value == 0 -> { t, f -> TreeStm.Branch.Jump(TreeExp.Name(f), listOf(f)) }
            exp is TreeExp.Const && exp.value == 1 -> { t, f -> TreeStm.Branch.Jump(TreeExp.Name(t), listOf(t)) }
            else -> { t, f -> TreeStm.Branch.CJump(RelOp.EQ, exp, TreeExp.Const(0), f, t) }
        }
    }

    class Nx(val stm: TreeStm) : TrExp() {
        override fun equals(other: Any?) = other is Nx && stm == other.stm
        override fun hashCode() = stm.hashCode()
        override fun toString() = "Nx[$stm]"
        override fun asEx() = TreeExp.ESeq(stm, TreeExp.Const(0))
        override fun asNx() = stm
        override fun asCx() = error("unCx not supported for $this")
    }

    class Cx(val generateStatement: (Label, Label) -> TreeStm) : TrExp() {
        override fun toString() = "Cx[...]"
        override fun asEx(): TreeExp {
            val r = Temp.gen()
            val t = Label.gen()
            val f = Label.gen()

            return TreeExp.ESeq(seq(TreeStm.Move(TreeExp.Temporary(r), TreeExp.Const(1)),
                    generateStatement(t, f),
                    TreeStm.Labeled(f),
                    TreeStm.Move(TreeExp.Temporary(r), TreeExp.Const(0)),
                    TreeStm.Labeled(t)),
                    TreeExp.Temporary(r))
        }

        override fun asNx(): TreeStm {
            val l = Label.gen()
            return TreeStm.Seq(generateStatement(l, l), TreeStm.Labeled(l))
        }

        override fun asCx() = generateStatement
    }
}
