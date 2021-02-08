package ru.trolsoft.compiler.utils

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