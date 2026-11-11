package ru.trolsoft.compiler.generator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.therat.arch.*

class BinaryFileGeneratorTest {

    private val dummyLocation = SourceLocation("test", 1, 1)

    @Test
    fun `generate simple byte sequence`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addByte(0x12, dummyLocation)
            addByte(0x34, dummyLocation)
            addByte(0x56, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        stream.readByteArray() shouldBe byteArrayOf(0x12, 0x34, 0x56)
    }

    @Test
    fun `generate word sequence little endian`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addWord(0x1234, dummyLocation)
            addWord(0x5678, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        // Little endian: low byte first
        stream.readByteArray() shouldBe byteArrayOf(0x34, 0x12, 0x78, 0x56)
    }

    @Test
    fun `generate word sequence big endian`() {
        val gen = BinaryFileGenerator(Endian.BIG, OutputFormat.BINARY).apply {
            addWord(0x1234, dummyLocation)
            addWord(0x5678, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        // Big endian: high byte first
        stream.readByteArray() shouldBe byteArrayOf(0x12, 0x34, 0x56, 0x78)
    }

    @Test
    fun `generate dword sequence little endian`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addDword(0x12345678, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        // Little endian: 78 56 34 12
        stream.readByteArray() shouldBe byteArrayOf(0x78, 0x56, 0x34, 0x12)
    }

    @Test
    fun `generate dword sequence big endian`() {
        val gen = BinaryFileGenerator(Endian.BIG, OutputFormat.BINARY).apply {
            addDword(0x12345678, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        // Big endian: 12 34 56 78
        stream.readByteArray() shouldBe byteArrayOf(0x12, 0x34, 0x56, 0x78)
    }

    @Test
    fun `generate with label definition and reference`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addByte(0xAA, dummyLocation)
            addLabel("myLabel", dummyLocation)
            addByte(0xBB, dummyLocation)
        }

        // Reference to label
        val labelArg = ArgLabel(
            name = "myLabel",
            localName = "myLabel",
            location = dummyLocation,
            resolver = { currentOffset, labelOffset -> labelOffset }
        )
        gen.addCommand(dummyLocation, TestByteCmd(), labelArg)

        val stream = Buffer()
        gen.write(stream)

        val bytes = stream.readByteArray()
        bytes[0] shouldBe 0xAA.toByte()
        bytes[1] shouldBe 0xBB.toByte() // label offset
    }

    @Test
    fun `label already defined throws exception`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addLabel("dup", dummyLocation)
        }

        shouldThrow<GeneratorException> {
            gen.addLabel("dup", dummyLocation)
        }.message shouldBe "Label already defined"
    }

    @Test
    fun `org directive changes offset`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addByte(0x01, dummyLocation)
            org(100)
            addByte(0x02, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        val bytes = stream.readByteArray()
        bytes shouldHaveSize 101
        bytes[0] shouldBe 0x01
        bytes[100] shouldBe 0x02
    }

