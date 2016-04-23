package kiger.absyn

import kiger.lexer.SourceLocation
import kiger.lexer.Token.Symbol

data class FieldDef(val name: Symbol, val exp: Expression, val pos: SourceLocation)
