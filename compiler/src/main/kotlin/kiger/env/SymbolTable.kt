package kiger.env

import kiger.absyn.Symbol

class SymbolTable<T> private constructor(private val parent: SymbolTable<T>?) {

    constructor(): this(null)

    private val map = mutableMapOf<Symbol, T>()

    operator fun get(symbol: Symbol): T? =
        map[symbol] ?: parent?.get(symbol)

    operator fun set(symbol: Symbol, value: T) {
        map[symbol] = value
    }

    fun child() = SymbolTable(this)
}
