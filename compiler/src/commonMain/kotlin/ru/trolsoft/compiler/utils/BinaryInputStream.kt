package ru.trolsoft.compiler.utils

import kotlinx.io.RawSource

abstract class BinaryInputStream {
    abstract fun getStartAddress(): Int
    abstract fun getEndAddress(): Int

    abstract fun getByte(offset: Int): Int

    abstract fun seek(offset: Int)

    abstract fun eof(): Boolean

    abstract fun getAddress(): Int

    abstract fun read(): Int
    
    abstract fun toRawSource(): RawSource
}
