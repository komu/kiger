package kiger.frame

import kiger.temp.Temp
import kiger.tree.TreeExp

class Frame {

    companion object {
        val FP = Temp()
        val wordSize = 4

        fun exp(access: FrameAccess, exp: TreeExp): TreeExp {
            TODO()
        }
    }

    val formals: List<FrameAccess>
        get() = TODO()
}

