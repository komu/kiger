package kiger.regalloc

import kiger.target.Register
import kiger.temp.Temp

class Coloring {

    private val colorMapping = mutableMapOf<Temp, Register>()

    fun name(t: Temp): String =
        colorMapping[t]?.name ?: t.name

    operator fun get(t: Temp): Register? = colorMapping[t]

    operator fun set(t: Temp, r: Register) {
        colorMapping[t] = r
    }

    override fun toString() = colorMapping.toString()
}
