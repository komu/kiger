package kiger.translate

import kiger.env.EnvEntry
import kiger.env.SymbolTable
import kiger.parser.parseExpression
import kiger.temp.Label
import kiger.temp.resetLabelSequence
import kiger.temp.resetTempSequence
import kiger.tree.TreeExp
import kiger.types.Type
import org.junit.Test
import kotlin.test.assertEquals

class TranslationTest {

    @Test
    fun translateLiterals() {
        assertTranslation("nil", Type.Nil, Translate.intLiteral(0))
        assertTranslation("42", Type.Int, Translate.intLiteral(42))
        assertTranslation("\"foo\"", Type.String, TrExp.Ex(TreeExp.Name(Label("l1"))))
    }

    @Test
    fun translateIf() {
        assertTranslation("if 1 < 2 then 3 else 4", Type.Int,
                "Ex[ESeq[Seq[CJump[LT, Const[1], Const[2], l1, l2], Seq[Labeled[l1], Seq[Move[Temporary[t1], Const[3]], Seq[Jump[Name[l3], [l3]], Seq[Labeled[l2], Seq[Move[Temporary[t1], Const[4]], Labeled[l3]]]]]]], Temporary[t1]]]")
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
        val venv = SymbolTable<EnvEntry>()
        val tenv = SymbolTable<Type>()
        val exp = parseExpression(code)
        resetTempSequence()
        resetLabelSequence()
        val result = Translator().transExp(exp, venv, tenv, Level.Top, null)
        return result
    }
}
