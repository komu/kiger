package kiger.translate

import kiger.target.Frame

sealed class Level {
    abstract val parent: Level?
    abstract val depth: Int

    object Top : Level() {
        override val parent = null
        override val depth = 0

        override fun toString() = "Top"
    }

    class Lev(override val parent: Level, val frame: Frame) : Level() {
        override val depth: Int
            get() = parent.depth + 1

        override fun toString() = "Lev[depth=$depth, name=${frame.name}]"
    }
}
