package kiger.vm

import kiger.vm.Inst.Op.*

/**
 * http://www.mrc.uidaho.edu/mrc/people/jff/digital/MIPSir.html
 */
class Evaluator(allInstructions: List<Inst>) {

    val regs = Registers()
    val PC_EXIT = -1
    var pc = PC_EXIT

    val instructions = mutableListOf<Instruction>()
    val originalInstructions = mutableListOf<Inst>()
    val labelMap = mutableMapOf<String, Int>()

    var trace = false
    var singleStep = false

    val mem = Array(1024 * 1024) { 0 }

    init {
        for (inst in allInstructions) {
            if (inst is Inst.Label) {
                labelMap[inst.name] = instructions.size
            } else {
                originalInstructions += inst
                instructions += when (inst) {
                    is Op0 -> inst.analyze()
                    is Op1 -> inst.analyze(regs)
                    is Op2 -> inst.analyze(regs)
                    is Op3 -> inst.analyze(regs)
                    else   -> error("unknown instruction: $inst")
                }
            }
        }

        regs.ra.value = PC_EXIT
        regs.fp.value = mem.size - 1000
        regs.sp.value = mem.size - 1000
    }

    fun run() {
        pc = labelMap["main"] ?: error("could not find main label")

        while (pc != PC_EXIT) {
            step()
        }

        println("v0: ${regs.v0.value}")
    }

    fun step() {
        if (trace)
            println("$pc: ${instructions[pc].toString().padEnd(30)} $regs")

        if (singleStep)
            System.`in`?.read()

        val oldPc = pc
        try {
            pc++
            instructions[oldPc].eval(this)
        } catch (e: Exception) {
            System.err?.println("Exception when evaluating ${instructions[oldPc]}")
            e.printStackTrace()
            pc = PC_EXIT
        }
    }

    fun syscall() {
        val call = regs.v0.value
        when (call) {
            1   -> System.out.print(regs.a1.value)
            4   -> System.out.print((originalInstructions[regs.a1.value] as Inst.Data).text)
            10  -> pc = PC_EXIT
            else -> error("unknown syscall $call")
        }
    }

    val Operand.immediate: Int
        get() = immediate(labelMap)
}

private fun Op0.analyze(): Instruction =
    when (name) {
        "syscall"   -> Instruction(this) { syscall() }
        else        -> error("Unsupported op: $this")
    }

private fun Op1.analyze(regs: Registers): Instruction =
    when (name) {
        "j"     -> Instruction(this) { pc = o1.immediate }
        "jr"    -> { val r = regs[o1.reg]; Instruction(this) { pc = r.value } }
        "jal"   -> Instruction(this) {
            regs.ra.value = pc
            pc = o1.immediate
        }
        "jalr"   -> {
            val r = regs[o1.reg]
            Instruction(this) {
                regs.ra.value = pc
                pc = r.value
            }
        }
        else -> error("Unsupported op: $this")
    }

private fun Op2.analyze(regs: Registers): Instruction =
    when (name) {
        "move"  -> { val d = regs[o1.reg]; val s = regs[o2.reg]; Instruction(this) { d.value = s.value } }
        "lw"    -> { val d = regs[o1.reg]; val b = regs[o2.baseReg]; val o = o2.offset; Instruction(this) { d.value = mem[b.value + o] } }
        "sw"    -> { val b = regs[o2.baseReg]; val o = o2.offset; val s = regs[o1.reg]; Instruction(this) { mem[b.value + o] = s.value } }
        "li"    -> { val d = regs[o1.reg]; Instruction(this) { d.value = o2.immediate } }
        "la"    -> { val d = regs[o1.reg]; Instruction(this) { d.value = o2.immediate } }
        else    -> error("Unsupported op: $this")
    }

private fun Op3.analyze(regs: Registers): Instruction =
    when (name) {
        "add"   -> { val d = regs[o1.reg]; val l = regs[o2.reg]; val r = regs[o3.reg]; Instruction(this) { d.value = l.value + r.value } }
        "addi",
        "addiu" -> { val d = regs[o1.reg]; val l = regs[o2.reg]; Instruction(this) { d.value = l.value + o3.immediate } }
        "mul"   -> { val d = regs[o1.reg]; val l = regs[o2.reg]; val r = regs[o3.reg]; Instruction(this) { d.value = l.value * r.value } }
        "bgez"  -> { val l = regs[o1.reg]; val r = regs[o2.reg]; Instruction(this) { if (l.value >= r.value) pc = o3.immediate } }
        else -> error("Unsupported op: $this")
    }

class Instruction(val inst: Inst, val eval: Evaluator.() -> Unit) {
    override fun toString() = inst.toString()
}

class Register(val name: String, var value: Int = 0) {
    override fun toString() = "$name: $value"
}

class Registers {

    private val regs = listOf("zero", "at", "v0", "v1", "a0", "a1", "a2", "a3",
            "t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7",
            "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7",
            "t8", "t9", "k0", "k1", "gp", "sp", "fp", "ra")
            .map { Register("\$" + it) }.toTypedArray()

    val ra = this["\$ra"]
    val v0 = this["\$v0"]
    val a1 = this["\$a0"]
    val fp = this["\$fp"]
    val sp = this["\$sp"]

    operator fun get(name: String): Register = regs.find { it.name == name } ?: error("no such register: '$name'")
    override fun toString() = regs.filter { it.value != 0 }.toString()
}
