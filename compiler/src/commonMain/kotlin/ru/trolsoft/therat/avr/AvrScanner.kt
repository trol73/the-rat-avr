package ru.trolsoft.therat.avr

import ru.trolsoft.therat.arch.avr.isAvrInstruction
import ru.trolsoft.therat.arch.avr.isAvrPairName
import ru.trolsoft.therat.arch.avr.isAvrRegister
import ru.trolsoft.therat.lang.Scanner

private val keywords = setOf(
    "proc", "var", "pin", "bus", "extern", "if", "else", "goto", "loop", "break", "continue", "as", "do", "while",
    "return", "vectors", "use", "reuse", "inline", "saveregs", "struct", "union", "typedef", "const"
)

private val dataTypes = setOf(
    "byte", "word", "dword", "qword", "ptr", "prgptr",
    "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int8_t", "int16_t", "int32_t", "int64_t", "char"
)

class AvrScanner : Scanner() {
    override fun isAssemblerInstruction(s: String): Boolean = isAvrInstruction(s)

    override fun isDataType(s: String): Boolean = dataTypes.contains(s)

    override fun isKeyword(s: String): Boolean = keywords.contains(s)

    override fun isRegister(s: String): Boolean = isAvrRegister(s) || isAvrPairName(s)

    override fun checkKeyword(name: String, firstLineToken: Boolean, assemblerLine: Boolean): Boolean {
        if (assemblerLine) {
            return false
        }
        if (name == "pin" || name == "bus") {
            return firstLineToken
        }
        return true
    }
}