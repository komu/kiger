package kiger.translate

import kiger.absyn.Declaration
import kiger.absyn.Expression
import kiger.absyn.Variable
import kiger.diag.Diagnostics
import kiger.env.EnvEntry
import kiger.env.SymbolTable
import kiger.lexer.SourceLocation
import kiger.lexer.Token
import kiger.lexer.Token.Operator
import kiger.lexer.Token.Symbol
import kiger.temp.Label
import kiger.types.Type

class VarEnv {
    val table = SymbolTable<EnvEntry>()

    operator fun get(symbol: Symbol): EnvEntry? = table[symbol]

    fun enter(name: Symbol, entry: EnvEntry): VarEnv {
        TODO()
    }
}

class TypeEnv {
    val table = SymbolTable<Type>()

    operator fun get(symbol: Symbol): Type? = table[symbol]
}

private enum class Kind {
    ARITH, COMP, EQ
}

private fun Token.Operator.classify(): Kind = when (this) {
    Operator.Plus -> Kind.ARITH
    Operator.Minus -> Kind.ARITH
    Operator.Multiply-> Kind.ARITH
    Operator.Divide -> Kind.ARITH
    Operator.LessThan -> Kind.COMP
    Operator.GreaterThan -> Kind.COMP
    Operator.LessThanOrEqual -> Kind.COMP
    Operator.GreaterThanOrEqual -> Kind.COMP
    Operator.EqualEqual -> Kind.EQ
    Operator.NotEqual -> Kind.EQ
}

data class TranslationResult(val exp: TrExp, val type: Type)

class Translator {

    private val diagnostics = Diagnostics()

    private val errorResult = TranslationResult(Translate.errorExp, Type.Nil)

