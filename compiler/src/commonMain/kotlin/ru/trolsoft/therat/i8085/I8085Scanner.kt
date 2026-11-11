package ru.trolsoft.therat.i8085

import ru.trolsoft.therat.arch.i8085.is8085Instruction
import ru.trolsoft.therat.arch.i8085.is8085Pair
import ru.trolsoft.therat.arch.i8085.is8085Register
import ru.trolsoft.therat.lang.Scanner

private val keywords = setOf(
    "proc", "var", "extern", "if", "else", "goto", "loop", "break", "continue", "as", "do", "while",
    "return", "use", "inline", "saveregs", "struct", "union", "absolute", "const" // "vectors", "typedef"
)

private val dataTypes = setOf(
    "byte", "word", "dword", "qword", "ptr",
    // "prgptr", "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int8_t", "int16_t", "int32_t", "int64_t", "char"
)

class I8085Scanner : Scanner() {
    override fun isAssemblerInstruction(s: String): Boolean = is8085Instruction(s)

    override fun isDataType(s: String): Boolean = dataTypes.contains(s)

    override fun isKeyword(s: String): Boolean = keywords.contains(s)

    override fun isRegister(s: String): Boolean = is8085Register(s) || is8085Pair(s)

    override fun checkKeyword(name: String, firstLineToken: Boolean, assemblerLine: Boolean): Boolean {
        return !assemblerLine
    }
}