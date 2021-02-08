package ru.trolsoft.therat.avr

import ru.trolsoft.therat.arch.avr.isAvrInstruction
import ru.trolsoft.therat.arch.avr.isAvrPair
import ru.trolsoft.therat.arch.avr.isAvrRegister
import ru.trolsoft.therat.lang.Scanner

private val preprocessorDirectives = setOf("define", "undef", "include", "ifdef", "ifndef",
        "if", "else", "elif", "endif", "warning", "error", "message")

private val keywords = setOf(
        "proc", "var", "pin", "extern", "if", "else", "goto", "loop", "break", "continue", "as", "do", "while",
        "return", "vectors", "use", "as", "inline", "saveregs"
)

private val dataTypes = setOf("byte", "word", "dword", "ptr", "prgptr")

class AvrScanner : Scanner() {
    override fun isAssemblerInstruction(s: String): Boolean = isAvrInstruction(s)

    override fun isDataType(s: String): Boolean = dataTypes.contains(s)

    override fun isKeyword(s: String): Boolean = keywords.contains(s)

    override fun isPreprocessorDirective(s: String): Boolean = preprocessorDirectives.contains(s)

    override fun isRegister(s: String): Boolean = isAvrRegister(s) || isAvrPair(s)

    override fun checkKeyword(name: String, firstLineToken: Boolean, assemblerLine: Boolean): Boolean {
        if (assemblerLine) {
            return false
        }
        if (name == "pin") {
            return firstLineToken
        }
        return true
    }
}