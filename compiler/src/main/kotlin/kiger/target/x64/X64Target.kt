package kiger.target.x64

import kiger.emitFragments
import kiger.frame.Fragment
import kiger.target.TargetArch
import java.io.Writer

object X64Target : TargetArch {
    override val frameType = X64Frame
    override val codeGen = X64Gen

    override fun writeOutput(fragments: List<Fragment>, it: Writer) {
        val runtime = javaClass.classLoader.getResourceAsStream("x64-runtime.s")?.use { it.reader().readText() } ?: error("could not load x64-runtime.s")

        it.emitFragments(codeGen, fragments)
        it.write(runtime)
    }
}
