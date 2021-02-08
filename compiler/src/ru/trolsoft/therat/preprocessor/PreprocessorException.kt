package ru.trolsoft.therat.preprocessor

import ru.trolsoft.therat.lang.Token
import ru.trolsoft.compiler.lexer.SourceLocation
import java.lang.Exception

class PreprocessorException(message: String, val location: SourceLocation, cause: Throwable? = null):
        Exception(message, cause)

fun errorUnexpectedBracket(token: Token): Nothing {
    throw PreprocessorException("Unexpected bracket", token.location)
}

fun errorWrongMacroSyntax(token: Token): Nothing {
    throw PreprocessorException("Wrong macro syntax", token.location)
}

fun errorUnexpectedEof(token: Token): Nothing {
    throw PreprocessorException("Unexpected EOF", token.location)
}

fun errorWrongIncludeSyntax(token: Token): Nothing {
    throw PreprocessorException("Wrong include syntax", token.location)
}

fun errorIdentifierExpected(token: Token): Nothing {
    throw PreprocessorException("Expected identifier", token.location)
}

fun errorExpressionExpected(token: Token): Nothing {
    throw PreprocessorException("Expected expression", token.location)
}

fun errorMissingCloseBracket(token: Token): Nothing {
    throw PreprocessorException("Missing ')'", token.location)
}

fun errorTooFewArgumentsProvided(token: Token): Nothing {
    throw PreprocessorException("Too few arguments provided", token.location)
}

fun errorIfNotFound(token: Token): Nothing {
    throw PreprocessorException("#if/#ifdef directive not found", token.location)
}

fun errorElseAfterElse(token: Token): Nothing {
    throw PreprocessorException("#else after #else", token.location)
}

fun errorOneValueExpected(token: Token): Nothing {
    throw PreprocessorException("extra symbols in line", token.location)
}