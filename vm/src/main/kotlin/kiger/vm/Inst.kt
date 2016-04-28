package kiger.vm

sealed class Inst {

    class Label(val name: String): Inst() {
        override fun toString() = "$name:"
    }

    class Pseudo(val name: String) : Inst() {
        override fun toString() = "    .$name"
    }

    sealed class Op : Inst() {
        class Op0(val name: String) : Op() {
            override fun toString() = "    $name"
        }

        class Op1(val name: String, val a1: Operand) : Op() {
            override fun toString() = "    $name $a1"
        }

        class Op2(val name: String, val a1: Operand, val a2: Operand) : Op() {
            override fun toString() = "    $name $a1, $a2"
        }

        class Op3(val name: String, val a1: Operand, val a2: Operand, val a3: Operand) : Op() {
            override fun toString() = "    $name $a1, $a2, $a3"
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
    asSequence().map { it.stripComments() }.filterNot { it == "" }.map { parseInstruction(it) }.toList()

private fun String.stripComments(): String {
    val i = indexOf('#')
    return if (i != -1) substring(0, i - 1).trim() else trim()
}

private val labelNameRegex = Regex("""[a-zA-Z_]+""")
private val labelDefRegex = Regex("""($labelNameRegex):""")
private val registerRegex = Regex("""\$[a-z]+\d?""")
private val immediateRegex = Regex("""\d+""")
private val offsetRegex = Regex("""(\d+)\(($registerRegex)\)""")
private val operandRegex = Regex("""($registerRegex|$offsetRegex|$labelNameRegex|$immediateRegex)""")
private val opNameRegex = Regex("""\w+""")
private val op1Regex = Regex("""($opNameRegex) $operandRegex""")
private val op2Regex = Regex("""($opNameRegex) $operandRegex, $operandRegex""")
private val op3Regex = Regex("""($opNameRegex) $operandRegex, $operandRegex, $operandRegex""")

private fun parseInstruction(s: String): Inst {
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

    if (s.startsWith("."))
        return Inst.Pseudo(s.substring(1))

    error("unknown instruction '$s'")
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
