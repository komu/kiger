package kiger.regalloc

import kiger.assem.Instr.Move
import kiger.assem.Instr.Oper
import kiger.temp.Temp
import org.junit.Test
import kotlin.test.assertEquals
import kiger.regalloc.InterferenceGraph.Move as IMove

class InterferenceGraphTest {

    @Test
    fun simpleGraph() {
        // from page 230 of Appel

        val b = Temp("b")
        val c = Temp("c")
        val d = Temp("d")
        val e = Temp("e")
        val f = Temp("f")
        val g = Temp("g")
        val h = Temp("h")
        val j = Temp("j")
        val k = Temp("k")
        val m = Temp("m")

        val inst = listOf(
                Oper("", dst=listOf(k, j)),
                Oper("g := mem[j+12]", dst=listOf(g), src=listOf(j)),
                Oper("h := k - 1", dst=listOf(h), src=listOf(k)),
                Oper("f := g * h", dst=listOf(f), src=listOf(g, h)),
                Oper("e := mem[j+8]", dst=listOf(e), src=listOf(j)),
                Oper("m := mem[j+16]", dst=listOf(m), src=listOf(j)),
                Oper("b := mem[f]", dst=listOf(b), src=listOf(f)),
                Oper("c := e + 8", dst=listOf(c), src=listOf(e)),
                Move("d := c", dst=d, src=c),
                Oper("k := m + 4", dst=listOf(k), src=listOf(m)),
                Move("j := b", dst=j, src=b),
                Oper("", src=listOf(d, k, j)))

        val igraph = inst.createFlowGraph().interferenceGraph()

        assertEquals(10, igraph.nodes.size)
        assertEquals(2, igraph.moves.size)

        assertEquals(5, igraph[b].degree)
        assertEquals(2, igraph[c].degree)
        assertEquals(4, igraph[d].degree)
        assertEquals(4, igraph[e].degree)
        assertEquals(3, igraph[f].degree)
        assertEquals(3, igraph[g].degree)
        assertEquals(2, igraph[h].degree)
        assertEquals(6, igraph[j].degree)
        assertEquals(4, igraph[k].degree)
        assertEquals(5, igraph[m].degree)

        assertEquals(listOf(IMove(igraph[b], igraph[j])), igraph[b].moveList)
        assertEquals(listOf(IMove(igraph[b], igraph[j])), igraph[j].moveList)

        assertEquals(listOf(IMove(igraph[c], igraph[d])), igraph[c].moveList)
        assertEquals(listOf(IMove(igraph[c], igraph[d])), igraph[d].moveList)
    }
}
