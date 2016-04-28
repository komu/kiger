package kiger.vm

import kiger.vm.Inst.Op.*

class Evaluator(val insts: List<Inst>) {

    val regs = Registers()
    var pc = 0
    val labelMap = insts.buildLabelMap()
    val RA = "\$ra"
    val V0 = "\$v0"
    val A1 = "\$a0"
    var running = true

    fun run() {
        pc = labelMap["main"] ?: error("could not find main label")

        while (running) {
            step()
        }
    }

    fun step() {
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
            4   -> System.out.print((insts[regs[A1]+1] as Inst.Pseudo.Asciiz).text)
            10  -> running = false
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
            else -> error("Unsupported op: $this")
        }
    }

    private fun Op2.eval() {
        when (name) {
            "move"  -> regs[a1.reg] = regs[a2.reg]
            "li"    -> regs[a1.reg] = a2.immediate
            "la"    -> regs[a1.reg] = a2.immediate // TODO: what's the difference to li?
            else    -> error("Unsupported op: $this")
        }
    }

    private fun Op3.eval() {
        when (name) {
            "add" -> regs[a1.reg] = regs[a2.reg] + regs[a3.reg]
            "mul" -> regs[a1.reg] = regs[a2.reg] * regs[a3.reg]
            else -> error("Unsupported op: $this")
        }
    }

    private val Operand.immediate: Int
        get() = immediate(labelMap)
}

fun <E> List<E>.buildLabelMap(): Map<String,Int> {
    val map = mutableMapOf<String, Int>()

    this.forEachIndexed { i, inst ->
        if (inst is Inst.Label)
            map[inst.name] = i
    }

    return map
}

class Registers {

    private val regs = mutableMapOf<String,Int>()

    operator fun get(name: String): Int = regs[name] ?: 0

    operator fun set(name: String, value: Int) {
        regs[name] = value
    }

    override fun toString() = regs.toString()
}
