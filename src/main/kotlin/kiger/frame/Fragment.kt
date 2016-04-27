package kiger.frame

import kiger.canon.basicBlocks
import kiger.canon.linearize
import kiger.canon.traceSchedule
import kiger.temp.Label
import kiger.tree.TreeStm

sealed class Fragment {

    class Proc(val body: TreeStm, val frame: Frame) : Fragment() {
        override fun toString(): String {
            val code = body.linearize().basicBlocks().traceSchedule().joinToString("\n")
            return ".code\n${frame.name}:\n$code\n"
        }
    }

    class Str(val label: Label, val value: String) : Fragment() {
        override fun toString() = "$label: .asciiz \"${value.replace("\"", "\\\"")}\""
    }
}
