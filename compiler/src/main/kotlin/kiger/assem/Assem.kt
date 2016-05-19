package kiger.assem

import kiger.temp.Label
import kiger.temp.Temp

private val registerRegexN = Regex("'([sdj])(\\d+)")

sealed class Instr(val comment: String?) {

    open val isJump: Boolean
        get() = false

    abstract val uses: Set<Temp>
    abstract val defs: Set<Temp>

    fun references(t: Temp) = t in uses || t in defs

    override fun toString() = format { it.name }

    fun format(func: (Temp) -> String): String {
        val op = format1(func)
        return if (comment == null)
            op
        else
            "${op.padEnd(30)} # $comment"
    }

    protected abstract fun format1(func: (Temp) -> String): String

    class Lbl(val assem: String, val label: Label, comment: String? = null) : Instr(comment) {
        init {
            require(assem.any())
        }

        override val uses = emptySet<Temp>()
        override val defs = emptySet<Temp>()

        override fun format1(func: (Temp) -> String) = assem
    }

    class Oper(val assem: String, val dst: List<Temp> = emptyList(), val src: List<Temp> = emptyList(), val jump: List<Label>? = null, comment: String? = null) : Instr(comment) {
        override val isJump: Boolean
            get() = jump != null

        fun rewriteRegisters(newDst: List<Temp>, newSrc: List<Temp>) = Oper(assem, newDst, newSrc, jump, comment)

        override val uses = src.toSet()
        override val defs = dst.toSet()

        override fun format1(func: (Temp) -> String): String {
            val op = assem.replacePlaceholders(func, dst, src, jump)
            return "    $op"
        }
    }

    class Move(val assem: String, val dst: Temp, val src: Temp, comment: String? = null) : Instr(comment) {
        init {
            require(assem.any())
        }

        override val uses = setOf(src)
        override val defs = setOf(dst)

        fun rewriteRegisters(newDst: Temp, newSrc: Temp) = Move(assem, newDst, newSrc, comment)

        override fun format1(func: (Temp) -> String): String {
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
