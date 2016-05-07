package kiger.regalloc

import kiger.assem.Instr.Move
import kiger.assem.Instr.Oper
import kiger.frame.Register
import kiger.temp.Temp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kiger.regalloc.InterferenceGraph.Move as IMove

class GraphColorerTest {

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

        val preallocated = listOf("R1", "R2", "R3", "R4").map { Pair(Temp(it), Register(it)) }.toMap()

        val flowGraph = inst.createFlowGraph()
        val igraph = flowGraph.interferenceGraph()
        val colorer = GraphColorer(flowGraph, preallocated, preallocated.values.toList())

        val (coloring, spills) = colorer.color()

        assertEquals(0, spills.size, "spills")
        assertValidColoring(igraph, coloring)
    }

    private fun assertValidColoring(igraph: InterferenceGraph, coloring: Coloring) {
        for ((reg, nodes) in igraph.nodes.groupBy { coloring[it.temp] })
            assertFalse(igraph.anyPairInterferes(nodes), "tried to assign interfering nodes to same reg $reg: $nodes")
    }

    private fun InterferenceGraph.anyPairInterferes(nodes: List<InterferenceGraph.INode>): Boolean {
        for (i in nodes.indices)
            for (j in i + 1..nodes.lastIndex)
                if (contains(nodes[i], nodes[j]))
                    return true
        return false
    }
}
