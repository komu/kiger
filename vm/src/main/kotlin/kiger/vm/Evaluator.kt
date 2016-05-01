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

        println("v0: ${regs[V0]}")
    }

    fun step() {
        // println("$pc: ${insts[pc].toString().padEnd(30)} ${regs}")
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
            "j"     -> pc = o1.immediate
            "jr"    -> pc = regs[o1.reg]
            "jal"   -> {
                regs[RA] = pc
                pc = o1.immediate
            }
            "jalr"   -> {
                regs[RA] = pc
                pc = regs[o1.reg]
            }
            else -> error("Unsupported op: $this")
        }
    }

    private fun Op2.eval() {
        when (name) {
            "move"  -> regs[o1.reg] = regs[o2.reg]
            "lw"    -> regs[o1.reg] = mem[regs[o2.baseReg] + o2.offset]
            "sw"    -> mem[regs[o2.baseReg] + o2.offset] = regs[o1.reg]
            "li"    -> regs[o1.reg] = o2.immediate
            "la"    -> regs[o1.reg] = o2.immediate
            else    -> error("Unsupported op: $this")
        }
    }

    private fun Op3.eval() {
        when (name) {
            "add"   -> regs[o1.reg] = regs[o2.reg] + regs[o3.reg]
            "addiu" -> regs[o1.reg] = regs[o2.reg] + o3.immediate
            "mul"   -> regs[o1.reg] = regs[o2.reg] * regs[o3.reg]
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

    override fun toString() = regs.map { Pair(it.key, it.value) }.filter { it.second != 0 }.toString()
}
