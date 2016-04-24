package kiger.translate

import kiger.temp.Label
import kiger.temp.Temp
import kiger.tree.RelOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm

sealed class TrExp {

    abstract fun unEx(): TreeExp
    abstract fun unNx(): TreeStm
    abstract fun unCx(): (Label, Label) -> TreeStm

    class Ex(val exp: TreeExp) : TrExp() {
        override fun equals(other: Any?) = other is Ex && exp == other.exp
        override fun hashCode() = exp.hashCode()
        override fun toString() = "Ex[$exp]"
        override fun unEx() = exp
        override fun unNx() = TreeStm.Exp(exp)
        override fun unCx(): (Label, Label) -> TreeStm = when {
            exp is TreeExp.Const && exp.value == 0 -> { t, f -> TreeStm.Jump(TreeExp.Name(f), listOf(f)) }
            exp is TreeExp.Const && exp.value == 1 -> { t, f -> TreeStm.Jump(TreeExp.Name(t), listOf(t)) }
            else -> { t, f -> TreeStm.CJump(RelOp.EQ, exp, TreeExp.Const(0), f, t) }
        }
    }

    class Nx(val stm: TreeStm) : TrExp() {
        override fun equals(other: Any?) = other is Nx && stm == other.stm
        override fun hashCode() = stm.hashCode()
        override fun toString() = "Nx[$stm]"
        override fun unEx() = TreeExp.ESeq(stm, TreeExp.Const(0))
        override fun unNx() = stm
        override fun unCx() = error("unCx not supported for $this")
    }

    class Cx(val generateStatement: (Label, Label) -> TreeStm) : TrExp() {
        override fun toString() = "Cx[...]"
        override fun unEx(): TreeExp {
            val r = Temp()
            val t = Label()
            val f = Label()

            return TreeExp.ESeq(seq(TreeStm.Move(TreeExp.Temporary(r), TreeExp.Const(1)),
                    generateStatement(t, f),
                    TreeStm.Labeled(f),
                    TreeStm.Move(TreeExp.Temporary(r), TreeExp.Const(0)),
                    TreeStm.Labeled(t)),
                    TreeExp.Temporary(r))
        }

        override fun unNx(): TreeStm {
            val l = Label()
            return TreeStm.Seq(generateStatement(l, l), TreeStm.Labeled(l))
        }

        override fun unCx() = generateStatement
    }
}
