package kiger.diag

import kiger.lexer.SourceLocation

class Diagnostics {
    fun error(error: String, pos: SourceLocation) {
        println("$error\n$pos")
    }

}
