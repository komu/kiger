package kiger.canon

import kiger.temp.Label
import kiger.tree.TreeExp
import kiger.tree.TreeStm
import kiger.utils.cons

data class BasicBlock(val statements: List<TreeStm>) {
    init {
        require(statements.any()) { "empty basic block" }
        require(statements.first() is TreeStm.Labeled) { "basic block without start label: $statements" }
        require(statements.last().isBranch) { "basic block without ending branch: $statements" }
    }

    val label: Label
        get() = (statements.first() as TreeStm.Labeled).label

    fun isEmpty() = statements.isEmpty()
}

/**
 * A list of basic blocks along with an [exitLabel] to which the blocks will finally jump to.
 */
data class BasicBlockGraph(val blocks: List<BasicBlock>, val exitLabel: Label)

/**
 * From a list of canonical trees (provided by [linearize]], produce a list of basic blocks satisfying
 * the following properties:
 *
 * 1. No SEQ's or ESEQ's (guarantee from [linearize])
 * 2. The parent of every CALL is an EXP(..) or a MOVE(TEMP t,..) (guarantee from [linearize])
 * 3. Every block begins with a LABEL;
 * 4. A LABEL appears only at the beginning of a block;
 * 5. Any JUMP or CJUMP is the last stm in a block;
 * 6. Every block ends with a JUMP or CJUMP;
 *
 * Also produce the label to which control will be passed upon exit.
 */
fun List<TreeStm>.basicBlocks(): BasicBlockGraph {
    val builder = BasicBlockGraphBuilder()

    for (stm in this)
        builder.process(stm)

    return builder.build()
}

private class BasicBlockGraphBuilder {

    private val exitLabel = Label()
    private val blocks = mutableListOf<BasicBlock>()
    private var currentBlock: BasicBlockBuilder? = null

    /**
     * Adds a new statement to the graph of basic blocks.
     */
    fun process(stm: TreeStm) {
        // First make sure that each basic-block starts with a label
        if (currentBlock == null && stm !is TreeStm.Labeled)
            currentBlock = BasicBlockBuilder(Label())

        if (stm is TreeStm.Labeled) {
            // Labels start a new block unless we are already at the beginning of a block
            if (currentBlock != null) {
                currentBlock!!.addJumpTo(stm.label)
                finishBlock()
            }

            currentBlock = BasicBlockBuilder(stm.label)
        } else {
            currentBlock!! += stm
            if (stm.isBranch)
                finishBlock()
        }
    }

    private fun finishBlock() {
        val cb = currentBlock
        if (cb != null) {
            blocks += cb.build()
            currentBlock = null
        }
    }

    fun build(): BasicBlockGraph {
        val cb = currentBlock
        if (cb != null) {
            if (!cb.endsWithBranch())
                cb.addJumpTo(exitLabel)

            finishBlock()
        }

        return BasicBlockGraph(blocks, exitLabel)
    }
}

private class BasicBlockBuilder(private val label: Label) {

    private val stms = mutableListOf<TreeStm>()

    operator fun plusAssign(stm: TreeStm) {
        stms += stm
    }

    fun addJumpTo(label: Label) {
        stms += TreeStm.Jump(TreeExp.Name(label), listOf(label))
    }

    fun endsWithBranch() = stms.lastOrNull()?.isBranch ?: false

    fun build() = BasicBlock(cons(TreeStm.Labeled(label), stms))
}
