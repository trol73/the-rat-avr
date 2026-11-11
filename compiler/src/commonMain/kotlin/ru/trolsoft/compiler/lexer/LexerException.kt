package ru.trolsoft.compiler.lexer

class LexerException(message: String, val location: SourceLocation, cause: Throwable? = null):
        Exception(message, cause)