package kiger.parser

import kiger.absyn.*
import kiger.lexer.*
import kiger.lexer.Token.Keyword.*
import kiger.lexer.Token.Keyword.Array
import kiger.lexer.Token.Operator
import kiger.lexer.Token.Punctuation.*
import kiger.lexer.Token.Symbol
import java.util.*

/**
 * Parse [code] as [Expression].
 *
 * @throws SyntaxErrorException if parsing fails
 */
fun parseExpression(code: String): Expression =
        parseComplete(Lexer(code)) { it.parseTopLevelExpression() }

/**
 * Parses a function definition.
 */
fun parseDeclaration(code: String): Declaration =
    parseComplete(Lexer(code)) { it.parseDeclaration() }

/**
 * Parses a function definition.
 */
fun parseDeclarations(code: String, file: String): List<Declaration> =
    parseComplete(Lexer(code, file)) { it.parseFunctionDefinitions() }

/**
 * Executes parser on code and verifies that it consumes all input.
 *
 * @throws SyntaxErrorException if the parser fails or if it did not consume all input
 */
private fun <T> parseComplete(lexer: Lexer, callback: (Parser) -> T): T {
    val parser = Parser(lexer)
    val result = callback(parser)
    parser.expectEnd()
    return result
}

/**
 * A simple recursive descent parser.
 */
private class Parser(lexer: Lexer) {

    private val lexer = LookaheadLexer(lexer)

    fun parseFunctionDefinitions(): List<Declaration> {
        val result = ArrayList<Declaration>()

        while (lexer.hasMore)
            result += parseDeclaration()

        return result
    }

    fun parseDeclaration(): Declaration {
        return parseFunctionDeclaration()
    }

    fun parseFunctionDeclaration(): Declaration.Functions {
        val pos = lexer.expect(Token.Keyword.Function)
        val name = parseName().first
        val params = parseArgumentDefinitionList()
        val returnType = if (lexer.readNextIf(Colon)) parseName() else null
        lexer.expect(Equal)
        val body = parseTopLevelExpression()

        val func = FunctionDeclaration(name, params, returnType, body, pos)
        return Declaration.Functions(listOf(func))
    }

    fun parseTopLevelExpression(): Expression {
        val location = lexer.nextTokenLocation()
        val exp = parseExpression0()

        if (lexer.nextTokenIs(Semicolon)) {
            val result = mutableListOf(exp to location)

            while (lexer.readNextIf(Semicolon)) {
                val pos = lexer.nextTokenLocation()
                result += parseExpression0() to pos
            }

            return Expression.Seq(result)
        } else {
            return exp
        }
    }


    fun parseExpression0() = when (lexer.peekToken().token) {
        is Token.Symbol -> {
            val exp = parseExpression1();
            if (exp is Expression.Var && lexer.nextTokenIs(Equal))
                parseAssignTo(exp.variable)
            else
                exp
        }
        else ->
            parseExpression1()
    }

    /**
     * ```
     * expression1 ::= expression2 (("||") expression2)*
     * ```
     */
    private fun parseExpression1(): Expression {
        var exp = parseExpression2()

//        while (lexer.hasMore) {
//            val location = lexer.nextTokenLocation()
//            when {
//                lexer.readNextIf(Operator.Or) ->
//                    exp = Expression.Op(exp, Operator.Or, parseExpression2(), location)
//                else ->
//                    return exp
//            }
//        }

        return exp
    }

    /**
     * ```
     * expression2 ::= expression3 (("&&") expression2)3
     * ```
     */
    private fun parseExpression2(): Expression {
        var exp = parseExpression3()

//        while (lexer.hasMore) {
//            val location = lexer.nextTokenLocation()
//            when {
//                lexer.readNextIf(Operator.And) ->
//                    exp = Expression.Op(exp, Operator.And, parseExpression3(), location)
//                else ->
//                    return exp
//            }
//        }

        return exp
    }

    /**
     * ```
     * expression3 ::= expression4 (("==" | "!=") expression4)*
     * ```
     */
    fun parseExpression3(): Expression {
        var exp = parseExpression4()

        while (lexer.hasMore) {
            val location = lexer.nextTokenLocation()
            when {
                lexer.readNextIf(Operator.EqualEqual) ->
                    exp = Expression.Op(exp, Operator.EqualEqual, parseExpression4(), location)
                lexer.readNextIf(Operator.NotEqual) ->
                    exp = Expression.Op(exp, Operator.NotEqual, parseExpression4(), location)
                else ->
                    return exp
            }
        }

        return exp
    }

    /**
     * ```
     * expression4 ::= expression5 (("<" | ">" | "<=" | ">=") expression5)*
     * ```
     */
    private fun parseExpression4(): Expression {
        var exp = parseExpression5()

        while (lexer.hasMore) {
            val location = lexer.nextTokenLocation()
            when {
                lexer.readNextIf(Operator.LessThan) ->
                    exp = Expression.Op(exp, Operator.LessThan, parseExpression5(), location)
                lexer.readNextIf(Operator.LessThanOrEqual) ->
                    exp = Expression.Op(exp, Operator.LessThanOrEqual, parseExpression5(), location)
                lexer.readNextIf(Operator.GreaterThan) ->
                    exp = Expression.Op(exp, Operator.GreaterThan, parseExpression5(), location)
                lexer.readNextIf(Operator.GreaterThanOrEqual) ->
                    exp = Expression.Op(exp, Operator.GreaterThanOrEqual, parseExpression5(), location)
                else ->
                    return exp
            }
        }

        return exp
    }

