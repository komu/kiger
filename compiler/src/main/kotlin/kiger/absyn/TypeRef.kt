package kiger.absyn

import kiger.lexer.SourceLocation

sealed class TypeRef {
    class Name(val name: Symbol, val pos: SourceLocation) : TypeRef()
    class Record(val fields: List<Field>) : TypeRef()
    class Array(val elementType: Symbol, val pos: SourceLocation) : TypeRef()
}
