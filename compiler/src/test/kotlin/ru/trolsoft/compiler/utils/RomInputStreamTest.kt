package ru.trolsoft.compiler.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.test.Test

private fun dataBytes(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()

class RomInputStreamTest {

    @Test
    fun `RomInputStream should load data from source`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        stream.getStartAddress() shouldBe 0
        stream.getEndAddress() shouldBe 4
    }

    @Test
    fun `RomInputStream should respect startOffset`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src, startOffset = 0x1000)
        
        stream.getStartAddress() shouldBe 0x1000
        stream.getEndAddress() shouldBe 0x1002
    }

    @Test
    fun `RomInputStream read should return bytes in order`() {
        val data = byteArrayOf(0x10, 0x20, 0x30)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        stream.read() shouldBe 0x10
        stream.read() shouldBe 0x20
        stream.read() shouldBe 0x30
    }

    @Test
    fun `RomInputStream read should return -1 at EOF`() {
        val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        stream.read() shouldBe 0xAA
        stream.read() shouldBe 0xBB
        stream.read() shouldBe -1
    }

    @Test
    fun `RomInputStream eof should be false when data available`() {
        val src = Buffer().apply { write(dataBytes(1, 2, 3)) }
        val stream = RomInputStream(src)
        
        stream.eof() shouldBe false
    }

    @Test
    fun `RomInputStream eof should be true when all data read`() {
        val src = Buffer().apply { write(dataBytes(1, 2, 3)) }
        val stream = RomInputStream(src)
        
        while (!stream.eof()) {
            stream.read()
        }
        stream.eof() shouldBe true
    }

    @Test
    fun `RomInputStream getAddress should return current position`() {
        val src = Buffer().apply { write(dataBytes(1, 2, 3)) }
        val stream = RomInputStream(src)
        
        stream.getAddress() shouldBe 0
        
        stream.read()
        stream.getAddress() shouldBe 1
    }

    @Test
    fun `RomInputStream getAddress with startOffset`() {
        val src = Buffer().apply { write(dataBytes(1, 2)) }
        val stream = RomInputStream(src, startOffset = 0x8000)
        
        stream.getAddress() shouldBe 0x8000
        
        stream.read()
        stream.getAddress() shouldBe 0x8001
    }

    @Test
    fun `RomInputStream seek should move to specified offset`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        stream.seek(3)
        stream.read() shouldBe 0x04
    }

    @Test
    fun `RomInputStream seek with startOffset`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src, startOffset = 0x100)
        
        stream.seek(0x101)
        stream.read() shouldBe 0x02
    }

    @Test
    fun `RomInputStream seek to invalid address should throw`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src, startOffset = 0x100)
        
        shouldThrow<IllegalArgumentException> {
            stream.seek(0x0FF)
        }.message!!.contains("Wrong address")
        
        shouldThrow<IllegalArgumentException> {
            stream.seek(0x103)
        }.message!!.contains("Wrong address")
    }

    @Test
    fun `RomInputStream getByte should return byte at offset`() {
        val data = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        stream.getByte(0) shouldBe 0x11
        stream.getByte(1) shouldBe 0x22
        stream.getByte(2) shouldBe 0x33
        stream.getByte(3) shouldBe 0x44
    }

    @Test
    fun `RomInputStream getByte with startOffset`() {
        val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src, startOffset = 0x500)
        
        stream.getByte(0x500) shouldBe 0xAA
        stream.getByte(0x501) shouldBe 0xBB
    }

    @Test
    fun `RomInputStream getByte at invalid offset should throw`() {
        val src = Buffer().apply { write(dataBytes(1, 2, 3)) }
        val stream = RomInputStream(src)
        
        shouldThrow<IllegalArgumentException> {
            stream.getByte(-1)
        }
        
        shouldThrow<IllegalArgumentException> {
            stream.getByte(3)
        }
    }

    @Test
    fun `RomInputStream reset read position`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        stream.read() shouldBe 0x01
        stream.seek(0)
        stream.read() shouldBe 0x01
    }

    @Test
    fun `RomInputStream toRawSource should return source with same data`() {
        val data = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xC0.toByte(), 0xDE.toByte())
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        val rawSource = stream.toRawSource()
        val buffer = rawSource.buffered()
        val readData = buffer.readByteArray()
        
        readData shouldBe data
    }

    @Test
    fun `RomInputStream toRawSource with startOffset`() {
        val data = byteArrayOf(0x01, 0x02)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src, startOffset = 0x1000)
        
        val rawSource = stream.toRawSource()
        val buffer = rawSource.buffered()
        val readData = buffer.readByteArray()
        
        readData shouldBe data
    }

    @Test
    fun `RomInputStream single byte`() {
        val src = Buffer().apply { write(dataBytes(0x42)) }
        val stream = RomInputStream(src)
        
        stream.getStartAddress() shouldBe 0
        stream.getEndAddress() shouldBe 0
        stream.read() shouldBe 0x42
        stream.eof() shouldBe true
    }

    @Test
    fun `RomInputStream empty data`() {
        val src = Buffer()
        val stream = RomInputStream(src)
        
        stream.getStartAddress() shouldBe 0
        stream.getEndAddress() shouldBe -1
        stream.read() shouldBe -1
        stream.eof() shouldBe true
    }

    @Test
    fun `RomInputStream read all byte values`() {
        val data = ByteArray(256) { it.toByte() }
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        for (i in 0..255) {
            stream.read() shouldBe (i and 0xFF)
        }
        stream.eof() shouldBe true
    }

    @Test
    fun `RomInputStream mixed operations`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        stream.read() shouldBe 0x01
        stream.getByte(2) shouldBe 0x03
        stream.seek(1)
        stream.read() shouldBe 0x02
        stream.read() shouldBe 0x03
    }

    @Test
    fun `RomInputStream large data`() {
        val data = ByteArray(1024) { it.toByte() }
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        stream.getStartAddress() shouldBe 0
        stream.getEndAddress() shouldBe 1023
        
        var readCount = 0
        while (!stream.eof()) {
            stream.read()
            readCount++
        }
        readCount shouldBe 1024
    }

    @Test
    fun `RomInputStream multiple seeks`() {
        val data = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D, 0x0E)
        val src = Buffer().apply { write(data) }
        val stream = RomInputStream(src)
        
        stream.seek(0)
        stream.read() shouldBe 0x0A
        stream.seek(2)
        stream.read() shouldBe 0x0C
        stream.seek(4)
        stream.read() shouldBe 0x0E
        stream.seek(1)
        stream.read() shouldBe 0x0B
    }
}
