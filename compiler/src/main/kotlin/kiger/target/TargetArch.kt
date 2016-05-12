package kiger.target

import java.io.Writer

interface TargetArch {
    val frameType: FrameType
    val codeGen: CodeGen
    fun writeOutput(fragments: List<Fragment>, it: Writer)
}
