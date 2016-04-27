package kiger.absyn

import kiger.lexer.SourceLocation

data class Field(val name: Symbol, val type: Symbol, val pos: SourceLocation) {
    var escape = true
}
