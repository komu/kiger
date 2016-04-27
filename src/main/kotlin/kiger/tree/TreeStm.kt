package kiger.tree

import kiger.temp.Label
import java.util.*

sealed class TreeStm {

    val isBranch = this is Jump || this is CJump

    class Seq(val lhs: TreeStm, val rhs: TreeStm) : TreeStm() {
        override fun equals(other: Any?) = other is Seq && lhs == other.lhs && rhs == other.rhs
        override fun hashCode() = Objects.hash(lhs, rhs)
        override fun toString() = "$lhs\n$rhs"
    }

    class Labeled(val label: Label) : TreeStm() {
        override fun equals(other: Any?) = other is Labeled && label == other.label
        override fun hashCode() = Objects.hash(label)
        override fun toString() = "$label:"
    }

    class Jump(val exp: TreeExp, val labels: List<Label>) : TreeStm() {
        override fun equals(other: Any?) = other is Jump && exp == other.exp && labels == other.labels
        override fun hashCode() = Objects.hash(exp, labels)
        override fun toString() = "    Jump[$exp, $labels]"
    }

    class CJump(val relop: RelOp, val lhs: TreeExp, val rhs: TreeExp, val trueLabel: Label, val falseLabel: Label) : TreeStm() {
        override fun equals(other: Any?) = other is CJump && relop == other.relop && lhs == other.lhs && rhs == other.rhs && trueLabel == other.trueLabel && falseLabel == other.falseLabel
        override fun hashCode() = Objects.hash(relop, lhs, rhs, trueLabel, falseLabel)
        override fun toString() = "    CJump[$relop, $lhs, $rhs, $trueLabel, $falseLabel]"
    }

    class Move(val target: TreeExp, val source: TreeExp) : TreeStm() {
        override fun equals(other: Any?) = other is Move && target == other.target && source == other.source
        override fun hashCode() = Objects.hash(target, source)
        override fun toString() = "    MOVE $target, $source"
    }

    class Exp(val exp: TreeExp) : TreeStm() {
        override fun equals(other: Any?) = other is Exp && exp == other.exp
        override fun hashCode() = exp.hashCode()
        override fun toString() = "    $exp"
    }
}
