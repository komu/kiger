package kiger.regalloc

sealed class IStatus {

    object Removed : IStatus() {
        override fun toString() = "Removed"
    }

    class InGraph(val degree: Int) : IStatus() {
        override fun toString() = "InGraph($degree)"
    }

    class Colored(val color: String) : IStatus() {
        override fun toString() = "Colored($color)"
    }
}
