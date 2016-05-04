package kiger.translate

import kiger.frame.MipsFrame
import kiger.parser.parseExpression
import kiger.temp.Label
import kiger.temp.resetLabelSequence
import kiger.temp.resetTempSequence
import kiger.tree.TreeExp
import kiger.types.Type
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class TranslationTest {

    val translator = SemanticAnalyzer()

    @Test
    fun translateLiterals() {
        assertTranslation("nil", Type.Nil, TrExp.Ex(TreeExp.Const(0)))
        assertTranslation("42", Type.Int, TrExp.Ex(TreeExp.Const(42)))
        assertTranslation("\"foo\"", Type.String, TrExp.Ex(TreeExp.Name(Label("l2"))))
    }

    @Test
    @Ignore
    fun translateIf() {
        assertTranslation("if 1 < 2 then 3 else 4", Type.Int,
                "Ex[ESeq[Seq[CJump[LT, Const[1], Const[2], l1, l2], Seq[Labeled[l1], Seq[Move[Temporary[t1], Const[3]], Seq[Jump[Name[l3], [l3]], Seq[Labeled[l2], Seq[Move[Temporary[t1], Const[4]], Labeled[l3]]]]]]], Temporary[t1]]]")
    }

    @Test
    fun simpleDefinition() {
        dump("let function square(x: int): int = x * x in square(4)")
    }

    @Test
    fun simpleDefinitions() {
        dump("let function fib(n: int): int = if n < 2 then n else fix(n-1) + fib(n-2) in fib(4)")
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
        return translator.transExp(exp, translator.baseVenv, translator.baseTenv,
                Level.Lev(Level.Top, MipsFrame.newFrame(Label.gen(), emptyList())), null)
    }

    private fun dump(code: String) {
        val exp = parseExpression(code)
        resetTempSequence()
        resetLabelSequence()
        val fragments = SemanticAnalyzer.transProg(exp)
        println(fragments.joinToString("\n"))
    }
}
