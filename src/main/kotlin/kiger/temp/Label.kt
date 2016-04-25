package kiger.temp

private var labelIdSeq = 0;

class Label(val name: String) {

    constructor(): this("l" + ++labelIdSeq)

    override fun toString() = name
    override fun equals(other: Any?) = other is Label && name == other.name
    override fun hashCode() = name.hashCode()
}

/** Useful for making tests deterministic */
fun resetLabelSequence() {
    labelIdSeq = 0
}
