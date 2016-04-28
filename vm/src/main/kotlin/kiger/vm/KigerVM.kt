package kiger.vm

import java.io.File

fun main(args: Array<String>) {
    val insts = File("output.s").readLines().parseInstructions()

    for (inst in insts) {
        println(inst)
    }
}
