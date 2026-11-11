package ru.trolsoft.compiler.generator

import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

fun BinaryOutput.saveToBinaryFile(stream: RawSink, fillUnused: Int, startOffset: Int = 0) {
    val buf = stream.buffered()
    var offset = 0
    for (page in this.pages) {
        while (offset < page.startAddress) {
            if (offset >= startOffset) {
                buf.writeByte(fillUnused.toByte())
                offset++
            }
        }
        for (v in page) {
            buf.writeByte((v.toInt() and 0xff).toByte())
            offset++
        }
    }
    buf.close()
}

fun BinaryOutput.saveToBinaryFile(fileName: String, fillUnused: Int) {
    saveToBinaryFile(SystemFileSystem.sink(Path(fileName)), fillUnused)
}