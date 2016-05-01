package kiger.vm

import kiger.vm.Inst.Op.*

/**
 * http://www.mrc.uidaho.edu/mrc/people/jff/digital/MIPSir.html
 */
class Evaluator(allInstructions: List<Inst>) {

    val regs = Registers()
    val PC_EXIT = -1
    var pc = PC_EXIT

    val insts = mutableListOf<Inst>()
    val labelMap = mutableMapOf<String, Int>()

    val RA = "\$ra"
    val V0 = "\$v0"
    val A1 = "\$a0"
    val FP = "\$fp"
    val SP = "\$sp"

    private val mem = Array(1024 * 1024) { 0 }

    init {
        for (inst in allInstructions) {
            if (inst is Inst.Label)
                labelMap[inst.name] = insts.size
            else
                insts += inst
        }

        regs[RA] = PC_EXIT
        regs[FP] = mem.size - 1000
        regs[SP] = mem.size - 1000
    }

    fun run() {
        pc = labelMap["main"] ?: error("could not find main label")

        while (pc != PC_EXIT) {
            step()
        }
    }

    fun step() {
        println("$pc: ${insts[pc]}")
        val op = insts[pc++] as? Inst.Op

        when (op) {
            is Op0 -> op.eval()
            is Op1 -> op.eval()
            is Op2 -> op.eval()
            is Op3 -> op.eval()
        }
    }

    private fun Op0.eval() {
        when (name) {
            "syscall"   -> syscall()
            else        -> error("Unsupported op: $this")
        }
    }

    private fun syscall() {
        val call = regs[V0]
        when (call) {
            1   -> System.out.print(regs[A1])
            4   -> System.out.print((insts[regs[A1]] as Inst.Data).text)
            10  -> pc = PC_EXIT
            else -> error("unknown syscall $call")
        }
    }

    private fun Op1.eval() {
        when (name) {
            "j"     -> pc = a1.immediate
            "jr"    -> pc = regs[a1.reg]
            "jal"   -> {
                regs[RA] = pc
                pc = a1.immediate
            }
            "jalr"   -> {
                regs[RA] = pc
                pc = regs[a1.reg]
            }
            else -> error("Unsupported op: $this")
        }
    }

    private fun Op2.eval() {
        when (name) {
            "move"  -> regs[a1.reg] = regs[a2.reg]
            "lw"    -> regs[a1.reg] = mem[regs[a2.baseReg] + a2.offset]
            "sw"    -> mem[regs[a2.baseReg] + a2.offset] = regs[a1.reg]
            "li"    -> regs[a1.reg] = a2.immediate
            "la"    -> regs[a1.reg] = a2.immediate
            else    -> error("Unsupported op: $this")
        }
    }

    private fun Op3.eval() {
        when (name) {
            "add"   -> regs[a1.reg] = regs[a2.reg] + regs[a3.reg]
            "addiu" -> regs[a1.reg] = regs[a2.reg] + a3.immediate
            "mul"   -> regs[a1.reg] = regs[a2.reg] * regs[a3.reg]
            else -> error("Unsupported op: $this")
        }
    }

    private val Operand.immediate: Int
        get() = immediate(labelMap)
}

class Registers {

    private val regs = mutableMapOf<String,Int>()

    operator fun get(name: String): Int = regs[name] ?: 0

    operator fun set(name: String, value: Int) {
        regs[name] = value
    }

    override fun toString() = regs.toString()
}
