package kiger.temp

private var labelIdSeq = 0;

class Label {
    val name = "l" + ++labelIdSeq;

    override fun toString() = name
}
