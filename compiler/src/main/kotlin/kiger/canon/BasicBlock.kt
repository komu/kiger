package kiger.canon

import kiger.temp.Label
import kiger.tree.TreeStm

class BasicBlock(val label: Label, private val body: List<TreeStm>, val branch: TreeStm.Branch) {
    val labelledBody: Sequence<TreeStm>
        get() = sequenceOf(TreeStm.Labeled(label)) + body
    val allStatements: Sequence<TreeStm>
        get() = labelledBody + branch
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

    return builder.finish()
}

private class BasicBlockGraphBuilder {

    private val exitLabel = Label.gen()
    private val blocks = mutableListOf<BasicBlock>()
    private var currentBlock: BasicBlockBuilder? = null

    /**
     * Adds a new statement to the graph of basic blocks.
     */
    fun process(stm: TreeStm) {

        val block = currentBlock

        // Each block must start with a label. If we actually have a label in the stream,
        // we'll use that for our new block. Otherwise we'll invent a new label.
        if (block == null) {
            if (stm is TreeStm.Labeled) {
                currentBlock = BasicBlockBuilder(stm.label)
            } else {
                currentBlock = BasicBlockBuilder(Label.gen())
                process(stm) // now that we have fixed the block with our invented label, try again
            }

        } else {
            if (stm is TreeStm.Labeled) {
                // If we encounter a label, we'll split the block into two blocks, ending the
                // previous block with a jump to the new label.
                blocks += block.finishWithJump(stm.label)
                currentBlock = BasicBlockBuilder(stm.label)

            } else {
                if (stm is TreeStm.Branch) {
                    // If we encounter a branch, we must end the block and start a new one on next instruction.
                    blocks += block.finish(stm)
                    currentBlock = null

                } else {
                    block += stm
                }
            }
        }
    }

    /**
     * All input has been processed: build the graph.
     */
    fun finish(): BasicBlockGraph {
        val block = currentBlock
        if (block != null)
            // If we have a current block, it will not have a branch at the end. Add jump to exit.
            blocks += block.finishWithJump(exitLabel)

        return BasicBlockGraph(blocks, exitLabel)
    }
}

private class BasicBlockBuilder(private val label: Label) {

    private val stms = mutableListOf<TreeStm>()

    operator fun plusAssign(stm: TreeStm) {
        require(stm !is TreeStm.Branch) { "tried to add branch to middle of basic block: $stm"}
        stms += stm
    }

    fun finish(branch: TreeStm.Branch) = BasicBlock(label, stms, branch)
    fun finishWithJump(target: Label) = BasicBlock(label, stms, TreeStm.Branch.Jump(target))
}
