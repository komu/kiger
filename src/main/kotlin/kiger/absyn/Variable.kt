package kiger.absyn

import kiger.lexer.SourceLocation

sealed class Variable(val pos: SourceLocation) {
    class Simple(val name: Symbol, pos: SourceLocation) : Variable(pos) {
        override fun toString(): String = name.toString()
    }
    class Field(val variable: Variable, val name: Symbol, pos: SourceLocation) : Variable(pos)
    class Subscript(val variable: Variable, val exp: Expression, pos: SourceLocation) : Variable(pos)
}
