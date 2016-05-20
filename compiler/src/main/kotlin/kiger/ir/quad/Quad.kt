package kiger.ir.quad

import kiger.ir.BinaryOp
import kiger.ir.RelOp
import kiger.temp.Label
import kiger.temp.Temp
import java.util.*

sealed class Quad {

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
    abstract override fun toString(): String
    open val isJump: Boolean
        get() = false

    open val jumpLabels: Collection<Label>? = null

    class BinOp(val op: BinaryOp, val target: Temp, val lhs: QExp, val rhs: QExp) : Quad() {
        override fun equals(other: Any?) = other is BinOp && op == other.op && target == other.target && lhs == other.lhs && rhs == other.rhs
        override fun hashCode() = Objects.hash(op, target, lhs, rhs)
        override fun toString() = "$target <- $lhs $op $rhs"
    }

    class Labeled(val label: Label) : Quad() {
        override fun equals(other: Any?) = other is Labeled && label == other.label
        override fun hashCode() = label.hashCode()
        override fun toString() = "$label:"
    }

    class Move(val target: Temp, val source: QExp) : Quad() {
        override fun equals(other: Any?) = other is Move && target == other.target && source == other.source
        override fun hashCode() = Objects.hash(target, source)
        override fun toString() = "$target <- $source"
    }

    class Jump(val target: QExp, val labels: List<Label>) : Quad() {
        constructor(label: Label): this(QExp.Name(label), listOf(label))
        override fun equals(other: Any?) = other is Jump && target == other.target && labels == other.labels
        override fun hashCode() = Objects.hash(target, labels)
        override fun toString() = "jump $target ; labels"
        override val isJump: Boolean
            get() = true
        override val jumpLabels: Collection<Label>
            get() = labels
    }

    class CJump(val op: RelOp, val lhs: QExp, val rhs: QExp, val trueLabel: Label, val falseLabel: Label) : Quad() {
        override fun equals(other: Any?) = other is CJump && op == other.op && lhs == other.lhs && rhs == other.rhs && trueLabel == other.trueLabel && falseLabel == other.falseLabel
        override fun hashCode() = Objects.hash(op, lhs, rhs, trueLabel, falseLabel)
        override fun toString() = "if ($lhs $op $rhs) jump $trueLabel else jump $falseLabel"
        override val isJump: Boolean
            get() = true
        override val jumpLabels: Collection<Label>
            get() = setOf(trueLabel, falseLabel)
    }

    class Call(val func: QExp, val args: List<QExp>, val result: Temp?) : Quad() {
        override fun equals(other: Any?) = other is Call && func == other.func && args == other.args && result == other.result
        override fun hashCode() = Objects.hash(func, args, result)
        override fun toString() = (if (result != null) "$result <- " else "") + "$func(${args.joinToString(", ")})"
    }

    class Load(val target: Temp, val address: QExp) : Quad() {
        override fun equals(other: Any?) = other is Load && target == other.target && address == other.address
        override fun hashCode() = Objects.hash(target, address)
        override fun toString() = "$target <- mem[$address]"
    }

    class Store(val address: QExp, val value: QExp) : Quad() {
        override fun equals(other: Any?) = other is Store && address == other.address && value == other.value
        override fun hashCode() = Objects.hash(address, value)
        override fun toString() = "mem[$address] = $value"
    }
}

sealed class QExp {
    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int
    abstract override fun toString(): String

    class Temporary(val temp: Temp) : QExp() {
        override fun equals(other: Any?) = other is Temporary && temp == other.temp
        override fun hashCode() = temp.hashCode()
        override fun toString() = temp.toString()
    }

    class Const(val value: Int) : QExp() {
        override fun equals(other: Any?) = other is Const && value == other.value
        override fun hashCode() = value
        override fun toString() = value.toString()
    }

    class Name(val name: Label) : QExp() {
        override fun equals(other: Any?) = other is Name && name == other.name
        override fun hashCode() = name.hashCode()
        override fun toString() = name.toString()
    }
}
