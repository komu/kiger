package kiger.vm

import java.io.File

fun main(args: Array<String>) {
    val insts = File(args[0]).readLines().parseInstructions()

    val evaluator = Evaluator(insts)

    evaluator.run()
}
