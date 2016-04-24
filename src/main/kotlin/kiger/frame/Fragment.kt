package kiger.frame

import kiger.temp.Label
import kiger.tree.TreeStm

sealed class Fragment {
    class Proc(val body: TreeStm, val frame: Frame) : Fragment()
    class Str(val label: Label, val value: String) : Fragment()
}
