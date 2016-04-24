package kiger.tree

import kiger.temp.Label

sealed class TreeStm {
    class Seq(val lhs: TreeStm, val rhs: TreeStm) : TreeStm()
    class Labeled(val label: Label) : TreeStm()
    class Jump(val exp: TreeExp, val labels: List<Label>) : TreeStm()
    class CJump(val relop: RelOp, val lhs: TreeExp, val rhs: TreeExp, val trueLabel: Label, val falseLabel: Label) : TreeStm()
    class Move(val target: TreeExp, val source: TreeExp) : TreeStm()
    class Exp(val exp: TreeExp) : TreeStm()
}
