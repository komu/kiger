package kiger.translate

import kiger.frame.Fragment
import kiger.frame.Frame
import kiger.frame.FrameAccess
import kiger.lexer.Token
import kiger.temp.Label
import kiger.temp.Temp
import kiger.tree.BinaryOp
import kiger.tree.RelOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm

sealed class Level {
    object Top : Level()
    class Lev(val parent: Level, val frame: Frame) : Level()
}

data class Access(val level: Level, val frameAccess: FrameAccess)

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
            else                                   -> { t, f -> TreeStm.CJump(RelOp.EQ, exp, TreeExp.Const(0), f, t) }
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

    companion object {
        val fragments = mutableListOf<Fragment>()

        val nilExp: TrExp = Ex(TreeExp.Const(0))

        val errorExp: TrExp = Ex(TreeExp.Const(0))

        fun intLiteral(value: Int): TrExp = Ex(TreeExp.Const(value))

        fun stringLiteral(s: String): TrExp {
            val t = fragments.asSequence().filterIsInstance<Fragment.Str>().find { s == it.value }

            return Ex(TreeExp.Name(t?.label ?: run {
                val label = Label()
                fragments += Fragment.Str(label, s)
                label
            }))
        }

        fun binop(op: Token.Operator, e1: TrExp, e2: TrExp): TrExp {
            val left = e1.unEx()
            val right = e2.unEx()
            val treeOp = when (op) {
                Token.Operator.Plus     -> BinaryOp.PLUS
                Token.Operator.Minus    -> BinaryOp.MINUS
                Token.Operator.Multiply -> BinaryOp.MUL
                Token.Operator.Divide   -> BinaryOp.DIV
                else                    -> error("unexpected op: $op")
            }

            return TrExp.Ex(TreeExp.BinOp(treeOp, left, right))
        }

        fun relop(op: Token.Operator, e1: TrExp, e2: TrExp): TrExp {
            val left = e1.unEx()
            val right = e2.unEx()

            val treeOp = when (op) {
                Token.Operator.EqualEqual           -> RelOp.EQ
                Token.Operator.NotEqual             -> RelOp.NE
                Token.Operator.LessThan             -> RelOp.LT
                Token.Operator.LessThanOrEqual      -> RelOp.LE
                Token.Operator.GreaterThan          -> RelOp.GT
                Token.Operator.GreaterThanOrEqual   -> RelOp.GE
                else                                -> error("unexpected op: $op")
            }

            return TrExp.Cx { t, f -> TreeStm.CJump(treeOp, left, right, t, f) }
        }

        /**
         * fetch static links between the level of use (the
         * level passed to simpleVar) and the level of definition
         * (the level within the variable's access)
         */
        fun simpleVar(access: Access, level: Level): TrExp {
            fun iter(currentLevel: Level, acc: TreeExp): TreeExp =
                if (access.level === currentLevel) {
                    Frame.exp(access.frameAccess, acc)
                } else {
                    currentLevel as Level.Lev
                    val staticLink = currentLevel.frame.formals
                    iter(currentLevel.parent, Frame.exp(staticLink, acc))
                }

            return TrExp.Ex(iter(level, TreeExp.Temporary(Frame.FP)))
        }
    }
}

private fun seq(vararg statements: TreeStm): TreeStm = seq(statements.asList())

private fun seq(statements: List<TreeStm>): TreeStm =
    when (statements.size) {
        0    -> error("no statements")
        1    -> statements[0]
        2    -> TreeStm.Seq(statements[0], statements[1])
        else -> TreeStm.Seq(statements[0], seq(statements.subList(1, statements.size)))
    }
