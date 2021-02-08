package ru.trolsoft.compiler.generator

import java.io.FileOutputStream
import java.io.OutputStream

fun BinaryOutput.saveToBinaryFile(stream: OutputStream, fillUnused: Int) {
    var offset = 0
    for (page in this.pages) {
        while (offset < page.startAddress) {
            stream.write(fillUnused)
            offset++
        }
        for (v in page) {
            stream.write(v.toInt() and 0xff)
        }
    }
}

fun BinaryOutput.saveToBinaryFile(fileName: String, fillUnused: Int) {
    saveToBinaryFile(FileOutputStream(fileName), fillUnused)
}