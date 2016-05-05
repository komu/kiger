package kiger.lexer

import org.junit.Test
import kotlin.test.assertEquals

class SourceLocationTest {

    @Test
    fun defaultToStringProvidesBasicInfo() {
        val location = SourceLocation("dummy.tig", 42, 14, "    if foo then bar() else baz")

        assertEquals("[dummy.tig:42:14]", location.toString())
    }

    @Test
    fun stringRepresentationProvidesInformationAboutCurrentLine() {
        val location = SourceLocation("dummy.tig", 42, 17, "    if foo then bar() else baz()")

        assertEquals("""
            |[dummy.tig:42:17]     if foo then bar() else baz()
            |                                  ^
            |
        """.trimMargin(), location.toLongString())
    }
}
