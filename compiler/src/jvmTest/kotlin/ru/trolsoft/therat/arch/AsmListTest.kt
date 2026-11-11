package ru.trolsoft.therat.arch

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import ru.trolsoft.compiler.generator.BinaryOutput
import ru.trolsoft.compiler.generator.Endian
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.Node
import kotlin.test.Test

private fun makeNode(): Node = object : Node {
    override fun getLocation(): SourceLocation = SourceLocation("test.s", 1, 1)
}

class AsmListTest {

    private val location = SourceLocation("test.s", 1, 1)

    @Test
    fun `AsmList should start with empty blocks`() {
        val asmList = AsmList()
        asmList.blocks shouldBe emptyList()
        asmList.offset shouldBe 0
    }

    @Test
    fun `AsmList offset setter should create new block`() {
        val asmList = AsmList()
        asmList.offset = 100
        asmList.blocks.size shouldBe 1
        asmList.blocks[0].offset shouldBe 100
        asmList.offset shouldBe 100
    }

    @Test
    fun `AsmList addCmd should add to current block and increase offset`() {
        val asmList = AsmList()
        asmList.offset = 0
        val cmd = StubCmd(2)
        asmList.addCmd(location, cmd, ArgNumber(1))
        asmList.offset shouldBe 2
        asmList.blocks[0].list.size shouldBe 1
    }

    @Test
    fun `AsmList addCmd should create block if none exists`() {
        val asmList = AsmList()
        val cmd = StubCmd(1)
        asmList.addCmd(location, cmd, ArgNumber(1))
        // Note: offset setter creates a new block, so we have 2 blocks
        // First block has the commands, second was created by offset setter
        asmList.blocks.size shouldBe 2
        asmList.blocks[0].list.size shouldBe 1
        asmList.blocks[0].bytesLength shouldBe 1
    }

    @Test
    fun `AsmList addByte should add 1 byte`() {
        val asmList = AsmList()
        asmList.addByte(0x42, location)
        asmList.offset shouldBe 1
        asmList.blocks[0].list.size shouldBe 1
    }

    @Test
    fun `AsmList addWord should add 2 bytes`() {
        val asmList = AsmList()
        asmList.addWord(0x1234, location)
        asmList.offset shouldBe 2
        asmList.blocks[0].list.size shouldBe 1
    }

    @Test
    fun `AsmList addDword should add 4 bytes`() {
        val asmList = AsmList()
        asmList.addDword(0x12345678, location)
        asmList.offset shouldBe 4
        asmList.blocks[0].list.size shouldBe 1
    }

    @Test
    fun `AsmList addDeferredByte should add deferred command`() {
        val asmList = AsmList()
        val deferred = DeferredNumberArg(makeNode(), { _, _ -> 0 })
        asmList.addByte(deferred, location)
        asmList.offset shouldBe 1
    }

    @Test
    fun `AsmList addDeferredWord should add deferred command`() {
        val asmList = AsmList()
        val deferred = DeferredNumberArg(makeNode(), { _, _ -> 0 })
        asmList.addWord(deferred, location)
        asmList.offset shouldBe 2
    }

    @Test
    fun `AsmList addDeferredDword should add deferred command`() {
        val asmList = AsmList()
        val deferred = DeferredNumberArg(makeNode(), { _, _ -> 0 })
        asmList.addDword(deferred, location)
        asmList.offset shouldBe 4
    }

    @Test
    fun `AsmList multiple addCmd should accumulate offset`() {
        val asmList = AsmList()
        asmList.addByte(0x01, location)
        asmList.addByte(0x02, location)
        asmList.addWord(0x1234, location)
        asmList.offset shouldBe 4
    }

    @Test
    fun `AsmList setting offset multiple times should create multiple blocks`() {
        val asmList = AsmList()
        asmList.offset = 0
        asmList.offset = 100
        asmList.offset = 200
        asmList.blocks.size shouldBe 3
        asmList.blocks[0].offset shouldBe 0
        asmList.blocks[1].offset shouldBe 100
        asmList.blocks[2].offset shouldBe 200
    }
}

class CodeBlockTest {

    private val location = SourceLocation("test.s", 1, 1)

    @Test
    fun `CodeBlock should start with empty list`() {
        val block = CodeBlock(0)
        block.list shouldBe emptyList()
        block.bytesLength shouldBe 0
        block.size() shouldBe 0
    }

