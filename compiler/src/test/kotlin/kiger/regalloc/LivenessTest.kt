package kiger.regalloc

import kiger.assem.Instr
import kiger.temp.Label
import kiger.temp.Temp
import org.junit.Test
import kotlin.test.assertEquals

class LivenessTest {

    @Test
    fun liveness() {
        val a = Temp("a")
        val b = Temp("b")
        val c = Temp("c")
        val l1 = Label("l1")

        val instrs = listOf(
                Instr.Oper("a = 0", dst=listOf(a)),
                Instr.Lbl("label l1", l1),
                Instr.Oper("b = a + 1", dst=listOf(b), src=listOf(a)),
                Instr.Oper("c = c + b", dst=listOf(c), src=listOf(b, c)),
                Instr.Oper("a = b * 2", dst=listOf(a), src=listOf(b)),
                Instr.Oper("if a < N goto L1", src=listOf(a), jump=listOf(l1)),
                Instr.Oper("return c", src=listOf(c)))

        val graph = instrs.createFlowGraph()
        graph.initializeLiveOuts()

        assertEquals(setOf(a, c), graph.nodes[0].liveOut)
        assertEquals(setOf(a, c), graph.nodes[1].liveOut)
        assertEquals(setOf(b, c), graph.nodes[2].liveOut)
        assertEquals(setOf(b, c), graph.nodes[3].liveOut)
        assertEquals(setOf(a, c), graph.nodes[4].liveOut)
        assertEquals(setOf(a, c), graph.nodes[5].liveOut)
        assertEquals(setOf(), graph.nodes[6].liveOut)
    }
}
