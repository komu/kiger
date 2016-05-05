package kiger.absyn

import kiger.lexer.SourceLocation

sealed class Declaration {

    class Functions(val declarations: List<FunctionDeclaration>) : Declaration() {
        override fun toString() = declarations.joinToString("\n")
    }

    class Types(val declarations: List<TypeDeclaration>) : Declaration() {
        override fun toString() = declarations.joinToString("\n")
    }

    class Var(val name: Symbol, val type: Pair<Symbol, SourceLocation>?, val init: Expression, val pos: SourceLocation, escape: Boolean = true) : Declaration() {
        var escape = escape

        override fun toString() =
            if (type != null)
                "var $name: $type := $init"
            else
                "var $name := $init"
    }
}

data class FunctionDeclaration(
        val name: Symbol,
        val params: List<Field>,
        val result: Pair<Symbol, SourceLocation>?,
        val body: Expression,
        val pos: SourceLocation) {
    override fun toString(): String {
        val res = if (result != null) ": $result" else ""
        return "function $name(${params.joinToString(", ")})$res = $body\n"
    }
}

data class TypeDeclaration(
        val name: Symbol,
        val type: TypeRef,
        val pos: SourceLocation) {
    override fun toString() = "type $name = $type"
}
