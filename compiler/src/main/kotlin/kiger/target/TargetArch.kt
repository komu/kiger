package kiger.target

import kiger.frame.Fragment
import kiger.frame.FrameType
import java.io.Writer

interface TargetArch {
    val frameType: FrameType
    val codeGen: CodeGen
    fun writeOutput(fragments: List<Fragment>, it: Writer)
}
