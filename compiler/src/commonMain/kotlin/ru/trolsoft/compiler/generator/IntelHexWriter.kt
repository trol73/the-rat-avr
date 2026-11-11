package ru.trolsoft.compiler.generator

import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import ru.trolsoft.compiler.utils.hexByte

class IntelHexWriter(
    sink: RawSink,
    private val bytesPerLine: Int = 16
) {
    private val buffered = sink.buffered()

    private var buf: ByteArray = ByteArray(bytesPerLine)
    private var segmentAddress: Int = 0

    fun addData(startOffset: Int, data: ByteArray) {
        var offset = startOffset
        if (data.isEmpty()) {
            return
        }

        var pos = 0
        var bytesToAdd = data.size
        while (bytesToAdd > 0) {
            val partSize = when {
                bytesToAdd <= bytesPerLine -> bytesToAdd
                offset % bytesPerLine != 0 -> bytesPerLine - offset % bytesPerLine
                else -> bytesPerLine
            }
            prepareBuf(partSize)
            data.copyInto(
                destination = buf,
                destinationOffset = 0,
                startIndex = pos,
                endIndex = pos + partSize
            )

            if (offset + partSize - 1 > segmentAddress + 0xffff) {
                val nextSegment = offset + bytesPerLine shr 4 shl 4
                addSegmentRecord(nextSegment)
                segmentAddress = nextSegment
            }
            addDataRecord(offset and 0xffff, buf)
            bytesToAdd -= partSize
            offset += partSize
            pos += partSize
        }
    }

    private fun prepareBuf(size: Int): ByteArray {
        if (buf.size == size) {
            return buf
        }
        buf = ByteArray(size)
        return buf
    }

    private fun addSegmentRecord(offset: Int) {
        val paragraph = offset shr 4
        val hi = paragraph shr 8 and 0xff
        val lo = paragraph and 0xff
        var crc = 2 + 2 + hi + lo
        crc = -crc and 0xff
        val rec = ":02000002" + hexByte(hi) + hexByte(lo) + hexByte(crc)
        writeStr(rec)
    }


    private fun addEofRecord() {
        writeStr(":00000001FF")
    }

    private fun writeStr(s: String) {
        buffered.writeString(s)
        buffered.writeByte('\n'.code.toByte())
    }

    private fun addDataRecord(offset: Int, data: ByteArray) {
        val hi = offset shr 8 and 0xff
        val lo = offset and 0xff
        var crc = data.size + hi + lo
        var rec = ":" + hexByte(data.size) + hexByte(hi) + hexByte(lo) + "00"
        for (d in data) {
            rec += hexByte(d.toInt())
            crc += d.toInt()
        }
        crc = -crc and 0xff
        rec += hexByte(crc)
        writeStr(rec)
    }


    fun done() {
        addEofRecord()
        buffered.flush()
    }
}


fun BinaryOutput.saveToIntelHex(sink: RawSink, width: Int = 32) {
    val hexWriter = IntelHexWriter(sink, width)
    for (page in this.pages) {
        hexWriter.addData(page.startAddress, page.data!!)
    }
    hexWriter.done()
}

fun BinaryOutput.saveToIntelHex(fileName: String, width: Int = 32) {
    saveToIntelHex(SystemFileSystem.sink(Path(fileName)), width)
}
