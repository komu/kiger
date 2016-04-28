package kiger.vm

import java.io.File

fun main(args: Array<String>) {
    val insts = File("asm-examples/square.s").readLines().parseInstructions()

    val evaluator = Evaluator(insts)

    evaluator.run()
}
