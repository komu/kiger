package kiger.ir.tree

import kiger.ir.RelOp
import kiger.temp.Label
import java.util.*

sealed class TreeStm {

    fun isConstExpr() = this is Exp && exp is TreeExp.Const

    abstract fun mapExps(f: (TreeExp) -> TreeExp): TreeStm

    class Seq(val lhs: TreeStm, val rhs: TreeStm) : TreeStm() {
        override fun mapExps(f: (TreeExp) -> TreeExp) = Seq(lhs.mapExps(f), rhs.mapExps(f))
        override fun equals(other: Any?) = other is Seq && lhs == other.lhs && rhs == other.rhs
        override fun hashCode() = Objects.hash(lhs, rhs)
        override fun toString() = "$lhs\n$rhs"
    }

    class Labeled(val label: Label) : TreeStm() {
        override fun mapExps(f: (TreeExp) -> TreeExp) = this
        override fun equals(other: Any?) = other is Labeled && label == other.label
        override fun hashCode() = Objects.hash(label)
        override fun toString() = "$label:"
    }

    sealed class Branch : TreeStm() {
        class Jump(val exp: TreeExp, val labels: List<Label>) : Branch() {
            constructor(label: Label): this(TreeExp.Name(label), listOf(label))
            override fun mapExps(f: (TreeExp) -> TreeExp) = Jump(f(exp), labels)
            override fun equals(other: Any?) = other is Jump && exp == other.exp && labels == other.labels
            override fun hashCode() = Objects.hash(exp, labels)
            override fun toString() = "    Jump[$exp, $labels]"
        }

        class CJump(val relop: RelOp, val lhs: TreeExp, val rhs: TreeExp, val trueLabel: Label, val falseLabel: Label) : Branch() {
            override fun mapExps(f: (TreeExp) -> TreeExp) = CJump(relop, f(lhs), f(rhs), trueLabel, falseLabel)
            override fun equals(other: Any?) = other is CJump && relop == other.relop && lhs == other.lhs && rhs == other.rhs && trueLabel == other.trueLabel && falseLabel == other.falseLabel
            override fun hashCode() = Objects.hash(relop, lhs, rhs, trueLabel, falseLabel)
            override fun toString() = "    CJump[$relop, $lhs, $rhs, $trueLabel, $falseLabel]"
        }
    }

    class Move(val target: TreeExp, val source: TreeExp) : TreeStm() {
        override fun mapExps(f: (TreeExp) -> TreeExp) = Move(f(target), f(source))
        override fun equals(other: Any?) = other is Move && target == other.target && source == other.source
        override fun hashCode() = Objects.hash(target, source)
        override fun toString() = "    MOVE $target, $source"
    }

    class Exp(val exp: TreeExp) : TreeStm() {
        override fun mapExps(f: (TreeExp) -> TreeExp) = Exp(f(exp))
        override fun equals(other: Any?) = other is Exp && exp == other.exp
        override fun hashCode() = exp.hashCode()
        override fun toString() = "    $exp"
    }
}
