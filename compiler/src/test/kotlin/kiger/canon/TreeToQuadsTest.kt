package kiger.canon

import kiger.ir.BinaryOp.PLUS
import kiger.ir.RelOp
import kiger.ir.tree.TreeExp
import kiger.ir.tree.TreeExp.*
import kiger.ir.tree.TreeStm
import kiger.temp.Label
import kiger.temp.Temp
import kiger.temp.resetTempSequence
import org.junit.Test
import kotlin.test.assertEquals

class TreeToQuadsTest {

    @Test
    fun translateToQuads() {
        val exp = TreeStm.Move(Mem(Const(400)), (Call(Name(Label("foo")), listOf(BinOp(PLUS, Const(1), Mem(Const(42)))))))

        resetTempSequence()
        val quads = exp.toQuads()

        assertEquals("""
            %1 <- mem[42]
            %2 <- 1 + %1
            %3 <- foo(%2)
            mem[400] = %3
        """.trimIndent(), quads.joinToString("\n"))
    }

    @Test
    fun translateCJump() {
        val exp = TreeStm.Branch.CJump(RelOp.EQ, BinOp(PLUS, Const(1), Const(2)), Const(3), Label("t"), Label("f"))

        resetTempSequence()
        val quads = exp.toQuads()

        assertEquals("""
            %1 <- 1 + 2
            if (%1 EQ 3) jump t else jump f
        """.trimIndent(), quads.joinToString("\n"))
    }

    @Test
    fun memOnBothSidesOfMove() {
        val exp = TreeStm.Move(Mem(plus(temp("fp"), Const(-8))), plus(Mem(plus(temp("fp"), Const(-8))), Const(4)))

        resetTempSequence()
        val quads = exp.toQuads()

        assertEquals("""
            %1 <- fp + -8
            %5 <- %1
            %2 <- fp + -8
            %3 <- mem[%2]
            %4 <- %3 + 4
            mem[%5] = %4
        """.trimIndent(), quads.joinToString("\n"))
    }

    private fun temp(name: String) = Temporary(Temp(name))
    private fun plus(lhs: TreeExp, rhs: TreeExp) = BinOp(PLUS, lhs, rhs)
}
