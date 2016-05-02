package kiger.regalloc

sealed class IStatus {

    open val degree: Int
        get() = error("can't call degree on $this")

    object Removed : IStatus() {
        override fun toString() = "Removed"
    }

    class InGraph(override val degree: Int) : IStatus() {
        override fun toString() = "InGraph($degree)"
    }

    class Colored(val color: String) : IStatus() {
        override fun toString() = "Colored($color)"
    }
}
