package ru.trolsoft.compiler.utils

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray

class RomInputStream(
    src: RawSource,
    startOffset: Int = 0
) : BinaryInputStream() {
    private val startAddress: Int = startOffset
    private val endAddress: Int
    private val data: ByteArray

    private var readPos: Int

    init {
        val buffered = src.buffered()
        val buf = Buffer()
        while (!buffered.exhausted()) {
            buf.writeByte(buffered.readByte())
        }
        data = buf.readByteArray()
        endAddress = startAddress + data.size - 1
        readPos = 0
    }

    override fun getStartAddress(): Int = startAddress

    override fun getEndAddress(): Int = endAddress

    override fun getByte(offset: Int): Int = data[checkOffset(offset) - startAddress].toInt() and 0xff

    override fun seek(offset: Int) {
        checkOffset(offset)
        readPos = offset - startAddress
    }

    override fun eof(): Boolean = readPos >= data.size

    override fun getAddress(): Int = startAddress + readPos

    override fun read(): Int =
        if (readPos < data.size) {
            data[readPos++].toInt() and 0xff
        } else {
            -1
        }

    private fun checkOffset(offset: Int): Int {
        if (offset !in startAddress..endAddress) {
            throw IllegalArgumentException("Wrong address (${hexWord(offset)}), available region is ${hexWord(startAddress)} .. ${hexWord(endAddress)}")
        }
        return offset
    }
    
    override fun toRawSource(): RawSource =
        Buffer().apply { write(data) }
}
