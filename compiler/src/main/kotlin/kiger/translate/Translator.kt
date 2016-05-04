package kiger.translate

import kiger.frame.Fragment
import kiger.frame.FrameType
import kiger.frame.MipsFrame
import kiger.lexer.Token
import kiger.temp.Label
import kiger.temp.Temp
import kiger.tree.BinaryOp
import kiger.tree.RelOp
import kiger.tree.TreeExp
import kiger.tree.TreeStm
import kiger.utils.cons
import kiger.utils.splitLast
import kiger.utils.tail

class Translator {
    val fragments = mutableListOf<Fragment>()

    val outermost = Level.Top

    val nilExp: TrExp = TrExp.Ex(TreeExp.Const(0))

    val errorExp: TrExp = TrExp.Ex(TreeExp.Const(999))

    val frameType: FrameType = MipsFrame

    fun newLevel(parent: Level, name: Label, formalEscapes: List<Boolean>) =
        Level.Lev(parent, frameType.newFrame(name, cons(true, formalEscapes)))

    fun intLiteral(value: Int): TrExp = TrExp.Ex(TreeExp.Const(value))

    fun stringLiteral(s: String): TrExp {
        val t = fragments.asSequence().filterIsInstance<Fragment.Str>().find { s == it.value }

        return TrExp.Ex(TreeExp.Name(t?.label ?: run {
            val label = Label.gen()
            fragments += Fragment.Str(label, s)
            label
        }))
    }

    fun binop(op: Token.Operator, e1: TrExp, e2: TrExp): TrExp {
        val left = e1.asEx()
        val right = e2.asEx()
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
        val left = e1.asEx()
        val right = e2.asEx()

        val treeOp = when (op) {
            Token.Operator.EqualEqual           -> RelOp.EQ
            Token.Operator.NotEqual             -> RelOp.NE
            Token.Operator.LessThan             -> RelOp.LT
            Token.Operator.LessThanOrEqual      -> RelOp.LE
            Token.Operator.GreaterThan          -> RelOp.GT
            Token.Operator.GreaterThanOrEqual   -> RelOp.GE
            else                                -> error("unexpected op: $op")
        }

        return TrExp.Cx { t, f -> TreeStm.Branch.CJump(treeOp, left, right, t, f) }
    }

    /**
     * fetch static links between the level of use (the
     * level passed to simpleVar) and the level of definition
     * (the level within the variable's access)
     */
    fun simpleVar(access: Access, level: Level): TrExp {
        fun iter(currentLevel: Level, acc: TreeExp): TreeExp =
            if (access.level === currentLevel) {
                frameType.exp(access.frameAccess, acc)
            } else {
                currentLevel as Level.Lev
                iter(currentLevel.parent, frameType.exp(currentLevel.frame.formals.first(), acc))
            }

        return TrExp.Ex(iter(level, TreeExp.Temporary(frameType.FP)))
    }

    fun fieldVar(base: TrExp, index: Int): TrExp =
        TrExp.Ex(memPlus(base.asEx(),
            TreeExp.BinOp(BinaryOp.MUL, TreeExp.Const(index), TreeExp.Const(frameType.wordSize))))

    fun memPlus(e1: TreeExp, e2: TreeExp): TreeExp =
        TreeExp.Mem(TreeExp.BinOp(BinaryOp.PLUS, e1, e2))

    fun subscriptVar(base: TrExp, offset: TrExp): TrExp =
        TrExp.Ex(memPlus(base.asEx(),
            TreeExp.BinOp(BinaryOp.MUL, offset.asEx(), TreeExp.Const(frameType.wordSize))))

    fun record(fields: List<TrExp>): TrExp {
        val r = Temp.gen()
        val init = TreeStm.Move(TreeExp.Temporary(r), frameType.externalCall("allocRecord", listOf(TreeExp.Const(fields.size * frameType.wordSize))))

        val inits = fields.mapIndexed { i, e ->
            TreeStm.Move(memPlus(TreeExp.Temporary(r), TreeExp.Const(i * frameType.wordSize)), e.asEx())
        }

        return TrExp.Ex(TreeExp.ESeq(seq(cons(init, inits)), TreeExp.Temporary(r)))
    }

    /**
     * Evaluate all expressions in sequence and return the value of the last.
     * If the last expression is a statement, then the whole sequence will be
     * a statement.
     */
    fun sequence(exps: List<TrExp>): TrExp = when (exps.size) {
        0 -> TrExp.Nx(TreeStm.Exp(TreeExp.Const(0)))
        1 -> exps.first()
        else -> {
            val (first, last) = exps.splitLast()
            val firstStm = seq(first.map { it.asNx() })
            when (last) {
                is TrExp.Nx -> TrExp.Nx(TreeStm.Seq(firstStm, last.stm))
                else        -> TrExp.Ex(TreeExp.ESeq(firstStm, last.asEx()))
            }
        }
    }

    fun assign(left: TrExp, right: TrExp): TrExp =
        TrExp.Nx(TreeStm.Move(left.asEx(), right.asEx()))

