package kiger.absyn

import kiger.lexer.SourceLocation
import kiger.lexer.Token.Symbol

sealed class Declaration {
    class Functions(val declarations: List<FunctionDeclatation>) : Declaration() {
        override fun toString() = declarations.toString()
    }
    class Var(val name: Symbol, var escape: Boolean, val type: Pair<Symbol, SourceLocation>?, val init: Expression, val pos: SourceLocation) : Declaration()
    class TypeDec(val name: Symbol, val type: TypeRef) : Declaration()
}
