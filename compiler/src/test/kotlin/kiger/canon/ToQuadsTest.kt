package kiger.canon

import kiger.ir.quad.QExp
import kiger.ir.quad.Quad
import kiger.temp.Label
import kiger.temp.Temp
import kiger.temp.resetTempSequence
import kiger.tree.BinaryOp.PLUS
import kiger.tree.RelOp
import kiger.tree.TreeExp.*
import kiger.tree.TreeStm
import org.junit.Test
import kotlin.test.assertEquals

class ToQuadsTest {

    @Test
    fun translateToQuads() {
        val exp = TreeStm.Move(Mem(Const(400)), (Call(Name(Label("foo")), listOf(BinOp(PLUS, Const(1), Mem(Const(42)))))))

        resetTempSequence()
        val quads = exp.toQuads()

        resetTempSequence()
        val temp1 = Temp.gen()
        val temp2 = Temp.gen()
        val temp3 = Temp.gen()

        assertEquals(4, quads.size)
        assertEquals(Quad.Load(temp1, QExp.Const(42)), quads[0])
        assertEquals(Quad.BinOp(PLUS, temp2, QExp.Const(1), QExp.Temporary(temp1)), quads[1])
        assertEquals(Quad.Call(QExp.Name(Label("foo")), listOf(QExp.Temporary(temp2)), temp3), quads[2])
        assertEquals(Quad.Store(QExp.Const(400), QExp.Temporary(temp3)), quads[3])
    }

    @Test
    fun translateCJump() {
        val exp = TreeStm.Branch.CJump(RelOp.EQ, BinOp(PLUS, Const(1), Const(2)), Const(3), Label("t"), Label("f"))

        resetTempSequence()
        val quads = exp.toQuads()

        resetTempSequence()
        val temp1 = Temp.gen()

        assertEquals(2, quads.size)
        assertEquals(Quad.BinOp(PLUS, temp1, QExp.Const(1), QExp.Const(2)), quads[0])
        assertEquals(Quad.CJump(RelOp.EQ, QExp.Temporary(temp1), QExp.Const(3), Label("t"), Label("f")), quads[1])
    }
}
