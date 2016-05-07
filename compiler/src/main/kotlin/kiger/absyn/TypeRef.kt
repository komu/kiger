package kiger.absyn

import kiger.lexer.SourceLocation

sealed class TypeRef {

    class Name(val name: Symbol, val pos: SourceLocation) : TypeRef() {
        override fun toString() = name.toString()
    }

    class Record(val fields: List<Field>) : TypeRef() {
        override fun toString() = fields.joinToString(", ", "{", "}")
    }

    class Array(val elementType: Symbol, val pos: SourceLocation) : TypeRef() {
        override fun toString() = "array of $elementType"
    }
}
