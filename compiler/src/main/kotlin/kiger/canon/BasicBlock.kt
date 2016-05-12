package kiger.canon

import kiger.ir.quad.Quad
import kiger.temp.Label

class BasicBlock(val label: Label, private val body: List<Quad>, val branch: Quad) {
    init {
        require(branch.isJump)
    }
    val labelledBody: Sequence<Quad>
        get() = sequenceOf(Quad.Labeled(label)) + body
    val allStatements: Sequence<Quad>
        get() = labelledBody + branch
}

/**
 * A list of basic blocks along with an [exitLabel] to which the blocks will finally jump to.
 */
data class ControlFlowGraph(val blocks: List<BasicBlock>, val exitLabel: Label)

fun List<Quad>.createControlFlowGraph(): ControlFlowGraph {
    val builder = ControlFlowGraphBuilder()

    for (stm in this)
        builder.process(stm)

    return builder.finish()
}

private class ControlFlowGraphBuilder {

    private val exitLabel = Label.gen("exit")
    private val blocks = mutableListOf<BasicBlock>()
    private var currentBlock: BasicBlockBuilder? = null

    /**
     * Adds a new statement to the graph of basic blocks.
     */
    fun process(stm: Quad) {

        val block = currentBlock

        // Each block must start with a label. If we actually have a label in the stream,
        // we'll use that for our new block. Otherwise we'll invent a new label.
        if (block == null) {
            if (stm is Quad.Labeled) {
                currentBlock = BasicBlockBuilder(stm.label)
            } else {
                currentBlock = BasicBlockBuilder(Label.gen("dummy"))
                process(stm) // now that we have fixed the block with our invented label, try again
            }

        } else {
            if (stm is Quad.Labeled) {
                // If we encounter a label, we'll split the block into two blocks, ending the
                // previous block with a jump to the new label.
                blocks += block.finishWithJump(stm.label)
                currentBlock = BasicBlockBuilder(stm.label)

            } else {
                if (stm.isJump) {
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
    fun finish(): ControlFlowGraph {
        val block = currentBlock
        if (block != null)
            // If we have a current block, it will not have a branch at the end. Add jump to exit.
            blocks += block.finishWithJump(exitLabel)

        return ControlFlowGraph(blocks, exitLabel)
    }
}

private class BasicBlockBuilder(private val label: Label) {

    private val stms = mutableListOf<Quad>()

    operator fun plusAssign(stm: Quad) {
        require(!stm.isJump) { "tried to add a jump to middle of basic block: $stm"}
        stms += stm
    }

    fun finish(branch: Quad) = BasicBlock(label, stms, branch)
    fun finishWithJump(target: Label) = BasicBlock(label, stms, Quad.Jump(target))
}
