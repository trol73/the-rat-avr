package ru.trolsoft.compiler.lexer

import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readString
import kotlin.text.StringBuilder

class LexerInput(
    stream: RawSource,
    private val fileName: String
) {
    private val source = stream.buffered()
    companion object {
        const val EOF = (-1).toChar()
    }

    private val backupBuf: StringBuilder = StringBuilder()
    private val currentToken: StringBuilder = StringBuilder()
    private var lineNumber: Int = 1
    private var column: Int = 1
    private var lastLineColumn = -1
    private var eof: Boolean = false
    private var charPos: Int = 0
    private var currentLine: String = ""

    private fun ensureChar(): Char {
        if (charPos >= currentLine.length) {
            val line = source.readString()
            if (line.isEmpty()) {
                return EOF
            }
            currentLine = line
            charPos = 0
        }
        return currentLine[charPos++]
    }


    fun read(): Char {
        val c = nextChar()
        if (c != EOF) {
            currentToken.append(c)
            if (c == '\n') {
                lastLineColumn = column
                lineNumber++
                column = 1
            } else if (c != '\r') {
                column++
                lastLineColumn = column
            }
        } else {
            eof = true
        }
        return c
    }

    fun backup(count: Int = 1) {
        if (count > currentToken.length) {
            throw IndexOutOfBoundsException("Count=$count, available=${currentToken.length}")
        }
        val wasEof = eof
        for (i in 1..count) {
            backupOneChar()
        }
        if (wasEof) {
            column -= count - 1
        } else {
            column -= count
        }
    }

    fun getText(): String {
        return currentToken.toString()
    }

    fun extractText(): String {
        val result = getText()
        currentToken.deleteRange(0, result.length)
        return result
    }

    fun getLocation(): SourceLocation {
        if (currentToken.startsWith('\n')) {
            return SourceLocation(fileName, lineNumber, 1)
        }
        return SourceLocation(fileName, lineNumber, column - currentToken.length)
    }

    fun readWhile(expr: (c: Char) -> Boolean) {
        do {
            val c = read()
            if (c == EOF) {
                backup()
                return
            }
        } while (expr(c))
        backup()
    }

    private fun nextChar(): Char {
        return if (backupBuf.isEmpty()) {
            ensureChar()
        } else {
            val result = backupBuf.last()
            backupBuf.deleteAt(backupBuf.lastIndex)
            result
        }
    }


    private fun backupOneChar() {
        if (eof) {
            eof = false
            return
        }
        val last = currentToken.last()
        if (last == '\n') {
            column = lastLineColumn + 1
            lineNumber--
            lastLineColumn = -1
        }
        backupBuf.append(last)
        currentToken.deleteAt(currentToken.lastIndex)
    }

}