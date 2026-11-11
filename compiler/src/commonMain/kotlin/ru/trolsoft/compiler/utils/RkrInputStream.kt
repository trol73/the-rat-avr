package ru.trolsoft.compiler.utils

import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray

class RkrInputStream(
    src: RawSource
) : BinaryInputStream() {
    private val crcCalculator = RkrCrcCalculator()
    private val startAddress: Int
    private val endAddress: Int
    private val readCrc: Int
    private val data: ByteArray

    private var readPos: Int

    init {
        val buffered = src.buffered()
        val first = buffered.nextByte()
        val withSyncByte = first == SYNC_BYTE
        startAddress = if (withSyncByte) {
            (buffered.nextByte() shl 8) + buffered.nextByte()
        } else {
            (first shl 8) + buffered.nextByte()
        }
        endAddress = (buffered.nextByte() shl 8) + buffered.nextByte()

        val expectedLen = endAddress - startAddress + 1
        if (expectedLen > FILE_SIZE_LIMIT) {
            throw IllegalArgumentException("File too big")
        }
        
        val buf = Buffer()
        var pos = 0
        while (pos < expectedLen) {
            val b = buffered.nextByte()
            buf.writeByte(b.toByte())
            pos++
            crcCalculator.nextByte(b)
        }

        var endSync = buffered.nextByte()
        while (endSync != SYNC_BYTE) {
            if (endSync != 0) {
                throw IllegalArgumentException("Last sync byte not found (${hexByte(endSync)} found)")
            }
            endSync = buffered.nextByte()
            crcCalculator.nextByte(0)
        }
        readCrc = (buffered.nextByte() shl 8) + buffered.nextByte()
        val calculatedCrc = crcCalculator.crc()
        data = buf.readByteArray()
        readPos = 0
        if (readCrc != calculatedCrc) {
            println("Wrong data CRC (expected ${hexWord(readCrc)} but found ${hexWord(calculatedCrc)})")
        }
    }

    fun getCrc(): Int = readCrc

    override fun getStartAddress(): Int = startAddress

    override fun getEndAddress(): Int = endAddress

    override fun seek(offset: Int) {
        checkOffset(offset)
        readPos = offset - startAddress
    }

    private fun Source.nextByte(): Int =
        try {
            readByte().toInt() and 0xff
        } catch (_: EOFException) {
            throw UnexpectedEofException()
        }

    private fun checkOffset(offset: Int): Int {
        if (offset < startAddress || offset > endAddress) {
            throw IllegalArgumentException("Wrong address (${hexWord(offset)}), available region is ${hexWord(startAddress)} .. ${hexWord(endAddress)}")
        }
        return offset
    }

    override fun read(): Int =
        if (readPos < data.size) {
            data[readPos++].toInt() and 0xff
        } else {
            -1
        }

    fun reset() {
        readPos = 0
    }

    override fun eof() = readPos >= data.size

    override fun getAddress() = readPos + startAddress

    override fun getByte(offset: Int): Int = data[checkOffset(offset) - startAddress].toInt() and 0xff

    override fun toRawSource(): RawSource =
        Buffer().apply { write(data) }


    companion object {
        private const val SYNC_BYTE = 0xE6
        private const val FILE_SIZE_LIMIT = 0x7600
    }
}