    fun ifElse(testExp: TrExp, thenExp: TrExp, elseExp: TrExp?): TrExp {
        val r = Temp.gen()
        val t = Label.gen("true")
        val f = Label.gen("false")
        val finish = Label.gen("after_if")
        val testFun = testExp.asCx()
        return when (thenExp) {
            is TrExp.Ex -> {
                elseExp!! // if there's no else, this is Nx

                TrExp.Ex(TreeExp.ESeq(seq(
                        testFun(t, f),
                        TreeStm.Labeled(t),
                        TreeStm.Move(TreeExp.Temporary(r), thenExp.exp),
                        TreeStm.Branch.Jump(TreeExp.Name(finish), listOf(finish)),
                        TreeStm.Labeled(f),
                        TreeStm.Move(TreeExp.Temporary(r), elseExp.asEx()),
                        TreeStm.Labeled(finish)),
                    TreeExp.Temporary(r))
                )
            }
            is TrExp.Nx -> {
                if (elseExp == null) {
                    TrExp.Nx(seq(
                            testFun(t, f),
                            TreeStm.Labeled(t),
                            thenExp.stm,
                            TreeStm.Labeled(f)))
                } else {
                    TrExp.Nx(seq(
                            testFun(t, f),
                            TreeStm.Labeled(t),
                            TreeStm.Branch.Jump(TreeExp.Name(finish), listOf(finish)),
                            thenExp.stm,
                            TreeStm.Labeled(f),
                            elseExp.asNx(),
                            TreeStm.Labeled(finish)))
                }
            }
            is TrExp.Cx -> { // TODO fix this
                elseExp!! // if there's no else, this is Nx

                TrExp.Cx { tt, ff ->
                    seq(testFun(t, f),
                        TreeStm.Labeled(t),
                        thenExp.generateStatement(tt, ff),
                        TreeStm.Labeled(f),
                        elseExp.asCx()(tt, ff))
                }
            }
        }
    }

    fun loop(test: TrExp, body: TrExp, doneLabel: Label): TrExp {
        val testLabel = Label.gen("loopTest")
        val bodyLabel = Label.gen("loopBody")

        return TrExp.Nx(seq(
                        TreeStm.Labeled(testLabel),
                        TreeStm.Branch.CJump(RelOp.EQ, test.asEx(), TreeExp.Const(0), doneLabel, bodyLabel),
                        TreeStm.Labeled(bodyLabel),
                        body.asNx(),
                        TreeStm.Branch.Jump(TreeExp.Name(testLabel), listOf(testLabel)),
                        TreeStm.Labeled(doneLabel)))
    }

    fun doBreak(label: Label): TrExp =
        TrExp.Nx(TreeStm.Branch.Jump(TreeExp.Name(label), listOf(label)))

    fun letExp(decs: List<TrExp>, body: TrExp): TrExp = when (decs.size) {
        0    -> body
        1    -> TrExp.Ex(TreeExp.ESeq(decs.first().asNx(), body.asEx()))
        else -> TrExp.Ex(TreeExp.ESeq(seq(decs.map { it.asNx() }), body.asEx()))
    }

    fun array(size: TrExp, init: TrExp): TrExp =
        TrExp.Ex(frameType.externalCall("initArray", listOf(size.asEx(), init.asEx())))

    fun call(useLevel: Level, defLevel: Level, label: Label, args: List<TrExp>, isProcedure: Boolean): TrExp {
        val argExps = args.map { it.asEx() }
        val call = if (defLevel.parent == Level.Top) {
            frameType.externalCall(label.name, argExps)

        } else {
            val diff = useLevel.depth - defLevel.depth + 1
            fun iter(d: Int, curLevel: Level): TreeExp =
                if (d == 0) {
                    TreeExp.Temporary(frameType.FP)
                } else {
                    curLevel as Level.Lev
                    frameType.exp(curLevel.frame.formals.first(), iter(d - 1, curLevel.parent))
                }

            TreeExp.Call(TreeExp.Name(label), cons(iter(diff, useLevel), argExps))
        }

        return if (isProcedure)
            TrExp.Nx(TreeStm.Exp(call))
        else
            TrExp.Ex(call)
    }

    fun allocLocal(level: Level, escape: Boolean): Access =
        Access(level, (level as Level.Lev).frame.allocLocal(escape))

    fun procEntryExit(level: Level.Lev, body: TrExp) {
        val body2 = level.frame.procEntryExit1(TreeStm.Move(TreeExp.Temporary(frameType.RV), body.asEx()))

        fragments += Fragment.Proc(body2, level.frame)
    }

    /**
     * Return formals associated with the frame in this level,
     * excluding the static link (first element of the list).
     */
    fun formals(level: Level.Lev): List<Access> =
        level.frame.formals.tail().map { Access(level, it) }
}

fun seq(vararg statements: TreeStm): TreeStm = seq(statements.asList())

fun seq(statements: List<TreeStm>): TreeStm =
    when (statements.size) {
        0    -> error("no statements")
        1    -> statements[0]
        2    -> TreeStm.Seq(statements[0], statements[1])
        else -> TreeStm.Seq(statements[0], seq(statements.tail()))
    }
