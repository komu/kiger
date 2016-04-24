package kiger.translate

import kiger.absyn.Expression
import kiger.absyn.Variable
import kiger.diag.Diagnostics
import kiger.env.EnvEntry
import kiger.env.SymbolTable
import kiger.lexer.SourceLocation
import kiger.lexer.Token
import kiger.lexer.Token.Symbol
import kiger.types.Type

class VarEnv {
    val table = SymbolTable<EnvEntry>()

    operator fun get(symbol: Symbol): EnvEntry? = table[symbol]
}

class TypeEnv {
    val table = SymbolTable<Type>()

    operator fun get(symbol: Symbol): Type? = table[symbol]
}

private enum class Kind {
    ARITH, COMP, EQ
}

private fun Token.Operator.classify(): Kind = when (this) {
    Token.Operator.Plus -> Kind.ARITH
    Token.Operator.Minus -> Kind.ARITH
    Token.Operator.Multiply-> Kind.ARITH
    Token.Operator.Divide -> Kind.ARITH
    Token.Operator.LessThan -> Kind.COMP
    Token.Operator.GreaterThan -> Kind.COMP
    Token.Operator.LessThanOrEqual -> Kind.COMP
    Token.Operator.GreaterThanOrEqual -> Kind.COMP
    Token.Operator.EqualEqual -> Kind.EQ
    Token.Operator.NotEqual -> Kind.EQ
}

private fun checkInt(type: Type, pos: SourceLocation) {
    TODO()
}

private fun checkType(expected: Type, type: Type, pos: SourceLocation) {
    TODO()
}

class Translator {

    val diagnostics = Diagnostics()

    private val errorResult = TranslationResult(TrExp.errorExp, Type.Nil)

    fun transExp(venv: VarEnv, tenv: TypeEnv, level: Level, `break`: Any, e: Expression): TranslationResult {
        fun trexp(exp: Expression): TranslationResult = when (exp) {
            is Expression.Nil ->
                TranslationResult(TrExp.nilExp, Type.Nil)

            is Expression.Int ->
                TranslationResult(TrExp.intLiteral(exp.value), Type.Int)

            is Expression.String ->
                TranslationResult(TrExp.stringLiteral(exp.value), Type.String)

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
                        checkInt(lt, exp.pos)
                        checkInt(rt, exp.pos)
                        TranslationResult(TrExp.binop(exp.op, le, re), Type.Int)
                    }
                    Kind.COMP -> {
                        checkComparable()
                        TranslationResult(TrExp.relop(exp.op, le, re), Type.Int)
                    }
                    Kind.EQ -> {
                        checkEquality()
                        TranslationResult(TrExp.relop(exp.op, le, re), Type.Int)
                    }
                }
            }

            is Expression.Var ->
                transVar(exp.variable, venv, level)

            is Expression.Record -> TODO()
            is Expression.Seq -> TODO()
            is Expression.Assign -> TODO()
            is Expression.If -> TODO()
            is Expression.While -> TODO()
            is Expression.Break -> TODO()
            is Expression.Let -> TODO()
            is Expression.Array -> TODO()
            is Expression.For -> TODO()
            is Expression.Call -> TODO()
        }


        return trexp(e)
    }

    private fun transVar(variable: Variable, venv: VarEnv, level: Level): TranslationResult {
        return when (variable) {
            is Variable.Simple -> {
                val entry = venv[variable.name]
                when (entry) {
                    is EnvEntry.Var ->
                        TranslationResult(TrExp.simpleVar(entry.access, level), entry.type.actualType(variable.pos, diagnostics))
                    is EnvEntry.Function -> {
                        diagnostics.error("expected variable, but function found", variable.pos)
                        errorResult
                    }
                    null -> {
                        diagnostics.error("undefined variable: ${variable.name}", variable.pos)
                        errorResult
                    }
                }
                TODO()
            }
            is Variable.Field -> {
                TODO()
            }
            is Variable.Subscript -> {
                TODO()
            }
        }
        /*
            and trvar (A.SimpleVar(id,pos)) =
            (case S.look(venv,id)
             of SOME(E.VarEntry{access,ty}) =>
                {exp=R.simpleVar(access,level),ty=actual_ty(ty,pos)}
              | SOME(_) =>
                (err pos ("expected variable, but function found"); err_result)
              | NONE =>
                (err pos ("undefined variable: " ^ S.name id); err_result))

          | trvar (A.FieldVar(v,id,pos)) =
            let val {exp,ty} = trvar v in
              case ty of
                T.RECORD(flist,_) =>
                (case List.find (fn x => (#1x) = id) flist of
                  NONE =>
                  (err pos ("id: " ^ S.name id ^ " not found");
                   {exp=R.errexp,ty=T.NIL})
                | SOME(rv) =>
                    {exp=R.fieldVar(exp,id,map #1 flist),
                     ty=actual_ty(#2rv,pos)})
              | t =>
                (err pos ("expected record type, but "
                          ^ type2str(t) ^ " found"); err_result)
            end

          | trvar (A.SubscriptVar(v,e,pos)) =
            let val {exp,ty} = trvar v in
              case actual_ty(ty,pos) of
                T.ARRAY(t,_) =>
                let val {exp=exp1,ty=ty1} = trexp e in
                  case ty1 of
                    T.INT => {exp=R.subscriptVar(exp,exp1),ty=t}
                  | t =>
                    (err pos ("array subscript should be int, but "
                              ^ type2str(t) ^ " found"); err_result)
                end
              | t => type_mismatch("array", type2str(t), pos)
            end

         */
        TODO()
    }


}

data class TranslationResult(val exp: TrExp, val type: Type)
