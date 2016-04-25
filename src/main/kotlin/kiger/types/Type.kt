package kiger.types

import kiger.diag.Diagnostics
import kiger.lexer.SourceLocation
import kiger.lexer.Token.Symbol

sealed class Type {

    open fun actualType(pos: SourceLocation, diagnostics: Diagnostics): Type = this
    override abstract fun toString(): kotlin.String

    object Nil : Type() {
        override fun toString() = "nil"
    }

    object Int : Type() {
        override fun toString() = "int"
    }

    object String : Type() {
        override fun toString() = "string"
    }

    object Unit : Type() {
        override fun toString() = "unit"
    }

    class Record(val fields: List<Pair<Symbol, Type>>): Type() {
        override fun toString() = "record" // TODO: improve
    }

    class Array(val elementType: Type) : Type() {
        override fun toString() = "array of $elementType"

        override fun actualType(pos: SourceLocation, diagnostics: Diagnostics): Type =
            Array(elementType.actualType(pos, diagnostics))
    }

    class Name(val name: Symbol) : Type() {
        var type: Type? = null

        override fun toString() = "name of $name"

        override fun actualType(pos: SourceLocation, diagnostics: Diagnostics): Type =
            type?.actualType(pos, diagnostics) ?: run {
                diagnostics.error("undefined type $name", pos)
                Nil
            }
    }
}
