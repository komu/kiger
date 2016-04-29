package kiger.escape

import kiger.absyn.Declaration
import kiger.absyn.Expression
import kiger.absyn.Expression.*
import kiger.absyn.FunctionDeclaration
import kiger.absyn.Variable
import kiger.env.SymbolTable
import kiger.absyn.Expression.Array as ArrayExp
import kiger.absyn.Expression.Int as IntExp
import kiger.absyn.Expression.String as StringExp

/**
 * Performs escape analysis on the expression tree.
 *
 * When parser originally constructs the expression tree, it conservatively
 * marks all variables and parameters as escaping. This routine goes through
 * the tree and checks if variables actually escape (are used at a depth
 * deeper than it is defined) and mark them correspondingly.
 *
 * Instead of rewriting the tree, calling this will modify the original tree.
 */
fun Expression.analyzeEscapes() {
    val env = SymbolTable<EscapeInfo>()
    traverseExp(env, 0)
}

private class EscapeInfo(val depth: Int, val flagEscape: () -> Unit)

private fun Expression.traverseExp(env: SymbolTable<EscapeInfo>, depth: Int) {
    when (this) {
        is Var          -> variable.traverseVar(env, depth)
        is Call         -> args.forEach { it.traverseExp(env, depth) }
        is Op           -> { left.traverseExp(env, depth); right.traverseExp(env, depth) }
        is Record       -> fields.forEach { it.exp.traverseExp(env, depth) }
        is Seq          -> exps.forEach { it.first.traverseExp(env, depth) }
        is Assign       -> { variable.traverseVar(env, depth); exp.traverseExp(env, depth) }
        is If           -> { test.traverseExp(env, depth); then.traverseExp(env, depth); alt?.traverseExp(env, depth) }
        is While        -> { test.traverseExp(env, depth); body.traverseExp(env, depth) }
        is ArrayExp     -> { size.traverseExp(env, depth); init.traverseExp(env, depth) }
        is Let          -> body.traverseExp(declarations.fold(env) { e, d -> d.traverseDeclaration(e, depth) }, depth)
        is For          -> {
            lo.traverseExp(env, depth)
            hi.traverseExp(env, depth)
            escape = false
            body.traverseExp(env.enter(variable, EscapeInfo(depth) { escape = true }), depth)
        }
        is Nil,
        is IntExp,
        is StringExp,
        is Break        -> {}
    }
}

private fun Variable.traverseVar(env: SymbolTable<EscapeInfo>, depth: Int) {
    when (this) {
        is Variable.Simple      -> {
            val b = env[name]!!
            if (depth > b.depth)
                b.flagEscape()
        }
        is Variable.Field       -> variable.traverseVar(env, depth)
        is Variable.Subscript   -> variable.traverseVar(env, depth)
    }
}

private fun Declaration.traverseDeclaration(env: SymbolTable<EscapeInfo>, depth: Int): SymbolTable<EscapeInfo> = when (this) {
    is Declaration.Functions -> declarations.fold(env) { e, d -> d.traverseFundec(e, depth) }
    is Declaration.Types -> env
    is Declaration.Var -> {
        init.traverseExp(env, depth)
        escape = false
        env.enter(name, EscapeInfo(depth) { escape = true})
    }
}

private fun FunctionDeclaration.traverseFundec(env: SymbolTable<EscapeInfo>, depth: Int): SymbolTable<EscapeInfo> {
    val newEnv = params.fold(env) { e, p ->
        p.escape = false
        e.enter(p.name, EscapeInfo(depth + 1) { p.escape = true })
    }

    body.traverseExp(newEnv, depth + 1)
    return env
}
