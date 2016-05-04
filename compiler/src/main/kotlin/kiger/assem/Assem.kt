package kiger.assem

import kiger.temp.Label
import kiger.temp.Temp

private val registerRegexN = Regex("'([sdj])(\\d+)")

sealed class Instr {

    open val isJump: Boolean
        get() = false

    abstract fun format(func: (Temp) -> String): String

    class Lbl(val assem: String, val label: Label) : Instr() {
        init {
            require(assem.any())
        }
        override fun toString() = assem
        override fun format(func: (Temp) -> String) = assem
    }

    class Oper(val assem: String, val dst: List<Temp> = emptyList(), val src: List<Temp> = emptyList(), val jump: List<Label>? = null) : Instr() {
        override val isJump: Boolean
            get() = jump != null

        override fun format(func: (Temp) -> String): String {
            val op = assem.replacePlaceholders(func, dst, src, jump)
            return "    $op"
        }

        override fun toString() = format { it.name }
    }

    class Move(val assem: String, val dst: Temp, val src: Temp) : Instr() {
        init {
            require(assem.any())
        }

        override fun toString() = format { it.name }
        override fun format(func: (Temp) -> String): String {
            val op = assem.replacePlaceholders(func, listOf(dst), listOf(src), null)
            return "    $op"
        }
    }
}

private fun String.replacePlaceholders(func: (Temp) -> String, dst: List<Temp>, src: List<Temp>, jump: List<Label>?): String =
    registerRegexN.replace(this) { m ->
        val type = m.groupValues[1]
        val index = m.groupValues[2].toInt()
        when (type) {
            "s" -> func(src[index])
            "d" -> func(dst[index])
            "j" -> jump!![index].name
            else -> error("invalid type '$type'")
        }
    }
