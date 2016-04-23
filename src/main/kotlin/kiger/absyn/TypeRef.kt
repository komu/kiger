package kiger.absyn

import kiger.lexer.SourceLocation
import kiger.lexer.Token.Symbol

sealed class TypeRef {
    class Name(val typeName: Symbol, val pos: SourceLocation) : TypeRef()
    class Record(val fields: List<Field>) : TypeRef()
    class Array(val typeName: Symbol, val pos: SourceLocation) : TypeRef()
}
