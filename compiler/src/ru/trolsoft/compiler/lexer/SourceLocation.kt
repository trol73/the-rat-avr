package ru.trolsoft.compiler.lexer

data class SourceLocation(val fileName: String, val line: Int, val column: Int) {
    constructor(src: SourceLocation, deltaColumn: Int) : this(src.fileName, src.line, src.column + deltaColumn)

    fun str(): String = "$fileName:$line:$column"
}