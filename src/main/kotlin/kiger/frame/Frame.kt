package kiger.frame

import kiger.temp.Label
import kiger.temp.Temp
import kiger.translate.Access
import kiger.tree.TreeExp
import kiger.tree.TreeStm

class Frame(val name: Label, private val formalEscapes: List<Boolean>) {

    companion object {
        val FP = Temp()
        val RV = Temp()
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

    fun procEntryExit1(body: TreeStm): TreeStm {
        TODO()
    }
}

