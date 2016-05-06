package kiger.target

import kiger.frame.FrameType

interface TargetArch {
    val frameType: FrameType
    val codeGen: CodeGen
}
