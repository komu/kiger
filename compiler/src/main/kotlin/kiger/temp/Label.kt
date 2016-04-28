package kiger.temp

private var labelIdSeq = 0;

class Label(val name: String) {

    override fun toString() = name
    override fun equals(other: Any?) = other is Label && name == other.name
    override fun hashCode() = name.hashCode()

    companion object {

        /**
         * Generates a new label using given prefix.
         */
        fun gen(prefix: String = "l") = Label(prefix + ++labelIdSeq)
    }
}

/** Useful for making tests deterministic */
fun resetLabelSequence() {
    labelIdSeq = 0
}
