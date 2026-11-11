package ru.trolsoft.compiler.utils

import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.readByteArray

class RkrOutputStream(private val buf: kotlinx.io.Buffer = kotlinx.io.Buffer()) {
    
    fun writeTo(sink: RawSink, startOffset: Int) {
        val buffered = sink.buffered()
        val data = buf.readByteArray()
        val crc = RkrCrcCalculator().calculate(data)

        buffered.writeByte(0xE6.toByte())
        buffered.writeByte((startOffset shr 8).toByte())
        buffered.writeByte((startOffset and 0xff).toByte())
        val endOffset = startOffset + data.size
        buffered.writeByte((endOffset shr 8).toByte())
        buffered.writeByte((endOffset and 0xff).toByte())

        buffered.write(buf, buf.size)

        buffered.writeByte(0x00.toByte())
        buffered.writeByte(0xE6.toByte())
        buffered.writeByte((crc shr 8).toByte())
        buffered.writeByte((crc and 0xff).toByte())
        buffered.flush()
    }
}
