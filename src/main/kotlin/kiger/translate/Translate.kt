package kiger.translate

import kiger.frame.Fragment
import kiger.frame.Frame
import kiger.lexer.Token
import kiger.temp.Label
import kiger.tree.BinaryOp
import kiger.tree.RelOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm

object Translate {
    val fragments = mutableListOf<Fragment>()

    val nilExp: TrExp = TrExp.Ex(TreeExp.Const(0))

    val errorExp: TrExp = TrExp.Ex(TreeExp.Const(0))

    fun intLiteral(value: Int): TrExp = TrExp.Ex(TreeExp.Const(value))

    fun stringLiteral(s: String): TrExp {
        val t = fragments.asSequence().filterIsInstance<Fragment.Str>().find { s == it.value }

        return TrExp.Ex(TreeExp.Name(t?.label ?: run {
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
                iter(currentLevel.parent, Frame.exp(currentLevel.frame.formals.first(), acc))
            }

        return TrExp.Ex(iter(level, TreeExp.Temporary(Frame.FP)))
    }

    fun fieldVar(base: TrExp, index: Int): TrExp =
        TrExp.Ex(memPlus(base.unEx(),
            TreeExp.BinOp(BinaryOp.MUL, TreeExp.Const(index), TreeExp.Const(Frame.wordSize))))

    fun memPlus(e1: TreeExp, e2: TreeExp): TreeExp =
        TreeExp.Mem(TreeExp.BinOp(BinaryOp.PLUS, e1, e2))

    fun subscriptVar(base: TrExp, offset: TrExp): TrExp =
        TrExp.Ex(memPlus(base.unEx(),
            TreeExp.BinOp(BinaryOp.MUL, offset.unEx(), TreeExp.Const(Frame.wordSize))))
}

fun seq(vararg statements: TreeStm): TreeStm = seq(statements.asList())

fun seq(statements: List<TreeStm>): TreeStm =
    when (statements.size) {
        0    -> error("no statements")
        1    -> statements[0]
        2    -> TreeStm.Seq(statements[0], statements[1])
        else -> TreeStm.Seq(statements[0], seq(statements.subList(1, statements.size)))
    }
