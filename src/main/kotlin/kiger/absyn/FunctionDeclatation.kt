package kiger.absyn

import kiger.lexer.SourceLocation
import kiger.lexer.Token.Symbol

data class FunctionDeclatation(
        val name: Symbol,
        val params: List<Field>,
        val result: Pair<Symbol, SourceLocation>?,
        val body: Expression,
        val pos: SourceLocation)
