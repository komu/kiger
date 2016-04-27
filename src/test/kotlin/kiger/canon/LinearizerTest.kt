package kiger.canon

import kiger.absyn.Symbol
import kiger.env.EnvEntry
import kiger.frame.JouletteFrame
import kiger.parser.parseExpression
import kiger.temp.Label
import kiger.temp.resetLabelSequence
import kiger.temp.resetTempSequence
import kiger.translate.Level
import kiger.translate.Translator
import kiger.tree.TreeStm
import kiger.types.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class LinearizerTest {

    @Test
    fun exampleLinearization() {
        assertLinearization("f(f(1, 2), f(f(3, 4), 5))",
                """
                MOVE tmp1, Call[Name[f], [1, 2]]
                MOVE tmp4, tmp1
                MOVE tmp3, Call[Name[f], [3, 4]]
                MOVE tmp2, Call[Name[f], [tmp3, 5]]
                Call[Name[f], [tmp4, tmp2]]
                """)
    }

    private fun assertLinearization(code: String, expected: String) {
        assertEquals(expected.trimIndent(), linearize(code).joinToString("\n").trimIndent())
    }

    private fun linearize(code: String): List<TreeStm> {
        val translator = Translator()
        translator.baseVenv = translator.baseVenv.enter(Symbol("f"), EnvEntry.Function(Level.Lev(Level.Top, JouletteFrame.newFrame(Label("f"), listOf(false, false))), Label("f"), listOf(Symbol("x") to Type.Int, Symbol("y") to Type.Int), Type.Int))

        resetLabelSequence()
        resetTempSequence()
        val exp = translator.transTopLevelExp(parseExpression(code)).exp

        return exp.asNx().linearize()
    }
}
