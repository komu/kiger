package kiger.frame

import kiger.temp.Label
import kiger.tree.TreeStm

abstract class Frame(val name: Label) {

    abstract val formals: List<FrameAccess>

    abstract fun allocLocal(escape: Boolean): FrameAccess

    /**
     * Move each incoming register parameter to its correct place in the function.
     */
    abstract fun procEntryExit1(body: TreeStm): TreeStm
}

