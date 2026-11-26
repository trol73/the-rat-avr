package ru.trolsoft.therat.viper

import ru.trolsoft.therat.arch.viper.isViperInstruction
import ru.trolsoft.therat.arch.viper.isViperPair
import ru.trolsoft.therat.arch.viper.isViperRegister
import ru.trolsoft.therat.lang.Scanner

private val keywords = setOf(
    "proc", "var", "extern", "if", "else", "goto", "loop", "break", "continue", "as", "do", "while",
    "return", "use", "reuse", "inline", "saveregs", "struct", "union", "absolute", "const", "template"
)

private val dataTypes = setOf(
    "byte", "word", "dword", "qword", "ptr",
    "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int8_t", "int16_t", "int32_t", "int64_t", "char"
)

class ViperScanner : Scanner() {
    override fun isAssemblerInstruction(s: String): Boolean = isViperInstruction(s)

    override fun isDataType(s: String): Boolean = dataTypes.contains(s)

    override fun isKeyword(s: String): Boolean = keywords.contains(s)

    override fun isRegister(s: String): Boolean = isViperRegister(s) || isViperPair(s)

    override fun checkKeyword(name: String, firstLineToken: Boolean, assemblerLine: Boolean): Boolean {
        return !assemblerLine
    }
}