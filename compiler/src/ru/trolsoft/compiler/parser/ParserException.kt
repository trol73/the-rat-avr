package ru.trolsoft.compiler.parser

import ru.trolsoft.therat.lang.KeywordToken
import ru.trolsoft.therat.lang.Token
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import java.lang.Exception

class ParserException(message: String, val location: SourceLocation, cause: Throwable? = null):
        Exception(message, cause) {

    override fun toString(): String {
        return super.toString() + " at " + location.str()
    }
}

fun errorIdentifierExpected(token: Token): Nothing {
    throw ParserException("Identifier expected", token.location)
}

fun errorIdentifierExpected(node: Node): Nothing {
    throw ParserException("Identifier expected", node.getLocation())
}

fun errorOperatorExpected(token: Token): Nothing {
    throw ParserException("Operator expected", token.location)
}

fun errorOperatorExpected(op: String, token: Token): Nothing {
    throw ParserException("'$op' expected", token.location)
}

fun errorUnexpectedEof(stream: TokenStream): Nothing {
    throw ParserException("Unexpected EOF", stream.getEofLocation())
}

fun errorDataTypeExpected(token: Token): Nothing {
    throw ParserException("Expected data type", token.location)
}

fun errorInvalidExternSyntax(token: Token): Nothing {
    throw ParserException("Expected ':' or '('", token.location)
}

fun errorRegisterExpected(token: Token): Nothing {
    throw ParserException("Register expected", token.location)
}

fun errorCharExpected(ch: Char, token: Token): Nothing {
    throw ParserException("Expected '$ch'", token.location)
}

fun errorUnexpectedToken(token: Token): Nothing {
    throw ParserException("Unexpected token: ${token.str()}", token.location)
}

fun errorUnexpectedKeyword(token: KeywordToken): Nothing {
    throw ParserException("Unexpected keyword: ${token.name}", token.location)
}

fun errorStringLiteralExpected(token: Token): Nothing {
    throw ParserException("String expected", token.location)
}

fun errorNumberExpected(token: Token): Nothing {
    throw ParserException("Number expected", token.location)
}

fun errorNumberExpected(node: Node): Nothing {
    throw ParserException("Number expected", node.getLocation())
}

fun errorLabelExpected(token: Token): Nothing {
    throw ParserException("Label expected", token.location)
}

fun errorKeywordExpected(token: Token, keyword: String): Nothing {
    throw ParserException("$keyword expected, but ${token.str()} found", token.location)
}

fun errorBracketExpected(token: Token, ch: Char): Nothing {
    throw ParserException("Expected '$ch', but ${token.str()} found", token.location)
}
