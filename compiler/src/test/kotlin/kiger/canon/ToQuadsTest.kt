package kiger.canon

import kiger.temp.Label
import kiger.temp.Temp
import kiger.temp.resetTempSequence
import kiger.tree.BinaryOp.PLUS
import kiger.tree.TreeExp.*
import kiger.tree.TreeStm.Exp
import kiger.tree.TreeStm.Move
import org.junit.Test
import kotlin.test.assertEquals

class ToQuadsTest {

    @Test
    fun translateToQuads() {
        resetTempSequence()
        val exp = Exp(Call(Name(Label("foo")), listOf(BinOp(PLUS, Const(1), Mem(Const(42))))))
        val quads = exp.toQuads()

        resetTempSequence()
        assertEquals(3, quads.size)
        val temp1 = Temp.gen()
        val temp2 = Temp.gen()

        assertEquals(Move(Temporary(temp1), Mem(Const(42))), quads[0])
        assertEquals(Move(Temporary(temp2), BinOp(PLUS, Const(1), Temporary(temp1))), quads[1])
        assertEquals(Exp(Call(Name(Label("foo")), listOf(Temporary(temp2)))), quads[2])
    }
}
