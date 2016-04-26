package kiger.absyn

import kiger.lexer.SourceLocation
import kiger.lexer.Token.Symbol

sealed class Declaration {

    class Functions(val declarations: List<FunctionDeclaration>) : Declaration() {
        override fun toString() = declarations.toString()
    }

    class Types(val declarations: List<TypeDeclaration>) : Declaration() {
        override fun toString() = declarations.toString()
    }

    class Var(val name: Symbol, var escape: Boolean, val type: Pair<Symbol, SourceLocation>?, val init: Expression, val pos: SourceLocation) : Declaration()
}

data class FunctionDeclaration(
        val name: Symbol,
        val params: List<Field>,
        val result: Pair<Symbol, SourceLocation>?,
        val body: Expression,
        val pos: SourceLocation)

data class TypeDeclaration(
        val name: Symbol,
        val type: TypeRef,
        val pos: SourceLocation)
