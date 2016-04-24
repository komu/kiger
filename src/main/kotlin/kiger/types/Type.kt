package kiger.types

import kiger.lexer.Token.Symbol

sealed class Type {
    object Nil : Type()
    object Int : Type()
    object String : Type()
    object Unit : Type()
    class Record(val fields: List<Pair<Symbol, Type>>): Type()
    class Array(val elementType: Type)
    class Name(val name: Symbol) {
        var type: Type? = null
    }
}
