package kiger.translate

import kiger.parser.parseExpression
import kiger.temp.Label
import kiger.temp.resetLabelSequence
import kiger.temp.resetTempSequence
import kiger.tree.TreeExp
import kiger.types.Type
import org.junit.Test
import kotlin.test.assertEquals

class TranslationTest {

    val translator = Translator()

    @Test
    fun translateLiterals() {
        assertTranslation("nil", Type.Nil, TrExp.Ex(TreeExp.Const(0)))
        assertTranslation("42", Type.Int, TrExp.Ex(TreeExp.Const(42)))
        assertTranslation("\"foo\"", Type.String, TrExp.Ex(TreeExp.Name(Label("l1"))))
    }

    @Test
    fun translateIf() {
        assertTranslation("if 1 < 2 then 3 else 4", Type.Int,
                "Ex[ESeq[Seq[CJump[LT, Const[1], Const[2], l1, l2], Seq[Labeled[l1], Seq[Move[Temporary[t1], Const[3]], Seq[Jump[Name[l3], [l3]], Seq[Labeled[l2], Seq[Move[Temporary[t1], Const[4]], Labeled[l3]]]]]]], Temporary[t1]]]")
    }

    @Test
    fun simpleDefinition() {
        dump("let function square(x: int): int = x * x in square(4)")
    }

    private fun assertTranslation(code: String, expectedType: Type, expectedExp: TrExp) {
        val (ex, type) = translate(code)

        assertEquals(expectedType, type)
        assertEquals(expectedExp, ex)
    }

    private fun assertTranslation(code: String, expectedType: Type, expectedExp: String) {
        val (ex, type) = translate(code)

        assertEquals(expectedType, type)
        assertEquals(expectedExp, ex.toString())
    }

    private fun translate(code: String): TranslationResult {
        val exp = parseExpression(code)
        resetTempSequence()
        resetLabelSequence()
        return translator.transExp(exp, translator.baseVenv, translator.baseTenv, Level.Top, null)
    }

    private fun dump(code: String) {
        val exp = parseExpression(code)
        resetTempSequence()
        resetLabelSequence()
        println(Translator.transProg(exp).joinToString("\n"))
    }
}
