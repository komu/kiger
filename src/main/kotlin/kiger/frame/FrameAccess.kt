package kiger.frame

import kiger.temp.Temp

sealed class FrameAccess {
    class InFrame(val offset: Int) : FrameAccess() {
        override fun toString() = "InFrame[$offset]"
    }
    class InReg(val reg: Temp) : FrameAccess() {
        override fun toString() = "InReg[$reg]"
    }
}