    fun transExp(e: Expression, venv: VarEnv, tenv: TypeEnv, level: Level, breakLabel: Label?): TranslationResult {
        fun trexp(exp: Expression): TranslationResult = when (exp) {
            is Expression.Nil ->
                TranslationResult(Translate.nilExp, Type.Nil)

            is Expression.Int ->
                TranslationResult(Translate.intLiteral(exp.value), Type.Int)

            is Expression.String ->
                TranslationResult(Translate.stringLiteral(exp.value), Type.String)

            is Expression.Op -> {
                val (le, lt) = trexp(exp.left)
                val (re, rt) = trexp(exp.right)

                fun checkEquality() = when (lt) {
                    Type.Int,
                    Type.String,
                    is Type.Array,
                    is Type.Record -> checkType(lt, rt, exp.pos)
                    else -> {
                        diagnostics.error("can only check equality on int, string, array of record types, found $rt", exp.pos)
                    }
                }

                fun checkComparable() = when (lt) {
                    Type.Int,
                    Type.String -> checkType(lt, rt, exp.pos)
                    else -> {
                        diagnostics.error("can only compare int or string for ordering, found $rt", exp.pos)
                    }
                }

                when (exp.op.classify()) {
                    Kind.ARITH -> {
                        checkType(Type.Int, lt, exp.pos)
                        checkType(Type.Int, rt, exp.pos)
                        TranslationResult(Translate.binop(exp.op, le, re), Type.Int)
                    }
                    Kind.COMP -> {
                        checkComparable()
                        TranslationResult(Translate.relop(exp.op, le, re), Type.Int)
                    }
                    Kind.EQ -> {
                        checkEquality()
                        TranslationResult(Translate.relop(exp.op, le, re), Type.Int)
                    }
                }
            }

            is Expression.Var ->
                transVar(exp.variable, tenv, venv, level, breakLabel)

            is Expression.Record -> {
                val t = tenv[exp.typ]
                if (t == null) {
                    diagnostics.error("record type ${exp.typ} not found", exp.pos)
                    errorResult
                } else {
                    val ty = t.actualType(exp.pos)
                    if (ty is Type.Record) {
                        val exps = exp.fields.map { trexp(it.exp) }
                        val locations = exp.fields.map { it.pos }

                        val fts = exps.map { it.type }.zip(locations)
                        val fes = exps.map { it.exp }

                        checkRecord(ty.fields, fts, exp.pos)

                        TranslationResult(Translate.record(fes), ty)

                    } else {
                        typeMismatch("record", ty, exp.pos)
                    }
                }
            }

            is Expression.Seq -> {
                val exps = exp.exps.map { trexp(it.first) }
                val type = if (exps.isEmpty()) Type.Unit else exps.last().type

                TranslationResult(Translate.sequence(exps.map { it.exp }), type)
            }

            is Expression.Assign -> {
                val (vexp, vty) = transVar(exp.variable, tenv, venv, level, breakLabel)
                val (eexp, ety) = trexp(exp.exp)

                checkType(vty, ety, exp.pos)

                TranslationResult(Translate.assign(vexp, eexp), Type.Unit)
            }

            is Expression.If -> {
                val (thenExp, thenTy) = trexp(exp.then)
                val (testExp, testTy) = trexp(exp.test)

                checkType(Type.Int, testTy, exp.pos)

                val elseExp = if (exp.alt != null) {
                    val (elseExp, elseTy) = trexp(exp.alt)
                    checkType(thenTy, elseTy, exp.pos)
                    elseExp

                } else {
                    checkType(Type.Unit, thenTy, exp.pos)
                    null
                }

                TranslationResult(Translate.ifElse(testExp, thenExp, elseExp), thenTy)
            }

            is Expression.While -> {
                val doneLabel = Label()
                val (testExp, testTy) = trexp(exp.test)
                val (bodyExp, bodyTy) = transExp(exp.body, venv, tenv, level, doneLabel)

                checkType(Type.Int, testTy, exp.pos);
                checkType(Type.Unit, bodyTy, exp.pos);

                TranslationResult(Translate.loop(testExp, bodyExp, doneLabel), Type.Unit)
            }

            is Expression.Break -> {
                if (breakLabel != null) {
                    TranslationResult(Translate.doBreak(breakLabel), Type.Unit)
                } else {
                    diagnostics.error("invalid break outside loop", exp.pos)
                    errorResult
                }
            }

            is Expression.Let -> {
                var venv2 = venv
                var tenv2 = tenv
                val dexps = mutableListOf<TrExp>()

                for (dec in exp.declarations) {
                    val (venv1, tenv1, exps1) = transDec(dec, venv2, tenv2, level, breakLabel)
                    venv2 = venv1
                    tenv2 = tenv1
                    dexps += exps1
                }

                val (bodyExp, bodyTy) = transExp(exp.body, venv2, tenv2, level, breakLabel)
                TranslationResult(Translate.letExp(dexps, bodyExp), bodyTy)
            }

            is Expression.Array -> {
                val t = tenv[exp.typ]
                if (t == null) {
                    diagnostics.error("type ${exp.typ} not found", exp.pos)
                    errorResult
                } else {
                    val at = t.actualType(exp.pos)
                    if (at is Type.Array) {
                        val (sizeExp, sizeTy) = trexp(exp.size)
                        val (initExp, initTy) = trexp(exp.init)
                        checkType(Type.Int, sizeTy, exp.pos)
                        checkType(at.elementType, initTy, exp.pos)
                        TranslationResult(Translate.array(sizeExp, initExp), at)

                    } else {
                        typeMismatch("array", at, exp.pos)
                    }
                }
            }

            is Expression.For -> {
                // rewrite for to while and translate the while

                val limit = Symbol("limit") // TODO: fresh symbol?
                val ivar = Variable.Simple(exp.variable, exp.pos)
                val limitVar = Variable.Simple(limit, exp.pos)
                val letDecs = listOf(
                        Declaration.Var(exp.variable, exp.escape, null, exp.lo, exp.pos),
                        Declaration.Var(limit, false, null, exp.hi, exp.pos))

                val loop = Expression.While(
                        test = Expression.Op(Expression.Var(ivar), Operator.LessThanOrEqual, Expression.Var(limitVar), exp.pos),
                        body = Expression.Seq(listOf(
                                exp.body to exp.pos,
                                Expression.Assign(ivar, Expression.Op(Expression.Var(ivar), Operator.Plus, Expression.Int(1), exp.pos), exp.pos) to exp.pos)),
                        pos = exp.pos
                )

                trexp(Expression.Let(letDecs, loop, exp.pos))
            }

            is Expression.Call -> {
                val func = venv[exp.func]
                when (func) {
                    null -> {
                        diagnostics.error("function ${exp.func} is not defined", exp.pos)
                        errorResult
                    }
                    is EnvEntry.Var -> {
                        diagnostics.error("function expected, but variable of type ${func.type} found", exp.pos)
                        errorResult
                    }
                    is EnvEntry.Function -> {
                        val argExps = exp.args.map { trexp(it) }
                        checkFormals(func.formals, argExps, exp.pos)

                        TranslationResult(Translate.call(level, func.level, func.label, argExps.map { it.exp }, func.result == Type.Unit), func.result)
                    }
                }
//                let
//                val argexps = map trexp args in
//                        checkformals(formals,argexps,pos);
//                {exp=R.call(level,funlevel,label,map #exp argexps,result=T.UNIT),
//                    ty=actual_ty(result,pos)}
//                end
            }
        }


        return trexp(e)
    }

