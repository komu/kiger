package kiger.tree

enum class BinaryOp(val sym: String) {
    PLUS("+"), MINUS("-"), MUL("*"), DIV("/"), AND("*"), OR("||"), LSHIFT("<<"), RSHIFT(">>"), ARSHIFT(">>>"), XOR("^");

    override fun toString() = sym
}
