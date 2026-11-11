package ru.trolsoft.compiler.compiler

import ru.trolsoft.compiler.lexer.SourceLocation

class CompilerException(message: String, val location: SourceLocation?, cause: Throwable? = null):
        Exception(message, cause)
