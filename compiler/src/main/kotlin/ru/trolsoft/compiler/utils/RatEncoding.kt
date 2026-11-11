package ru.trolsoft.compiler.utils

import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readLine
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.ParserException

class RatEncoding(
    private val charCode: Map<Char, Int>
) {
    private val codeChar: Map<Int, Char> = buildCodeToChar()

    operator fun contains(ch: Char) = charCode.containsKey(ch)

    operator fun get(ch: Char) = charCode[ch]

    fun size() = charCode.size
    fun hasChar(ch: Char) = charCode.containsKey(ch)
    fun hasCode(code: Int) = charCode.containsValue(code)
    fun getCode(ch: Char): Int? = charCode[ch]
    fun getChar(code: Int): Char? = codeChar[code]

    private fun buildCodeToChar(): Map<Int, Char> {
        val res = mutableMapOf<Int, Char>()
        for ((ch, code) in charCode) {
            res[code] = ch
        }
        return res
    }
}

class RatEncodingReader {
    private val charCodes = mutableMapOf<Char, Int>()
    private var filePath: String? = null
    private var line: Int = 1

    fun read(source: RawSource): RatEncoding {
        val buffered = source.buffered()
        line = 0

        charCodes.clear()
        while (true) {
            val s = buffered.readLine() ?: break
            line++
            if (s.isBlank() || s.startsWith(";")) {
                continue
            }
            if (s.length < 6 || s[2] != '-' || s[5] != ':') {
                errorWrongFormat()
            }
            val from = parseNumber("0x${s.substring(0, 2)}") ?: errorWrongFormat()
            val to = parseNumber("0x${s.substring(3, 5)}") ?: errorWrongFormat()
            if (from > to) {
                error("Wrong range: 0x$from .. 0x$to")
            }
            val chars = s.substring(6)
            if (chars.length != to-from+1) {
                error("Wrong format: expected ${to-from+1} chars but found ${chars.length}")
            }
            for (code in from..to) {
                val ch = chars[code-from]
                charCodes[ch] = code
            }
        }
        return RatEncoding(charCodes)
    }

    fun read(path: String): RatEncoding {
        val filePath = kotlinx.io.files.Path(path)
        if (!kotlinx.io.files.SystemFileSystem.exists(filePath)) {
            error("File not found: $path")
        }
        val source = kotlinx.io.files.SystemFileSystem.source(filePath)
        return read(source)
    }

    private fun errorWrongFormat(): Nothing = error("Wrong encoding line format")

    private fun error(msg: String): Nothing {
        throw ParserException(msg, SourceLocation(filePath ?: "", line, 0))
    }
}

