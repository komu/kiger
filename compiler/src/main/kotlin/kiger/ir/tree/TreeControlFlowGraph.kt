package kiger.ir.tree

import kiger.temp.Label

data class TreeControlFlowGraph(val blocks: List<TreeBasicBlock>, val exitLabel: Label) {
    fun toStatementList(): List<TreeStm> =
        (blocks.asSequence().flatMap { it.toStatements() } + TreeStm.Labeled(exitLabel)).toList()
}

class TreeBasicBlock(val label: Label, val body: List<TreeStm>, val branch: TreeStm.Branch) {
    fun toStatements(): Sequence<TreeStm> =
        sequenceOf(TreeStm.Labeled(label)) + body + branch
}

