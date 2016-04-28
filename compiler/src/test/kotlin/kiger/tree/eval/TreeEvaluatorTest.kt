package kiger.tree.eval

import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class TreeEvaluatorTest {

    @Test
    fun simpleExpressions() {
        assertEval(1, "1")
        assertEval(2, "1+1")
        assertEval(7, "1+2*3")
    }

    @Test
    fun variables() {
        assertEval(42, "let var x = 42 in x")
        assertEval(16, "let var x = 4 in x * x")
        assertEval(36, """
            let var x = 4
                var y = 9
            in x * y""")
    }

    @Test
    @Ignore
    fun simpleDefinition() {
        eval("let function square(x: int): int = x * x in square(4)")
    }

    @Test
    @Ignore
    fun simpleDefinitions() {
        eval("let function fib(n: int): int = if n < 2 then n else fix(n-1) + fib(n-2) in fib(4)")
    }

    private fun assertEval(expected: Int, code: String) {
        assertEquals(expected, eval(code))
    }

    private fun eval(code: String) =
        TreeEvaluator.evaluate(code)
}
