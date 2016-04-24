package kiger.tree

import kiger.temp.Label
import kiger.temp.Temp

sealed class TreeExp {
    class BinOp(val binop: BinaryOp, val lhs: TreeExp, val rhs: TreeExp) : TreeExp()
    class Mem(val exp: TreeExp) : TreeExp()
    class Temporary(val temp: Temp) : TreeExp()
    class ESeq(val stm: TreeStm, val exp: TreeExp) : TreeExp()

    class Name(val label: Label) : TreeExp() {
        override fun equals(other: Any?) = other is Name && label == other.label
        override fun hashCode() = label.hashCode()
        override fun toString() = "Name[$label]"
    }

    class Const(val value: Int) : TreeExp() {
        override fun equals(other: Any?) = other is Const && value == other.value
        override fun hashCode() = value
        override fun toString() = "Const[$value]"
    }

    class Call(val func: TreeExp, val args: List<TreeExp>) : TreeExp()
}