    /**
     * ```
     * expression5 ::= expression6 (("+" | "-") expression6)*
     * ```
     */
    private fun parseExpression5(): Expression {
        var exp = parseExpression6()

        while (lexer.hasMore) {
            val location = lexer.nextTokenLocation()
            when {
                lexer.readNextIf(Operator.Plus) ->
                    exp = Expression.Op(exp, Operator.Plus, parseExpression6(), location)
                lexer.readNextIf(Operator.Minus) ->
                    exp = Expression.Op(exp, Operator.Minus, parseExpression6(), location)
                else ->
                    return exp
            }
        }

        return exp
    }

    /**
     * ```
     * expression6 ::= expression7 (("*" | "/") expression7)*
     * ```
     */
    private fun parseExpression6(): Expression {
        var exp = parseExpression7()

        while (lexer.hasMore) {
            val location = lexer.nextTokenLocation()
            when {
                lexer.readNextIf(Operator.Multiply) ->
                    exp = Expression.Op(exp, Operator.Multiply, parseExpression7(), location)
                lexer.readNextIf(Operator.Divide) ->
                    exp = Expression.Op(exp, Operator.Divide, parseExpression7(), location)
                else ->
                    return exp
            }
        }

        return exp
    }

    /**
     * ```
     * expression7 ::= expression8 [ '(' args ')']
     * ```
     */
    private fun parseExpression7(): Expression {
        return parseExpression8()

//        return if (lexer.nextTokenIs(LeftParen))
//            Expression.Call(exp, parseArgumentList())
//        else
//            exp
    }

    /**
     * ```
     * expression8 ::= identifier | literal | not | "(" expression ")" | if | while
     * ```
     */
    private fun parseExpression8(): Expression {
        val (token, location) = lexer.peekToken()

        return when (token) {
            is Symbol           -> parseIdentifierOrCall()
            is Token.Str        -> parseString()
            is Token.Integer    -> parseInteger()
            Nil                 -> parseNil()
            LeftParen           -> inParens { parseTopLevelExpression() }
            If                  -> parseIf()
            While               -> parseWhile()
            else                -> fail(location, "unexpected token $token")
        }
    }

    private fun parseNil(): Expression {
        lexer.expect(Nil)
        return Expression.Nil
    }

    private fun parseInteger(): Expression {
        val token = lexer.readExpected<Token.Integer>().token
        return Expression.Int(token.value)
    }

    private fun parseString(): Expression {
        val (token, location) = lexer.readExpected<Token.Str>()
        return Expression.String(token.value, location)
    }

    private fun parseAssignTo(variable: Variable): Expression {
        val location = lexer.expect(Equal)
        val rhs = parseTopLevelExpression()

        return Expression.Assign(variable, rhs, location)
    }

    private fun parseIf(): Expression {
        val location = lexer.expect(If)
        val condition = parseTopLevelExpression()
        lexer.expect(Then)
        val consequent = parseTopLevelExpression()
        val alternative = if (lexer.readNextIf(Else)) parseTopLevelExpression() else null

        return Expression.If(condition, consequent, alternative, location)
    }

    private fun parseWhile(): Expression {
        val location = lexer.expect(While)
        val condition = inParens { parseTopLevelExpression() }
        val body = parseTopLevelExpression()

        return Expression.While(condition, body, location)
    }

    private fun parseIdentifierOrCall(): Expression {
        val (name, location) = parseName()

        if (lexer.nextTokenIs(LeftParen)) {
            val args = parseArgumentList()
            return Expression.Call(name, args, location)
        } else
            return Expression.Var(Variable.Simple(name, location))
    }

    private fun parseArgumentList(): List<Expression> =
        inParens {
            if (lexer.nextTokenIs(RightParen))
                emptyList()
            else {
                val args = ArrayList<Expression>()
                do {
                    args += parseTopLevelExpression()
                } while (lexer.readNextIf(Comma))
                args
            }
        }

    private fun parseArgumentDefinitionList(): List<Field> =
        inParens {
            if (lexer.nextTokenIs(RightParen))
                emptyList()
            else {
                val args = ArrayList<Field>()
                do {
                    val (name, location) = parseName()
                    lexer.expect(Colon)
                    val type = parseName().first
                    args += Field(name, type, location)
                } while (lexer.readNextIf(Comma))
                args
            }
        }

    private fun parseName(): Pair<Symbol, SourceLocation> {
        val (token, location) = lexer.readExpected<Symbol>()

        return Pair(token, location)
    }

    private fun parseType(): TypeRef {
        if (lexer.nextTokenIs(Array)) {
            lexer.expect(Of)

            val (token, location) = lexer.readExpected<Symbol>()
            return TypeRef.Array(token, location)
        } else if (lexer.nextTokenIs(LeftBrace)) {
            return TypeRef.Record(inBraces { parseFields() })
        } else {
            val (token, location) = lexer.readExpected<Symbol>()
            return TypeRef.Name(token, location)
        }
    }

    private fun parseFields(): List<Field> {
        TODO()
    }

    private inline fun <T> inParens(parser: () -> T): T =
        between(LeftParen, RightParen, parser)

    private inline fun <T> inBraces(parser: () -> T): T =
        between(LeftBrace, RightBrace, parser)

    private inline fun <T> between(left: Token, right: Token, parser: () -> T): T {
        lexer.expect(left)
        val value = parser()
        lexer.expect(right)
        return value
    }

    private inline fun <T> separatedBy(separator: Token, parser: () -> T): List<T> {
        val result = ArrayList<T>()

        do {
            result += parser()
        } while (lexer.readNextIf(separator))

        return result
    }

    private fun fail(location: SourceLocation, message: String): Nothing =
        throw SyntaxErrorException(message, location)

    fun expectEnd() {
        if (lexer.hasMore) {
            val (token, location) = lexer.peekToken()
            fail(location, "expected end, but got $token")
        }
    }
}
