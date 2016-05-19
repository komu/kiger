package kiger.ir.tree

import kiger.temp.Label

data class TreeControlFlowGraph(val blocks: List<TreeBasicBlock>, val exitLabel: Label)

class TreeBasicBlock(val label: Label, val body: List<TreeStm>, val branch: TreeStm.Branch) {
    val allStatements: List<TreeStm>
        get() = body + branch
}

