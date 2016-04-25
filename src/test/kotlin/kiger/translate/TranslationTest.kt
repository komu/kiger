package kiger.translate

import kiger.parser.parseExpression
import kiger.types.Type
import org.junit.Test
import kotlin.test.assertEquals

class TranslationTest {

    @Test
    fun translateLiterals() {
        assertTranslation("nil", Type.Nil, Translate.intLiteral(0))
        assertTranslation("42", Type.Int, Translate.intLiteral(42))
        assertType("\"foo\"", Type.String)
    }

    private fun assertTranslation(code: String, expectedType: Type, expectedExp: TrExp) {
        val (ex, type) = translate(code)

        assertEquals(expectedType, type)
        assertEquals(expectedExp, ex)
    }

    private fun assertType(code: String, expectedType: Type) {
        assertEquals(expectedType, translate(code).type)
    }

    private fun translate(code: String): TranslationResult {
        val venv = VarEnv()
        val tenv = TypeEnv()
        val exp = parseExpression(code)
        val result = Translator().transExp(exp, venv, tenv, Level.Top, null)
        return result
    }
}
