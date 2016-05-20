package kiger.regalloc

import kiger.assem.Instr.Oper
import kiger.assem.InstrBasicBlock
import kiger.assem.InstrControlFlowGraph
import kiger.temp.Label
import kiger.temp.Temp
import org.junit.Test

class LivenessTest {

    @Test
    fun liveness() {
        val a = Temp("a")
        val b = Temp("b")
        val c = Temp("c")
        val l1 = Label("l1")
        val l2 = Label("l2")

        val block1 = InstrBasicBlock(Label.gen(), listOf(
                Oper("a = 0", dst = listOf(a), jump = listOf(l1))))
        val block2 = InstrBasicBlock(l1, listOf(
                Oper("b = a + 1", dst = listOf(b), src = listOf(a)),
                Oper("c = c + b", dst = listOf(c), src = listOf(b, c)),
                Oper("a = b * 2", dst = listOf(a), src = listOf(b)),
                Oper("if a < N goto l1", src = listOf(a), jump = listOf(l1, l2))))
        val block3 = InstrBasicBlock(l2, listOf(
                Oper("return c", src = listOf(c))))

        val cfg = InstrControlFlowGraph(listOf(block1, block2, block3), Label.gen())

        val liveoutMap = cfg.buildLiveOutsForBasicBlocks()
//        val liveout = cfg.toInstrs().map { liveoutMap[it] ?: emptySet() }
//
//        println("${cfg.toInstrs()}")
//
//        assertEquals(setOf(c), liveout[0])
//        assertEquals(setOf(a, c), liveout[1])
//        assertEquals(setOf(a, c), liveout[2])
//        assertEquals(setOf(b, c), liveout[3])
//        assertEquals(setOf(b, c), liveout[4])
//        assertEquals(setOf(a, c), liveout[5])
//        assertEquals(setOf(a, c), liveout[6])
//        assertEquals(setOf(c), liveout[7])
//        assertEquals(setOf(), liveout[8])
    }
}
