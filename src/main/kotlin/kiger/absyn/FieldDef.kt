package kiger.absyn

import kiger.lexer.SourceLocation

data class FieldDef(val name: Symbol, val exp: Expression, val pos: SourceLocation)
