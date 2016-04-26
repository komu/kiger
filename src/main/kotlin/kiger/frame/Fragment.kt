package kiger.frame

import kiger.canon.linearize
import kiger.temp.Label
import kiger.tree.TreeStm

sealed class Fragment {
    class Proc(val body: TreeStm, val frame: Frame) : Fragment() {
        override fun toString(): String {
            val code = body.linearize().joinToString("\n") { if (it is TreeStm.Labeled) "${it.label}:" else "    $it" }
            return ".code\n${frame.name}:\n$code\n"
        }
    }
    class Str(val label: Label, val value: String) : Fragment() {
        override fun toString() = ".data $label: \"${value.replace("\"", "\\\"")}\""
    }
}
