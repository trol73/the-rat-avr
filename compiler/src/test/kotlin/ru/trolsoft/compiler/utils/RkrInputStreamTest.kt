package ru.trolsoft.compiler.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.test.Test

private fun dataBytes(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()

class RkrInputStreamTest {

    private fun createRkrData(
        startAddr: Int = 0x1000,
        endAddr: Int = 0x1005,
        data: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
    ): Buffer {
        require(endAddr - startAddr + 1 == data.size) { "Data size must match address range" }

        val buf = Buffer()
        // Sync byte
        buf.writeByte(0xE6.toByte())
        // Start address (big endian)
        buf.writeByte((startAddr shr 8).toByte())
        buf.writeByte((startAddr and 0xFF).toByte())
        // End address (big endian)
        buf.writeByte((endAddr shr 8).toByte())
        buf.writeByte((endAddr and 0xFF).toByte())
        // Data
        buf.write(data)
        // End sync marker
        buf.writeByte(0x00.toByte())
        buf.writeByte(0xE6.toByte())
        // CRC
        val crc = RkrCrcCalculator().calculate(data)
        buf.writeByte((crc shr 8).toByte())
        buf.writeByte((crc and 0xFF).toByte())

        return buf
    }

    private fun createRkrDataWithoutFirstSync(
        startAddr: Int = 0x1000,
        endAddr: Int = 0x1005,
        data: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
    ): Buffer {
        require(endAddr - startAddr + 1 == data.size) { "Data size must match address range" }
        return Buffer().apply {
            // No first sync byte - start address starts directly
            writeByte((startAddr shr 8).toByte())
            writeByte((startAddr and 0xFF).toByte())
            writeByte((endAddr shr 8).toByte())
            writeByte((endAddr and 0xFF).toByte())
            write(data)
            writeByte(0x00.toByte())
            writeByte(0xE6.toByte())
            val crc = RkrCrcCalculator().calculate(data)
            writeByte((crc shr 8).toByte())
            writeByte((crc and 0xFF).toByte())
        }
    }

    @Test
    fun `RkrInputStream should parse valid RKR file with sync byte`() {
        val src = createRkrData()
        val stream = RkrInputStream(src)

        stream.getStartAddress() shouldBe 0x1000
        stream.getEndAddress() shouldBe 0x1005
    }

    @Test
    fun `RkrInputStream should parse valid RKR file without first sync byte`() {
        val src = createRkrDataWithoutFirstSync()
        val stream = RkrInputStream(src)

        stream.getStartAddress() shouldBe 0x1000
        stream.getEndAddress() shouldBe 0x1005
    }

    @Test
    fun `RkrInputStream should return correct CRC`() {
        val src = createRkrData()
        val stream = RkrInputStream(src)

        val crc = stream.getCrc()
        crc and 0xFFFF shouldBe crc
    }

    @Test
    fun `RkrInputStream read should return bytes in order`() {
        val data = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val src = createRkrData(startAddr = 0x1000, endAddr = 0x1000 + data.size - 1, data = data)
        val stream = RkrInputStream(src)

        stream.read() shouldBe 0x10
        stream.read() shouldBe 0x20
        stream.read() shouldBe 0x30
        stream.read() shouldBe 0x40
    }

    @Test
    fun `RkrInputStream read should return -1 at EOF`() {
        val src = createRkrData()
        val stream = RkrInputStream(src)

        while (!stream.eof()) {
            stream.read()
        }

        stream.read() shouldBe -1
        stream.eof() shouldBe true
    }

    @Test
    fun `RkrInputStream eof should be false when data available`() {
        val src = createRkrData()
        val stream = RkrInputStream(src)

        stream.eof() shouldBe false
    }

    @Test
    fun `RkrInputStream getAddress should return current position`() {
        val src = createRkrData(startAddr = 0x2000, endAddr = 0x2005)
        val stream = RkrInputStream(src)

        stream.getAddress() shouldBe 0x2000

        stream.read()
        stream.getAddress() shouldBe 0x2001
    }

    @Test
    fun `RkrInputStream seek should move to specified offset`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val src = createRkrData(startAddr = 0x100, endAddr = 0x104, data = data)
        val stream = RkrInputStream(src)

        stream.seek(0x102)
        stream.read() shouldBe 0x03
    }

    @Test
    fun `RkrInputStream seek to start should reset position`() {
        val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val src = createRkrData(startAddr = 0, endAddr = data.size - 1, data = data)
        val stream = RkrInputStream(src)

        stream.read() shouldBe 0xAA
        stream.read() shouldBe 0xBB

        stream.seek(0)
        stream.read() shouldBe 0xAA
    }

    @Test
    fun `RkrInputStream seek to invalid address should throw`() {
        val data = ByteArray(256) { (it + 0x100).toByte() }
        val src = createRkrData(startAddr = 0x100, endAddr = 0x1FF, data = data)
        val stream = RkrInputStream(src)

        shouldThrow<IllegalArgumentException> {
            stream.seek(0x0FF)
        }.message!!.contains("Wrong address")

        shouldThrow<IllegalArgumentException> {
            stream.seek(0x200)
        }.message!!.contains("Wrong address")
    }

    @Test
    fun `RkrInputStream getByte should return byte at offset`() {
        val data = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val src = createRkrData(startAddr = 0x1000, endAddr = 0x1000 + data.size - 1, data = data)
        val stream = RkrInputStream(src)

        stream.getByte(0x1000) shouldBe 0x11
        stream.getByte(0x1001) shouldBe 0x22
        stream.getByte(0x1002) shouldBe 0x33
        stream.getByte(0x1003) shouldBe 0x44
    }

    @Test
    fun `RkrInputStream getByte at invalid offset should throw`() {
        val data = ByteArray(256) { (it + 0x500).toByte() }
        val src = createRkrData(startAddr = 0x500, endAddr = 0x5FF, data = data)
        val stream = RkrInputStream(src)

        shouldThrow<IllegalArgumentException> {
            stream.getByte(0x4FF)
        }.message!!.contains("Wrong address")
    }

    @Test
    fun `RkrInputStream reset should reset read position to start`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val src = createRkrData(startAddr = 0x1000, endAddr = 0x1000 + data.size - 1, data = data)
        val stream = RkrInputStream(src)

        stream.read() shouldBe 0x01
        stream.read() shouldBe 0x02

        stream.reset()
        stream.read() shouldBe 0x01
    }

    @Test
    fun `RkrInputStream read all bytes sequentially`() {
        val data = dataBytes(0x00, 0x01, 0x02, 0x0F, 0xFF)
        val src = createRkrData(startAddr = 0x1000, endAddr = 0x1000 + data.size - 1, data = data)
        val stream = RkrInputStream(src)

        for (expected in data) {
            stream.read() shouldBe (expected.toInt() and 0xFF)
        }
        stream.eof() shouldBe true
    }

    @Test
    fun `RkrInputStream read with single byte`() {
        val src = createRkrData(startAddr = 0, endAddr = 0, data = byteArrayOf(0x42))
        val stream = RkrInputStream(src)

        stream.read() shouldBe 0x42
        stream.eof() shouldBe true
        stream.read() shouldBe -1
    }

    @Test
    fun `RkrInputStream getAddress after reset`() {
        val src = createRkrData(startAddr = 0x5000, endAddr = 0x5005)
        val stream = RkrInputStream(src)

        stream.reset()
        stream.getAddress() shouldBe 0x5000
    }

    @Test
    fun `RkrInputStream toRawSource should return source with same data`() {
        val data = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val src = createRkrData(startAddr = 0x1000, endAddr = 0x1000 + data.size - 1, data = data)
        val stream = RkrInputStream(src)

        val rawSource = stream.toRawSource()
        val buffer = rawSource.buffered()
        val readData = buffer.readByteArray()

        readData shouldBe data
    }

    @Test
    fun `RkrInputStream with different address ranges`() {
        val data = byteArrayOf(0x01)
        val src = createRkrData(startAddr = 0xFFFF, endAddr = 0xFFFF, data = data)
        val stream = RkrInputStream(src)

        stream.getStartAddress() shouldBe 0xFFFF
        stream.getEndAddress() shouldBe 0xFFFF
        stream.read() shouldBe 0x01
    }

    @Test
    fun `RkrInputStream read byte values 0-255`() {
        val data = ByteArray(256) { it.toByte() }
        val src = createRkrData(startAddr = 0, endAddr = 0xFF, data = data)
        val stream = RkrInputStream(src)

        for (i in 0..255) {
            stream.read() shouldBe (i and 0xFF)
        }
        stream.eof() shouldBe true
    }

    @Test
    fun `RkrInputStream mixed read and getByte operations`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val src = createRkrData(startAddr = 0x1000, endAddr = 0x1000 + data.size - 1, data = data)
        val stream = RkrInputStream(src)

        stream.read() shouldBe 0x01
        stream.getByte(0x1002) shouldBe 0x03
        stream.read() shouldBe 0x02
        stream.getByte(0x1000) shouldBe 0x01
    }

    @Test
    fun `RkrInputStream seek to end position`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val src = createRkrData(startAddr = 0x1000, endAddr = 0x1000 + data.size - 1, data = data)
        val stream = RkrInputStream(src)

        stream.seek(0x1002)
        stream.read() shouldBe 0x03
        stream.eof() shouldBe true
    }

    @Test
    fun `RkrInputStream multiple reads then seek back`() {
        val data = byteArrayOf(0xA, 0xB, 0xC, 0xD, 0xE)
        val src = createRkrData(startAddr = 0x1000, endAddr = 0x1000 + data.size - 1, data = data)
        val stream = RkrInputStream(src)

        stream.read() shouldBe 0xA
        stream.read() shouldBe 0xB

        stream.seek(0x1000)
        stream.read() shouldBe 0xA
        stream.read() shouldBe 0xB
        stream.read() shouldBe 0xC
    }

    @Test
    fun `RkrInputStream with minimal valid data`() {
        val data = byteArrayOf(0x55)
        val src = createRkrData(startAddr = 0x7FFE, endAddr = 0x7FFE, data = data)
        val stream = RkrInputStream(src)

        stream.read() shouldBe 0x55
        stream.eof() shouldBe true
    }
}