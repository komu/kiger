package kiger.vm

sealed class Inst {
    class Label(val name: String): Inst() {
        override fun toString() = "$name:"
    }

    class Op0(val name: String) : Inst() {
        override fun toString() = "    $name"
    }

    class Op1(val name: String, val a1: Operand) : Inst() {
        override fun toString() = "    $name $a1"
    }

    class Op2(val name: String, val a1: Operand, val a2: Operand) : Inst() {
        override fun toString() = "    $name $a1, $a2"
    }

    class Op3(val name: String, val a1: Operand, val a2: Operand, val a3: Operand) : Inst() {
        override fun toString() = "    $name $a1, $a2, $a3"
    }
}

sealed class Operand {
    class Reg(val reg: String) : Operand() {
        override fun toString() = reg
    }

    class Offset(val offset: Int, val reg: String) : Operand() {
        override fun toString() = "$offset($reg)"
    }

    class Immediate(val value: Int) : Operand() {
        override fun toString() = value.toString()
    }

    class LabelRef(val label: String) : Operand() {
        override fun toString() = label
    }
}

fun List<String>.parseInstructions(): List<Inst> =
    asSequence().map { it.stripComments() }.filterNot { it == "" }.map { parseInstruction(it) }.toList()

private fun String.stripComments(): String {
    val i = indexOf('#')
    return if (i != -1) substring(0, i - 1).trim() else trim()
}

private val labelNameRegex = Regex("""\w+""")
private val labelDefRegex = Regex("""($labelNameRegex):""")
private val registerRegex = Regex("""\$[a-z]+\d?""")
private val immediateRegex = Regex("""\d+""")
private val offsetRegex = Regex("""(\d+)\(($registerRegex)\)""")
private val operandRegex = Regex("""($registerRegex|$offsetRegex|$labelNameRegex)""")
private val opNameRegex = Regex("""\w+""")
private val op1Regex = Regex("""($opNameRegex) $operandRegex""")
private val op2Regex = Regex("""($opNameRegex) $operandRegex, $operandRegex""")
private val op3Regex = Regex("""($opNameRegex) $operandRegex, $operandRegex, $operandRegex""")

private fun parseInstruction(s: String): Inst {
    val labelMatch = labelDefRegex.matchEntire(s)
    if (labelMatch != null)
        return Inst.Label(labelMatch.groupValues[1])

    if (opNameRegex.matches(s))
        return Inst.Op0(s)

    val op1Match = op1Regex.matchEntire(s)
    if (op1Match != null)
        return Inst.Op1(op1Match.groupValues[1], parseOperand(op1Match.groupValues[2]))

    val op2Match = op2Regex.matchEntire(s)
    if (op2Match != null)
        return Inst.Op2(op2Match.groupValues[1], parseOperand(op2Match.groupValues[2]), parseOperand(op2Match.groupValues[5]))

    val op3Match = op3Regex.matchEntire(s)
    if (op3Match != null)
        return Inst.Op3(op3Match.groupValues[1], parseOperand(op3Match.groupValues[2]), parseOperand(op3Match.groupValues[5]), parseOperand(op3Match.groupValues[8]))

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