    @Test
    fun `calculate size with multiple blocks`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addByte(0x01, dummyLocation)
            addByte(0x02, dummyLocation)
            org(50)
            addByte(0x03, dummyLocation)
        }

        gen.calculateSize() shouldBe 51
    }

    @Test
    fun `calculate size with overlapping blocks`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addByte(0x01, dummyLocation)
            addByte(0x02, dummyLocation)
            org(10)
            addByte(0x03, dummyLocation)
            addByte(0x04, dummyLocation)
        }

        gen.calculateSize() shouldBe 12
    }

    @Test
    fun `validation rule passes`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addLabel("start", dummyLocation)
            addByte(0x01, dummyLocation)
            addByte(0x02, dummyLocation)
            addByte(0x03, dummyLocation)
            addLabel("end", dummyLocation)

            addValidation(dummyLocation, "start", "end", 10)
        }

        // Should not throw
        gen.validate()
    }

    @Test
    fun `validation rule fails when too large`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addLabel("start", dummyLocation)
            for (i in 0 until 20) {
                addByte(0x00, dummyLocation)
            }
            addLabel("end", dummyLocation)

            addValidation(dummyLocation, "start", "end", 10)
        }

        shouldThrow<GeneratorException> {
            gen.validate()
        }.message shouldBe "Block too large: expected: 10, but found 20 bytes"
    }

    @Test
    fun `label not found throws exception`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY)

        val labelArg = ArgLabel(
            name = "nonexistent",
            localName = "nonexistent",
            location = dummyLocation,
            resolver = { _, _ -> 0 }
        )

        shouldThrow<GeneratorException> {
            gen.getLabelOffset(labelArg)
        }.message shouldBe "Label not found: nonexistent"
    }

    @Test
    fun `resolve returns null for undefined label`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY)
        gen.resolve("undefined") shouldBe null
    }

    @Test
    fun `resolve returns correct offset for defined label`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addByte(0x00, dummyLocation)
            addByte(0x00, dummyLocation)
            addLabel("testLabel", dummyLocation)
        }

        gen.resolve("testLabel") shouldBe 2
    }

    @Test
    fun `generate intel hex format`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.INTEL_HEX).apply {
            addByte(0x01, dummyLocation)
            addByte(0x02, dummyLocation)
            addByte(0x03, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        val output = stream.readByteArray().decodeToString()
        output shouldContain ":03000000010203"
        output shouldContain "00000001FF"
    }

    @Test
    fun `generate binary format with fill empty`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY, fillEmpty = 0xFF).apply {
            addByte(0x01, dummyLocation)
            addByte(0x02, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        val bytes = stream.readByteArray()
        // Should have fill bytes for empty pages
        bytes.size shouldBe 2
        bytes[0] shouldBe 0x01
        bytes[1] shouldBe 0x02
    }

    @Test
    fun `multiple commands in sequence`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addByte(0x11, dummyLocation)
            addWord(0x2233, dummyLocation)
            addDword(0x44556677, dummyLocation)
            addByte(0x88, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        val bytes = stream.readByteArray()
        bytes.size shouldBe 8
        bytes[0] shouldBe 0x11
        bytes[1] shouldBe 0x33 // 0x2233 little endian
        bytes[2] shouldBe 0x22
        bytes[3] shouldBe 0x77 // 0x44556677 little endian
        bytes[4] shouldBe 0x66
        bytes[5] shouldBe 0x55
        bytes[6] shouldBe 0x44
        bytes[7] shouldBe 0x88.toByte()
    }

    @Test
    fun `set start offset`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY)
        gen.setStartOffset(0x1000)
        // The start offset is used during output saving
    }

    @Test
    fun `intel hex with custom width`() {
        val gen = BinaryFileGenerator(
            Endian.LITTLE,
            OutputFormat.INTEL_HEX,
            intelHexWidth = 8
        )

        for (i in 0 until 16) {
            gen.addByte(i, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        val output = stream.readByteArray().decodeToString()
        output shouldContain "00000001FF"
    }

    @Test
    fun `empty generator produces empty output`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY)

        val stream = Buffer()
        gen.write(stream)

        stream.readByteArray().size shouldBe 0
    }

    @Test
    fun `label with different locations`() {
        val loc1 = SourceLocation("file1.asm", 10, 5)

        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY)
        gen.addLabel("test", loc1)

        gen.resolve("test") shouldBe 0
    }

    @Test
    fun `validation with missing labels throws exception`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY)

        // Add validation but don't define the labels
        gen.addValidation(dummyLocation, "missing1", "missing2", 10)

        shouldThrow<GeneratorException> {
            gen.validate()
        }.message shouldBe "Internal error: validation label not found"
    }

    @Test
    fun `calculate size with single byte`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addByte(0xFF, dummyLocation)
        }
        gen.calculateSize() shouldBe 1
    }

    @Test
    fun `calculate size with single word`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addWord(0xFFFF, dummyLocation)
        }
        gen.calculateSize() shouldBe 2
    }

    @Test
    fun `calculate size with single dword`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addDword(-1, dummyLocation)
        }
        gen.calculateSize() shouldBe 4
    }

    @Test
    fun `generate at non-zero address`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            org(0x1000)
            addByte(0xAB, dummyLocation)
            addByte(0xCD, dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        val bytes = stream.readByteArray()
        bytes.size shouldBe 0x1000 + 2
        for (i in 0..< 0x1000) {
            bytes[i] shouldBe 0
        }
        bytes[bytes.size-2] shouldBe 0xAB.toByte()
        bytes[bytes.size-1] shouldBe 0xCD.toByte()
    }

    @Test
    fun `multiple labels at different offsets`() {
        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.BINARY).apply {
            addLabel("a", dummyLocation)
            addByte(0x00, dummyLocation)
            addLabel("b", dummyLocation)
            addByte(0x00, dummyLocation)
            addByte(0x00, dummyLocation)
            addLabel("c", dummyLocation)
        }

        gen.resolve("a") shouldBe 0
        gen.resolve("b") shouldBe 1
        gen.resolve("c") shouldBe 3
    }

    @Test
    fun `intel hex format preserves data integrity`() {
        val expectedData = byteArrayOf(
            0x00.toByte(), 0x55.toByte(), 0xAA.toByte(), 0xFF.toByte(),
            0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte())

        val gen = BinaryFileGenerator(Endian.LITTLE, OutputFormat.INTEL_HEX)
        for (byte in expectedData) {
            gen.addByte(byte.toInt(), dummyLocation)
        }

        val stream = Buffer()
        gen.write(stream)

        val output = stream.readByteArray().decodeToString()
        output shouldBe ":080000000055AAFF12345678E6\n" + ":00000001FF\n"
    }
}

// Helper function for string containment
private infix fun String.shouldContain(substring: String) {
    if (!this.contains(substring)) {
        throw AssertionError("Expected \"$this\" to contain \"$substring\"")
    }
}

// Test command for simple byte operations
private class TestByteCmd : AsmCmd {
    override fun size(vararg args: AsmArg): Int = 1
    override fun compile(out: CompilerOutput, vararg args: AsmArg) {
        val arg = args[0]
        if (arg is ArgLabel && arg.resolved != null) {
            out.addByte(arg.resolved!!)
        } else {
            out.addByte(0x00)
        }
    }
    override fun str(vararg args: AsmArg): String = "db ?"
}
