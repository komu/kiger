package kiger.lexer

/**
 * Tokens are the indivisible building blocks of source code.
 *
 * [Lexer] analyzes the source string to return tokens to be consumed by the parser.
 *
 * Some tokens are singleton values: e.g. when encountering `if` in the source code,
 * the lexer will return simply [Token.Keyword.If]. Other tokens contain information about
 * the value read: e.g. for source code `123`, the lexer will return [Token.Literal],
 * with its `value` set to integer `123`.
 *
 * @see TokenInfo
 */
sealed class Token {

    /**
     * Identifier such as variable, method or class name.
     */
    class Symbol(val name: String): Token() {
        override fun toString() = name
        override fun equals(other: Any?) = other is Symbol && name == other.name
        override fun hashCode(): Int = name.hashCode()
    }

    class Str(val value: String) : Token() {
        override fun toString() = "[Str $value]"
        override fun equals(other: Any?) = other is Str && value == other.value
        override fun hashCode(): Int = value.hashCode()
    }

    class Integer(val value: Int) : Token() {
        override fun toString() = "[Integer $value]"
        override fun equals(other: Any?) = other is Integer && value == other.value
        override fun hashCode(): Int = value
    }

    /**
     * Reserved word in the language.
     */
    sealed class Keyword(private val name: String) : Token() {

        override fun toString() = name

        object Type : Keyword("type")
        object Array : Keyword("array")
        object Of : Keyword("of")
        object Function : Keyword("function")
        object If : Keyword("if")
        object Then : Keyword("then")
        object Else : Keyword("else")
        object Var : Keyword("var")
        object Let : Keyword("let")
        object Nil : Keyword("nil")
        object While : Keyword("while")
        object In : Keyword("in")
    }

    /**
     * Operators.
     */
    sealed class Operator(private val name: String) : Token() {

        override fun toString() = name

        object Plus : Operator("+")
        object Minus : Operator("-")
        object Multiply : Operator("*")
        object Divide : Operator("/")
        object EqualEqual : Operator("==")
        object NotEqual : Operator("!=")
        object LessThan : Operator("<")
        object GreaterThan : Operator(">")
        object LessThanOrEqual : Operator("<=")
        object GreaterThanOrEqual : Operator(">=")
    }

    /**
     * General punctuation.
     */
    sealed class Punctuation(private val name: String) : Token() {

        override fun toString() = "'$name'"

        object LeftParen : Punctuation("(")
        object RightParen : Punctuation(")")
        object LeftBrace : Punctuation("{")
        object RightBrace : Punctuation("}")
        object Equal : Punctuation("=")
        object Colon : Punctuation(":")
        object Semicolon : Punctuation(";")
        object Comma : Punctuation(",")
    }
}
