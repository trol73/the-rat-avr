package ru.trolsoft.compiler.lexer

import ru.trolsoft.compiler.utils.resolvePath

data class SourceLocation(val fileName: String, val line: Int, val column: Int) {
    constructor(src: SourceLocation, deltaColumn: Int) : this(src.fileName, src.line, src.column + deltaColumn)

    fun str(): String = "${fileName.resolvePath()}:$line:$column"

    override fun toString(): String = str()
}