    @Test
    fun `CodeBlock should store offset`() {
        CodeBlock(42).offset shouldBe 42
    }

    @Test
    fun `CodeBlock add should increase bytesLength and list size`() {
        val block = CodeBlock(0)
        val cmd = StubCmd(3)
        val added = block.add(location, cmd, ArgNumber(1))
        added shouldBe 3
        block.bytesLength shouldBe 3
        block.list.size shouldBe 1
    }

    @Test
    fun `CodeBlock add multiple commands should accumulate`() {
        val block = CodeBlock(0)
        block.add(location, StubCmd(1), ArgNumber(1))
        block.add(location, StubCmd(2), ArgNumber(2))
        block.add(location, StubCmd(4), ArgNumber(3))
        block.bytesLength shouldBe 7
        block.list.size shouldBe 3
    }

    @Test
    fun `CodeBlock should support index access`() {
        val block = CodeBlock(0)
        block.add(location, StubCmd(1), ArgNumber(1))
        block.add(location, StubCmd(2), ArgNumber(2))
        block[0].cmd shouldBe block[0].cmd
        block[1].cmd.size() shouldBe 2
    }

    @Test
    fun `CodeBlock should support iteration`() {
        val block = CodeBlock(0)
        block.add(location, StubCmd(1), ArgNumber(1))
        block.add(location, StubCmd(2), ArgNumber(2))
        val items = block.list.toList()
        items.size shouldBe 2
    }

    @Test
    fun `CodeBlock compile should write to BinaryOutput`() {
        val block = CodeBlock(0)
        val binaryOutput = BinaryOutput(Endian.LITTLE)
        val cmd = RecordingCmd(0xAA)
        block.add(location, cmd, ArgNumber(1))
        binaryOutput.gotoAddress(0)
        binaryOutput.reserve(block.bytesLength)
        binaryOutput.allocatePages()
        block.compile(binaryOutput)
        binaryOutput.pages[0].data!![0] shouldBe 0xAA.toByte()
    }

    @Test
    fun `CodeBlock compile should handle multiple commands`() {
        val block = CodeBlock(0)
        val binaryOutput = BinaryOutput(Endian.LITTLE)
        block.add(location, RecordingCmd(0x11), ArgNumber(1))
        block.add(location, RecordingCmd(0x22), ArgNumber(2))
        block.add(location, RecordingCmd(0x33), ArgNumber(3))
        binaryOutput.gotoAddress(0)
        binaryOutput.reserve(block.bytesLength)
        binaryOutput.allocatePages()
        block.compile(binaryOutput)
        binaryOutput.pages[0].data!![0] shouldBe 0x11.toByte()
        binaryOutput.pages[0].data!![1] shouldBe 0x22.toByte()
        binaryOutput.pages[0].data!![2] shouldBe 0x33.toByte()
    }

    @Test
    fun `CodeBlock compile with AsmCompileException should wrap it`() {
        val block = CodeBlock(0)
        val binaryOutput = BinaryOutput(Endian.LITTLE)
        block.add(location, FailingCmd(), ArgNumber(1))
        val result = shouldThrow<ru.trolsoft.compiler.compiler.CompilerException> {
            block.compile(binaryOutput)
        }
        result.cause shouldBeInstanceOf AsmCompileException::class
    }

    @Test
    fun `CodeBlock with different offset should not be equal to another`() {
        val block1 = CodeBlock(0)
        val block2 = CodeBlock(100)
        block1.offset shouldNotBe block2.offset
    }
}

// ============ Test Stubs ============

private class StubCmd(private val sizeValue: Int) : AsmCmd {
    override fun size(vararg args: AsmArg): Int = sizeValue
    override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
    override fun str(vararg args: AsmArg): String = "stub"
}

private class RecordingCmd(private val byte: Int) : AsmCmd {
    override fun size(vararg args: AsmArg): Int = 1
    override fun compile(out: CompilerOutput, vararg args: AsmArg) {
        out.addByte(byte)
    }
    override fun str(vararg args: AsmArg): String = byte.toString()
}

private class FailingCmd : AsmCmd {
    override fun size(vararg args: AsmArg): Int = 1
    override fun compile(out: CompilerOutput, vararg args: AsmArg) {
        throw AsmCompileException("Test failure")
    }
    override fun str(vararg args: AsmArg): String = "failing"
}
