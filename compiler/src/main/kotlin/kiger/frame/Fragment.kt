package kiger.frame

import kiger.temp.Label
import kiger.tree.TreeStm

sealed class Fragment {

    class Proc(val body: TreeStm, val frame: Frame) : Fragment() {
        override fun toString(): String {
            return ".code\n${frame.name}:\n$body\n"
        }
    }

    class Str(val label: Label, val value: String) : Fragment() {
        override fun toString() = "$label: .asciiz \"${value.replace("\"", "\\\"")}\""
    }
}
