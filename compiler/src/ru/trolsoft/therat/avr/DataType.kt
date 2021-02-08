package ru.trolsoft.therat.avr

import ru.trolsoft.therat.lang.DataTypeToken

enum class DataType(
        val ramSize: Int
) {
    BYTE(1),
    WORD(2),
    DWORD(4),
    PTR(2),
    PRGPTR(0);

    companion object {
        fun fromToken(t: DataTypeToken): DataType {
            return when (t.name) {
                "byte" -> BYTE
                "word" -> WORD
                "dword" -> DWORD
                "ptr" -> PTR
                "prgptr" -> PRGPTR
                else -> throw Exception("Internal error, wrong data type: " + t.name)
            }
        }
    }
}