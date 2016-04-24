package kiger.types

import kiger.diag.Diagnostics
import kiger.lexer.SourceLocation
import kiger.lexer.Token.Symbol

sealed class Type {

    open fun actualType(pos: SourceLocation, diagnostics: Diagnostics): Type = this

    object Nil : Type()
    object Int : Type()
    object String : Type()
    object Unit : Type()
    class Record(val fields: List<Pair<Symbol, Type>>): Type()

    class Array(val elementType: Type) : Type() {
        override fun actualType(pos: SourceLocation, diagnostics: Diagnostics): Type =
            Array(elementType.actualType(pos, diagnostics))
    }

    class Name(val name: Symbol) : Type(){
        var type: Type? = null

        override fun actualType(pos: SourceLocation, diagnostics: Diagnostics): Type =
            type?.actualType(pos, diagnostics) ?: run {
                diagnostics.error("undefined type $name", pos)
                Nil
            }
    }
}
