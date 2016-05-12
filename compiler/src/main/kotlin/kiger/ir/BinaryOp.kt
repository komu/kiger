package kiger.ir

enum class BinaryOp(val sym: String) {
    PLUS("+"), MINUS("-"), MUL("*"), DIV("/"), AND("*"), OR("||"), LSHIFT("<<"), RSHIFT(">>"), XOR("^");

    override fun toString() = sym
}
