package kiger.ir.tree

import kiger.temp.Label

data class TreeControlFlowGraph(val blocks: List<TreeBasicBlock>, val exitLabel: Label)

class TreeBasicBlock(val label: Label, private val body: List<TreeStm>, val branch: TreeStm.Branch) {
    val labelledBody: Sequence<TreeStm>
        get() = sequenceOf(TreeStm.Labeled(label)) + body
    val allStatements: Sequence<TreeStm>
        get() = labelledBody + branch
}

