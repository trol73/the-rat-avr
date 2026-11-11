package ru.trolsoft.compiler.utils

class RkrCrcCalculator {
    private var crcLo = 0
    private var crcHi = 0
    fun nextByte(b: Int) {
        crcLo += b
        crcHi += b
        crcHi += (crcLo shr 8) and 1
        crcLo = crcLo and 0xff
        crcHi = crcHi and 0xff
    }

    fun crc() = crcLo + (crcHi shl 8)

    fun reset() {
        crcLo = 0
        crcHi = 0
    }

    fun calculate(data: ByteArray): Int {
        reset()
        for (b in data) {
            nextByte(b.toInt() and 0xff)
        }
        return crc()
    }
}