package kiger.absyn

import kiger.lexer.SourceLocation
import kiger.lexer.Token.Operator
import kiger.lexer.Token.Symbol

sealed class Expression {
    class Var(val variable: Variable) : Expression() {
        override fun toString() = variable.toString()
    }
    object Nil : Expression()
    class Int(val value: kotlin.Int) : Expression() {
        override fun toString() = value.toString()
    }
    class String(val value: kotlin.String, val pos: SourceLocation) : Expression() {
        override fun toString() = '"' + value.replace("\"", "\\\"") + '"'
    }
    class Call(val func: Symbol, val args: List<Expression>, val pos: SourceLocation) : Expression() {
        override fun toString() = "$func(${args.joinToString(", ")})"
    }
    class Op(val left: Expression, val op: Operator, val right: Expression, val pos: SourceLocation) : Expression() {
        override fun toString() = "($left $op $right)"
    }
    class Record(val fields: List<FieldDef>, val typ: Symbol, val pos: SourceLocation) : Expression()
    class Seq(val exps: List<Pair<Expression, SourceLocation>>) : Expression() {
        override fun toString() = exps.joinToString("; ") { it.first.toString() }
    }
    class Assign(val variable: Variable, val exp: Expression, val pos: SourceLocation) : Expression() {
        override fun toString() = "$variable = $exp"
    }
    class If(val test: Expression, val then: Expression, val alt: Expression?, val pos: SourceLocation) : Expression() {
        override fun toString() = "if $test then $then" + (alt?.let { " else $alt" } ?: "")
    }
    class While(val test: Expression, val body: Expression, val pos: SourceLocation) : Expression() {
        override fun toString() = "while ($test) $body"
    }
    class For(val variable: Symbol, var escape: Boolean, val lo: Expression, val hi: Expression, val bod: Expression, val pos: SourceLocation) : Expression()
    class Break(val pos: Expression) : Expression()
    class Let(val declarations: List<Declaration>, val body: Expression, val pos: SourceLocation) : Expression()
    class Array(val typ: Symbol, val size: Expression, val init: Expression, val pos: SourceLocation) : Expression()
}
