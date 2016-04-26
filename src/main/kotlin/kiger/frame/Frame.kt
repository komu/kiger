package kiger.frame

import kiger.temp.Temp
import kiger.translate.Access
import kiger.tree.TreeExp

class Frame {

    companion object {
        val FP = Temp()
        val wordSize = 4

        fun exp(access: FrameAccess, exp: TreeExp): TreeExp {
            TODO()
        }

        fun externalCall(name: String, args: List<TreeExp>): TreeExp {
            TODO()
        }
    }

    val formals: List<FrameAccess>
        get() = TODO()

    fun allocLocal(escape: Boolean): Access =
        TODO()
}

