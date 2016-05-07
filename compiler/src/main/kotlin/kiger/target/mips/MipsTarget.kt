package kiger.target.mips

import kiger.emitFragments
import kiger.frame.Fragment
import kiger.target.CodeGen
import kiger.target.TargetArch
import java.io.Writer

object MipsTarget : TargetArch {
    override val frameType = MipsFrame
    override val codeGen: CodeGen = MipsGen

    override fun writeOutput(fragments: List<Fragment>, it: Writer) {
        val runtime = javaClass.classLoader.getResourceAsStream("mips-runtime.s")?.use { it.reader().readText() } ?: error("could not load mips-runtime.s")

        it.emitFragments(codeGen, fragments)
        it.write(runtime)
    }
}
