package kiger.absyn

import kiger.lexer.SourceLocation

sealed class Variable(val pos: SourceLocation) {

    class Simple(val name: Symbol, pos: SourceLocation) : Variable(pos) {
        override fun toString() = name.toString()
    }

    class Field(val variable: Variable, val name: Symbol, pos: SourceLocation) : Variable(pos) {
        override fun toString() = "$variable.$name"
    }

    class Subscript(val variable: Variable, val exp: Expression, pos: SourceLocation) : Variable(pos) {
        override fun toString() = "$variable[$exp]"
    }
}
