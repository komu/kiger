package kiger.env

import kiger.lexer.Token.Symbol

class SymbolTable<T> {
    operator fun get(symbol: Symbol): T? = TODO()

    fun enter(name: Symbol, entry: T): SymbolTable<T> {
        TODO()
    }
}
