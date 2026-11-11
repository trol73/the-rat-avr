package ru.trolsoft.compiler.utils

import kotlin.collections.get
import kotlin.collections.set
import kotlin.toString

private val HEX_CHAR_ARRAY = "0123456789ABCDEF".toCharArray()
private val HEX_BYTE_STRINGS = arrayOfNulls<String>(256)
private val STRING_OF_ZERO = arrayOf("", "0", "00", "000", "0000", "00000", "000000", "0000000", "00000000", "000000000", "0000000000")

fun isLineEnd(ch: Char): Boolean = ch == '\r' || ch == '\n'

fun isWhitespace(ch: Char): Boolean = ch == ' ' || ch == '\t'

fun isDigit(ch: Char): Boolean = ch in '0'..'9'

fun isHexDigit(ch: Char): Boolean = isDigit(ch) || ch in 'a'..'f' || ch in 'A'..'F'

fun isOctalDigit(ch: Char): Boolean = ch in '0'..'7'

fun isLetter(ch: Char): Boolean = ch in 'a'..'z' || ch in 'A'..'Z' || ch == '_'

fun isNumberChar(ch: Char, base: Int): Boolean {
    when (base) {
        2 -> return ch in '0'..'1'
        8 -> return isOctalDigit(ch)
        16 -> return isHexDigit(ch)
    }
    return false
}

fun parseNumber(s: String): Int? =
    when {
        s.startsWith("0x") || s.startsWith("0X") -> s.substring(2).toIntOrNull(16)
        s.startsWith("0b") || s.startsWith("0B") -> s.substring(2).toIntOrNull(2)
        s.startsWith('0') -> s.toIntOrNull(8)
        else -> s.toIntOrNull()
    }

fun isNumber(s: String) = parseNumber(s) != null

fun hexByte(b: Int): String {
    val v = b and 0xFF
    var result = HEX_BYTE_STRINGS[v]
    if (result == null) {
        result = HEX_CHAR_ARRAY[v.ushr(4)].toString() + HEX_CHAR_ARRAY[v and 0x0f]
        HEX_BYTE_STRINGS[v] = result
    }
    return result
}

fun hexWord(w: Int): String = "${hexByte(w.ushr(8))}${hexByte(w and 0xff)}"

fun hexDword(dw: Int): String = "${hexWord(dw.ushr(16))}${hexWord(dw and 0xffff)}"

