package kiger.diag

import kiger.lexer.SourceLocation

class Diagnostics {

    var errorCount = 0

    fun error(error: String, pos: SourceLocation) {
        errorCount++
        println("$error\n${pos.toLongString()}")
    }
}
