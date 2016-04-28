package kiger.tree

import kiger.temp.Label
import kiger.temp.Temp
import java.util.*

sealed class TreeExp {

    class BinOp(val binop: BinaryOp, val lhs: TreeExp, val rhs: TreeExp) : TreeExp() {
        override fun equals(other: Any?) = other is BinOp && binop == other.binop && lhs == other.lhs && rhs == other.rhs
        override fun hashCode() = Objects.hash(binop, lhs, rhs)
        override fun toString() = "($lhs $binop $rhs)"
    }

    class Mem(val exp: TreeExp) : TreeExp() {
        override fun equals(other: Any?) = other is Mem && exp == other.exp
        override fun hashCode() = exp.hashCode()
        override fun toString() = "Mem[$exp]"
    }

    class Temporary(val temp: Temp) : TreeExp() {
        override fun equals(other: Any?) = other is Temporary && temp == other.temp
        override fun hashCode() = temp.hashCode()
        override fun toString() = temp.toString()
    }

    class ESeq(val stm: TreeStm, val exp: TreeExp) : TreeExp() {
        override fun equals(other: Any?) = other is ESeq && stm == other.stm && exp == other.exp
        override fun hashCode() = Objects.hash(stm, exp)
        override fun toString() = "ESeq[$stm, $exp]"
    }

    class Name(val label: Label) : TreeExp() {
        override fun equals(other: Any?) = other is Name && label == other.label
        override fun hashCode() = label.hashCode()
        override fun toString() = "Name[$label]"
    }

    class Const(val value: Int) : TreeExp() {
        override fun equals(other: Any?) = other is Const && value == other.value
        override fun hashCode() = value
        override fun toString() = value.toString()
    }

    class Call(val func: TreeExp, val args: List<TreeExp>) : TreeExp() {
        override fun equals(other: Any?) = other is Call && func == other.func && args == other.args
        override fun hashCode() = Objects.hash(func, args)
        override fun toString() = "Call[$func, $args]"
    }
}
