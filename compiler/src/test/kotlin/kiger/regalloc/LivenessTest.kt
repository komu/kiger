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
        val liveoutMap = graph.buildLiveOuts()
        val liveout = graph.nodes.map { liveoutMap[it]!! }

        assertEquals(setOf(a, c), liveout[0])
        assertEquals(setOf(a, c), liveout[1])
        assertEquals(setOf(b, c), liveout[2])
        assertEquals(setOf(b, c), liveout[3])
        assertEquals(setOf(a, c), liveout[4])
        assertEquals(setOf(a, c), liveout[5])
        assertEquals(setOf(), liveout[6])
    }
}
