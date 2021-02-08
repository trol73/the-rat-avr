package ru.trolsoft.compiler.lexer

import java.lang.Exception

class LexerException(message: String, val location: SourceLocation, cause: Throwable? = null):
        Exception(message, cause)