package ru.trolsoft.compiler.general

enum class DataType(
    val ramSize: Int
) {
    BYTE(1),
    WORD(2),
    DWORD(4),
    QWORD(8),
    PTR(2),
    PRGPTR(0),
    STRUCTURED(0);
}