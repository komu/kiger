package kiger.temp

private var tempIdSeq = 0;

class Temp(val name: String) : Comparable<Temp> {

    constructor(): this("t" + ++tempIdSeq)

    override fun toString() = name
    override fun equals(other: Any?) = other is Temp && name == other.name
    override fun hashCode() = name.hashCode()
    override fun compareTo(other: Temp) = name.compareTo(other.name)
}

/** Useful for making tests deterministic */
fun resetTempSequence() {
    tempIdSeq = 0
}
