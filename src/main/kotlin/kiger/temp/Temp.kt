package kiger.temp

private var tempIdSeq = 0;

class Temp(val name: String) {

    constructor(): this("t" + ++tempIdSeq)

    override fun toString() = name
    override fun equals(other: Any?) = other is Temp && name == other.name
    override fun hashCode() = name.hashCode()
}

/** Useful for making tests deterministic */
fun resetTempSequence() {
    tempIdSeq = 0
}
