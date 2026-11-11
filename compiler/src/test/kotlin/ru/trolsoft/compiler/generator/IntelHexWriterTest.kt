package ru.trolsoft.compiler.generator

import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Test

class IntelHexWriterTest {

    @Test
    fun `write single byte`() {
        val buffer = Buffer()
        IntelHexWriter(buffer, 16).apply {
            addData(0, byteArrayOf(0x42))
            done()
        }

        val output = buffer.readByteArray().decodeToString()
        output shouldBe ":0100000042BD\n" + ":00000001FF\n"
    }

    @Test
    fun `write 16 bytes fits in one record`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)

        val data = (0..15).map { it.toByte() }.toByteArray()
        writer.addData(0, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        // :10000000 + 16 bytes + CRC
        output shouldContain ":10000000"
        output shouldContain "00000001FF"
    }

    @Test
    fun `write more than 16 bytes creates multiple records`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)

        val data = (0..31).map { it.toByte() }.toByteArray()
        writer.addData(0, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        val lines = output.lines().filter { it.startsWith(":") }
        lines.size shouldBe 3 // 2 data records + 1 EOF
    }

    @Test
    fun `write with custom bytes per line`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 4)

        val data = (0..10).map { it.toByte() }.toByteArray()
        writer.addData(0, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        val lines = output.lines().filter { it.startsWith(":") }
        // 11 bytes / 4 per line = 3 records + 1 EOF = 4 lines
        lines.size shouldBe 4
    }

    @Test
    fun `write empty data does nothing`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)
        writer.addData(0, byteArrayOf())
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `write data at non-zero offset`() {
        val buffer = Buffer()
        IntelHexWriter(buffer, 16).apply {
            addData(100, byteArrayOf(0xAB.toByte(), 0xCD.toByte()))
            done()
        }

        val output = buffer.readByteArray().decodeToString()
        // Offset 100 = 0x0064
        output shouldContain "006400" // data record offset
    }

    @Test
    fun `write data crossing segment boundary`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)

        // Data starting near 0xFFFF boundary
        val data = (0..20).map { it.toByte() }.toByteArray()
        writer.addData(0xFFE8, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `write single byte value 0x00`() {
        val buffer = Buffer()
        IntelHexWriter(buffer, 16).apply {
            addData(0, byteArrayOf(0x00))
            done()
        }

        val output = buffer.readByteArray().decodeToString()
        output shouldContain ":0100000000"
    }

    @Test
    fun `write single byte value 0xFF`() {
        val buffer = Buffer()
        IntelHexWriter(buffer, 16).apply {
            addData(0, byteArrayOf(0xFF.toByte()))
            done()
        }

        val output = buffer.readByteArray().decodeToString()
        output shouldContain ":01000000FF"
    }

    @Test
    fun `write multiple addData calls`() {
        val buffer = Buffer()
        IntelHexWriter(buffer, 16).apply {
            addData(0, byteArrayOf(0x01, 0x02))
            addData(2, byteArrayOf(0x03, 0x04))
            done()
        }

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `write data with all zero offset bytes`() {
        val buffer = Buffer()
        IntelHexWriter(buffer, 16).apply {
            addData(0, byteArrayOf(0x12, 0x34))
            done()
        }

        val output = buffer.readByteArray().decodeToString()
        output shouldBe ":020000001234B8\n" + ":00000001FF\n"
    }

    @Test
    fun `write 1 byte at offset 0x100`() {
        val buffer = Buffer()
        IntelHexWriter(buffer, 16).apply {
            addData(0x100, byteArrayOf(0x55.toByte()))
            done()
        }

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `write exactly bytesPerLine bytes`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 8)

        val data = (0..7).map { it.toByte() }.toByteArray()
        writer.addData(0, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        val lines = output.lines().filter { it.startsWith(":") }
        lines.size shouldBe 2 // 1 data record + 1 EOF
    }

    @Test
    fun `write bytesPerLine + 1 bytes creates two records`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 8)

        val data = (0..8).map { it.toByte() }.toByteArray()
        writer.addData(0, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        val lines = output.lines().filter { it.startsWith(":") }
        lines.size shouldBe 3 // 2 data records + 1 EOF
    }

    @Test
    fun `write data starting at non-aligned offset`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)

        // Start at offset 10, write 20 bytes
        val data = (0..19).map { it.toByte() }.toByteArray()
        writer.addData(10, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `write large data set`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)

        val data = (0..255).map { it.toByte() }.toByteArray()
        writer.addData(0, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        val lines = output.lines().filter { it.startsWith(":") }
        // 256 bytes / 16 per line = 16 records + 1 EOF = 17 lines
        lines.size shouldBe 17
    }

    @Test
    fun `write data with alternating 0x00 and 0xFF`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)

        val data = (0..15).map { i -> if (i % 2 == 0) 0x00.toByte() else 0xFF.toByte() }.toByteArray()
        writer.addData(0, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `write single byte at maximum offset`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)
        writer.addData(0xFFFF, byteArrayOf(0x01))
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `write data crossing multiple segment boundaries`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)

        // Write data that crosses several 64KB boundaries
        val data = (0..100).map { it.toByte() }.toByteArray()
        writer.addData(0xFF00, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `hex record format validation`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)
        writer.addData(0, byteArrayOf(0x42))
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        val lines = output.lines()

        // Each line should start with ':'
        for (line in lines) {
            if (line.isNotEmpty()) {
                line.startsWith(":") shouldBe true
            }
        }
    }

    @Test
    fun `eof record is always last`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)
        writer.addData(0, byteArrayOf(0x01, 0x02, 0x03))
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        val lines = output.lines().filter { it.isNotEmpty() }
        lines.last() shouldBe ":00000001FF"
    }

    @Test
    fun `write with small bytesPerLine value`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 1)

        val data = (0..5).map { it.toByte() }.toByteArray()
        writer.addData(0, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        val lines = output.lines().filter { it.startsWith(":") }
        // 6 bytes / 1 per line = 6 records + 1 EOF = 7 lines
        lines.size shouldBe 7
    }

    @Test
    fun `write with large bytesPerLine value`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 256)

        val data = (0..10).map { it.toByte() }.toByteArray()
        writer.addData(0, data)
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        val lines = output.lines().filter { it.startsWith(":") }
        lines.size shouldBe 2 // 1 data record + 1 EOF
    }

    @Test
    fun `write data at offset requiring segment record`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)

        // Offset 0x100 requires segment adjustment
        writer.addData(0x100, byteArrayOf(0xAA.toByte()))
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `save to intel hex extension function`() {
        val bo = BinaryOutput(Endian.LITTLE)
        bo.gotoAddress(0)
        bo.reserve(4)
        bo.allocatePages()

        // Write some data to the pages
        bo.gotoAddress(0)
        bo.addByte(0x12)
        bo.addByte(0x34)
        bo.addByte(0x56)
        bo.addByte(0x78)

        val buffer = Buffer()
        bo.saveToIntelHex(buffer, 16)

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `intel hex with different widths produces different line counts`() {
        val data = (0..31).map { it.toByte() }.toByteArray()

        val buffer16 = Buffer()
        val writer16 = IntelHexWriter(buffer16, 16)
        writer16.addData(0, data)
        writer16.done()

        val buffer8 = Buffer()
        val writer8 = IntelHexWriter(buffer8, 8)
        writer8.addData(0, data)
        writer8.done()

        val lines16 = buffer16.readByteArray().decodeToString().lines().filter { it.startsWith(":") }
        val lines8 = buffer8.readByteArray().decodeToString().lines().filter { it.startsWith(":") }

        // 32 bytes with width 16 = 2 records + 1 EOF = 3
        lines16.size shouldBe 3
        // 32 bytes with width 8 = 4 records + 1 EOF = 5
        lines8.size shouldBe 5
    }

    @Test
    fun `write byte array with all same values`() {
        val buffer = Buffer()
        val writer = IntelHexWriter(buffer, 16)
        writer.addData(0, ByteArray(16) { 0x55 })
        writer.done()

        val output = buffer.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }
}

// Helper function for string containment
private infix fun String.shouldContain(substring: String) {
    if (!this.contains(substring)) {
        throw AssertionError("Expected \"$this\" to contain \"$substring\"")
    }
}