    private fun transVar(v: Variable, tenv: TypeEnv, venv: VarEnv, level: Level, breakLabel: Label?): TranslationResult {
        return when (v) {
            is Variable.Simple -> {
                val entry = venv[v.name]
                when (entry) {
                    is EnvEntry.Var ->
                        TranslationResult(Translate.simpleVar(entry.access, level), entry.type.actualType(v.pos))
                    is EnvEntry.Function -> {
                        diagnostics.error("expected variable, but function found", v.pos)
                        errorResult
                    }
                    null -> {
                        diagnostics.error("undefined variable: ${v.name}", v.pos)
                        errorResult
                    }
                }
            }
            is Variable.Field -> {
                val (exp, ty) = transVar(v.variable, tenv, venv, level, breakLabel)
                when (ty) {
                    is Type.Record -> {
                        val index = ty.fields.indexOfFirst { v.name == it.first }
                        if (index != -1) {
                            val fieldType = ty.fields[index].second.actualType(v.pos)
                            TranslationResult(Translate.fieldVar(exp, index), fieldType)

                        } else {
                            diagnostics.error("could not find field ${v.name} for $ty", v.pos)
                            errorResult
                        }
                    }
                    else -> {
                        diagnostics.error("expected record type, but $ty found", v.pos)
                        errorResult
                    }
                }
            }
            is Variable.Subscript -> {
                val (exp, ty) = transVar(v.variable, tenv, venv, level, breakLabel)
                val actualType = ty.actualType(v.pos)

                if (actualType is Type.Array) {
                    val (exp1, ty1) = transExp(v.exp, venv, tenv, level, breakLabel)
                    if (ty1 == Type.Int) {
                        TranslationResult(Translate.subscriptVar(exp, exp1), actualType.elementType)

                    } else {
                        diagnostics.error("array subscript should be int, but was $ty1}", v.pos)
                        errorResult
                    }

                } else {
                    typeMismatch("array", actualType, v.pos)
                }
            }
        }
    }

    private fun transDec(dec: Declaration, venv: VarEnv, tenv: TypeEnv, level: Level, breakLabel: Label?): Triple<VarEnv, TypeEnv, List<TrExp>> =
        when (dec) {
            is Declaration.Functions -> transDec(dec, venv, tenv, level, breakLabel)
            is Declaration.Var       -> transDec(dec, venv, tenv, level, breakLabel)
            is Declaration.TypeDec   -> transDec(dec, venv, tenv, level, breakLabel)
        }

    private fun transDec(dec: Declaration.Var, venv: VarEnv, tenv: TypeEnv, level: Level, breakLabel: Label?): Triple<VarEnv, TypeEnv, List<TrExp>> {
        val (exp, ty) = transExp(dec.init, venv, tenv, level, breakLabel)
        val type = if (dec.type == null) {
            if (ty == Type.Nil)
                diagnostics.error("can't use nil", dec.pos)

            ty
        } else {
            val type = tenv[dec.type.first]
            if (type == null) {
                diagnostics.error("type ${dec.type.first} not found", dec.type.second)
                ty
            } else {
                val at = type.actualType(dec.type.second)
                checkType(at, ty, dec.pos)
                at
            }
        }

        val acc = Translate.allocLocal(level, !dec.escape)
        val varexp = Translate.simpleVar(acc, level)
        return Triple(venv.enter(dec.name, EnvEntry.Var(acc, type)), tenv, listOf(Translate.assign(varexp, exp)))
    }

    private fun transDec(dec: Declaration.Functions, venv: VarEnv, tenv: TypeEnv, level: Level, breakLabel: Label?): Triple<VarEnv, TypeEnv, List<TrExp>> =
        TODO()

    private fun transDec(dec: Declaration.TypeDec, venv: VarEnv, tenv: TypeEnv, level: Level, breakLabel: Label?): Triple<VarEnv, TypeEnv, List<TrExp>> =
        TODO()

    private fun typeMismatch(expected: String, actual: Type, pos: SourceLocation): TranslationResult {
        diagnostics.error("expected $expected, but got $actual", pos)
        return errorResult
    }

    private fun checkType(expected: Type, type: Type, pos: SourceLocation) {
        if (type != expected)
            typeMismatch(expected.toString(), type, pos)
    }

    private fun checkRecord(ts: List<Pair<Symbol, Type>>, fs: List<Pair<Type, SourceLocation>>, pos: SourceLocation) {
        if (ts.size != fs.size) {
            diagnostics.error("${ts.size} fields needed, but got ${fs.size}", pos)
        } else {
            for ((t1, t2) in ts.zip(fs))
                checkType(t1.second, t2.first, t2.second)
        }
    }

    private fun checkFormals(ts: List<Pair<Symbol, Type>>, es: List<TranslationResult>, pos: SourceLocation) {
        if (es.size != ts.size) {
            diagnostics.error("${ts.size} args needed, but got ${es.size}", pos)
        } else {
            for ((t, e) in ts.zip(es)) {
                checkType(t.second, e.type, pos)
            }
        }
    }

    private fun Type.actualType(pos: SourceLocation) = actualType(pos, diagnostics)
}
