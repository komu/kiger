package kiger.parser

import kiger.lexer.SyntaxErrorException
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class ParserTest {

    @Test
    fun variables() {
        assertParseExpression("foo", "foo")
    }

    @Test
    fun literals() {
        assertParseExpression("42", "42")
        assertParseExpression("\"foo\"", "\"foo\"")
    }

    @Test
    fun ifStatements() {
        assertParseExpression("if x then y else z", "if x then y else z")
        assertParseExpression("if x then y", "if x then y")
    }

    @Test
    fun whileStatements() {
        assertParseExpression("while x do y", "while x do y")
    }

    @Test
    fun assignment() {
        assertParseExpression("foo := bar", "foo := bar")
    }

    @Test
    @Ignore
    fun ifAsAnExpression() {
        assertParseExpression("1 + if (true) 2 else 3", "[Plus 1 [If [Lit true] 2 3]]")
        assertParseExpression("if (true) 2 else 3 + 4", "[If [Lit true] 2 [Plus 3 4]]")
        assertParseExpression("(if (true) 2 else 3) + 4", "[Plus [If [Lit true] 2 3] 4]")
    }

    @Test
    fun expressionList() {
        assertParseExpression("(x; y; z)", "x; y; z")
    }

    @Test
    fun assignmentToLiteralIsSyntaxError() {
        assertSyntaxError("1 = bar;")
    }

    @Test
    fun binaryOperators() {
        assertParseExpression("1 + 2", "(1 + 2)")
        assertParseExpression("1 - 2", "(1 - 2)")
        assertParseExpression("1 = 2", "(1 = 2)")
        assertParseExpression("1 != 2", "(1 != 2)")
        assertParseExpression("1 < 2", "(1 < 2)")
        assertParseExpression("1 > 2", "(1 > 2)")
        assertParseExpression("1 <= 2", "(1 <= 2)")
        assertParseExpression("1 >= 2", "(1 >= 2)")
    }

    @Test
    fun operatorPrecedence() {
        assertParseExpression("a + b = c + d", "((a + b) = (c + d))")
        assertParseExpression("a + (b = c) + d", "((a + (b = c)) + d)")
        assertParseExpression("a + b * c + d", "((a + (b * c)) + d)")
        assertParseExpression("a = b < c", "(a = (b < c))")
        //assertParseExpression("a == b || c == d && e == f", "((a == b) || ((c == d) && (e == f)))")
    }

    @Test
    fun functionCall() {
        assertParseExpression("foo()", "foo()")
        assertParseExpression("bar(1)", "bar(1)")
        assertParseExpression("baz(1, x)", "baz(1, x)")
    }

    @Test
    @Ignore
    fun functionDefinition() {
        assertParseFunctionDefinition("function square(x: Int, y: Int): Int = x * x",
            "FunctionDefinition(name=square, args=[(x, Int), (y, Int)], returnType=Int, body=[Multiply x x])")
    }

    private fun assertSyntaxError(code: String) {
        assertFailsWith<SyntaxErrorException> {
            val stmt = parseExpression(code)
            fail("expected syntax error, but got $stmt")
        }
    }

    private fun assertParseExpression(source: String, expected: String) {
        val expression = parseExpression(source)

        assertEquals(expected, expression.toString(), source)
    }

    private fun assertParseFunctionDefinition(source: String, expected: String) {
        val expression = parseDeclaration(source)

        assertEquals(expected, expression.toString(), source)
    }
}
