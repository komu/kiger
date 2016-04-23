package kiger.absyn

import kiger.lexer.SourceLocation
import kiger.lexer.Token.Symbol

data class Field(val name: Symbol, val typ: Symbol, val pos: SourceLocation) {
    var escape = true
}
