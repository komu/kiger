package kiger.vm

sealed class Inst {

    class Label(val name: String): Inst() {
        override fun toString() = "$name:"
    }

    class Data(val text: String) : Inst() {
        override fun toString() = text
    }

    sealed class Op : Inst() {
        class Op0(val name: String) : Op() {
            override fun toString() = "    $name"
        }

        class Op1(val name: String, val o1: Operand) : Op() {
            override fun toString() = "    $name $o1"
        }

        class Op2(val name: String, val o1: Operand, val o2: Operand) : Op() {
            override fun toString() = "    $name $o1, $o2"
        }

        class Op3(val name: String, val o1: Operand, val o2: Operand, val o3: Operand) : Op() {
            override fun toString() = "    $name $o1, $o2, $o3"
        }
    }
}

sealed class Operand {

    open val reg: String
        get() = error("no register for operand $this")

    open val offset: Int
        get() = error("no offset for operand $this")

    open val baseReg: String
        get() = error("no base-reg for operand $this")

    open fun immediate(labelMap: Map<String,Int>): Int =
        error("no immediate value for operand $this of type ${javaClass.name}")

    class Reg(override val reg: String) : Operand() {
        override fun toString() = reg
        override fun equals(other: Any?) = other is Reg && reg == other.reg
        override fun hashCode() = reg.hashCode()
    }

    class Offset(override val offset: Int, override val baseReg: String) : Operand() {
        override fun toString() = "$offset($baseReg)"
    }

    class Immediate(val value: Int) : Operand() {
        override fun immediate(labelMap: Map<String, Int>) = value
        override fun toString() = value.toString()
    }

    class LabelRef(val label: String) : Operand() {
        override fun immediate(labelMap: Map<String, Int>) = labelMap[label] ?: error("unknown label $label")
        override fun toString() = label
    }
}

fun List<String>.parseInstructions(): List<Inst> =
    asSequence().map { parseInstruction(it) }.filterNotNull().toList()

private fun String.stripComments(): String {
    val i = indexOf('#')
    return if (i != -1) substring(0, i - 1).trim() else trim()
}

private val labelNameRegex = Regex("""[a-zA-Z_][a-zA-Z_0-9]*""")
private val labelDefRegex = Regex("""($labelNameRegex):""")
private val registerRegex = Regex("""\$[a-z]+\d*""")
private val immediateRegex = Regex("""-?\d+""")
private val offsetRegex = Regex("""($immediateRegex)\(($registerRegex)\)""")
private val operandRegex = Regex("""$registerRegex|$offsetRegex|$labelNameRegex|$immediateRegex""")
private val opNameRegex = Regex("""\w+""")
private val op1Regex = Regex("""($opNameRegex) ($operandRegex)""")
private val op2Regex = Regex("""($opNameRegex) ($operandRegex), ($operandRegex)""")
private val op3Regex = Regex("""($opNameRegex) ($operandRegex), ($operandRegex), ($operandRegex)""")
private val asciiZRegex = Regex("""\.asciiz "(.+)"""")

private fun parseInstruction(ss: String): Inst? {
    val s = ss.stripComments()
    if (s.isEmpty()) return null

    val labelMatch = labelDefRegex.matchEntire(s)
    if (labelMatch != null)
        return Inst.Label(labelMatch.groupValues[1])

    if (opNameRegex.matches(s))
        return Inst.Op.Op0(s)

    val op1Match = op1Regex.matchEntire(s)
    if (op1Match != null)
        return Inst.Op.Op1(op1Match.groupValues[1], parseOperand(op1Match.groupValues[2]))

    val op2Match = op2Regex.matchEntire(s)
    if (op2Match != null)
        return Inst.Op.Op2(op2Match.groupValues[1], parseOperand(op2Match.groupValues[2]), parseOperand(op2Match.groupValues[5]))

    val op3Match = op3Regex.matchEntire(s)
    if (op3Match != null)
        return Inst.Op.Op3(op3Match.groupValues[1], parseOperand(op3Match.groupValues[2]), parseOperand(op3Match.groupValues[5]), parseOperand(op3Match.groupValues[8]))

    if (s == ".text" || s == ".data")
        return null

    val asciiZMatch = asciiZRegex.matchEntire(s)
    if (asciiZMatch != null)
        return Inst.Data(parseAsciiZText(asciiZMatch))

    error("invalid instruction format '$s'")
}

private fun parseOperand(s: String): Operand {
    if (s.matches(registerRegex))
        return Operand.Reg(s)

    if (s.matches(labelNameRegex))
        return Operand.LabelRef(s)

    if (s.matches(immediateRegex))
        return Operand.Immediate(s.toInt())

    val offsetMatch = offsetRegex.matchEntire(s)
    if (offsetMatch != null)
        return Operand.Offset(offsetMatch.groupValues[1].toInt(), offsetMatch.groupValues[2])

    else error("operand '$s'")
}

private fun parseAsciiZText(asciiZMatch: MatchResult) =
    asciiZMatch.groupValues[1].replace("\\n", "\n")
