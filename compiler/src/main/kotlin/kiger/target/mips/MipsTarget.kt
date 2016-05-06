package kiger.target.mips

import kiger.target.CodeGen
import kiger.target.TargetArch

object MipsTarget : TargetArch {
    override val frameType = MipsFrame
    override val codeGen: CodeGen = MipsGen
}
