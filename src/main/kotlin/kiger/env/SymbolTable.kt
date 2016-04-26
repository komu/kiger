package kiger.env

import kiger.lexer.Token.Symbol

open class SymbolTable<T> {

    open operator fun get(symbol: Symbol): T? = null

    fun enter(name: Symbol, entry: T): SymbolTable<T> =
        Nested(name, entry, this)

    override fun toString() = "[]"

    private class Nested<T>(val name: Symbol, val entry: T, val parent: SymbolTable<T>) : SymbolTable<T>() {
        override fun get(symbol: Symbol): T? =
            if (symbol == name) entry else parent[symbol]

        override fun toString() = "$name: $entry :: $parent"
    }
}
