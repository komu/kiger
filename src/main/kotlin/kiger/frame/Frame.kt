package kiger.frame

import kiger.temp.Temp
import kiger.tree.TreeExp

class Frame {

    companion object {
        val FP = Temp()

        fun exp(access: FrameAccess, exp: TreeExp): TreeExp {
            TODO()
        }
    }

    val formals: FrameAccess
        get() = TODO()
}

