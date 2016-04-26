package kiger.tree.eval

import org.junit.Test
import kotlin.test.assertEquals

class TreeEvaluatorTest {

    @Test
    fun simpleDefinition() {
        assertIntEvaluation(16, "let function square(x: int): int = x * x in square(4)")
    }

    @Test
    fun simpleDefinitions() {
        assertIntEvaluation(42, "let function fib(n: int): int = if n < 2 then n else fix(n-1) + fib(n-2) in fib(4)")
    }

    private fun assertIntEvaluation(expectedValue: Int, code: String) {
        val result = TreeEvaluator.evaluate(code)
        assertEquals(expectedValue, result)
    }
}
