package kiger.translate

import kiger.frame.Frame

sealed class Level {
    object Top : Level()
    class Lev(val parent: Level, val frame: Frame) : Level()
}
