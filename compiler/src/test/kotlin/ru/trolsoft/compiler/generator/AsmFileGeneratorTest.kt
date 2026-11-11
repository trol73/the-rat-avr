package ru.trolsoft.compiler.generator

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.therat.arch.AsmArg
import ru.trolsoft.therat.arch.AsmCmd
import ru.trolsoft.therat.arch.AsmCall
import ru.trolsoft.therat.arch.ArgNumber
import ru.trolsoft.therat.arch.CompilerOutput
import kotlin.test.Test

class AsmFileGeneratorTest {

    private val loc = SourceLocation("test.s", 1, 1)

    private class TestAsmFileGenerator : AsmFileGenerator()

    private fun createTestGenerator(): TestAsmFileGenerator {
        return TestAsmFileGenerator()
    }

    // ============ addLine tests ============

    @Test
    fun `AsmFileGenerator addLine should not throw`() {
        val gen = createTestGenerator()
        gen.addLine("main:")
    }

    @Test
    fun `AsmFileGenerator addMultipleLines should not throw`() {
        val gen = createTestGenerator()
        gen.addLine("line1")
        gen.addLine("line2")
        gen.addLine("line3")
    }

    @Test
    fun `AsmFileGenerator addEmptyLine should not throw`() {
        val gen = createTestGenerator()
        gen.addLine("")
    }

    // ============ addHeader tests ============

    @Test
    fun `AsmFileGenerator addHeader should not throw`() {
        val gen = createTestGenerator()
        gen.addLine("body")
        gen.addHeader(listOf("; Header 1", "; Header 2"))
    }

    @Test
    fun `AsmFileGenerator addHeader with empty list should not throw`() {
        val gen = createTestGenerator()
        gen.addHeader(emptyList())
    }

    @Test
    fun `AsmFileGenerator addHeader with multiple lines should not throw`() {
        val gen = createTestGenerator()
        gen.addHeader(listOf("; Header 1", "; Header 2", "; Header 3"))
    }

    // ============ addLabel tests ============

    @Test
    fun `AsmFileGenerator addLabel should not throw`() {
        val gen = createTestGenerator()
        gen.addLabel("main", loc)
    }

    @Test
    fun `AsmFileGenerator addMultipleLabels should not throw`() {
        val gen = createTestGenerator()
        gen.addLabel("label1", loc)
        gen.addLabel("label2", loc)
        gen.addLabel("label3", loc)
    }

    // ============ org tests ============

    @Test
    fun `AsmFileGenerator org should not throw`() {
        val gen = createTestGenerator()
        gen.org(0x1000)
    }

    @Test
    fun `AsmFileGenerator org with zero should not throw`() {
        val gen = createTestGenerator()
        gen.org(0)
    }

    // ============ addByte/addWord/addDword tests ============

    @Test
    fun `AsmFileGenerator addByte should not throw`() {
        val gen = createTestGenerator()
        gen.addByte(0xFF, loc)
    }

    @Test
    fun `AsmFileGenerator addByte with zero should not throw`() {
        val gen = createTestGenerator()
        gen.addByte(0, loc)
    }

    @Test
    fun `AsmFileGenerator addWord should not throw`() {
        val gen = createTestGenerator()
        gen.addWord(0xFFFF, loc)
    }

    @Test
    fun `AsmFileGenerator addDword should not throw`() {
        val gen = createTestGenerator()
        gen.addDword(0x7FFFFFFF, loc)
    }

    // ============ addCommand tests ============

    @Test
    fun `AsmFileGenerator addCommand should not throw`() {
        val gen = createTestGenerator()
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg) = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg) = "nop"
        }
        gen.addCommand(loc, cmd)
    }

    @Test
    fun `AsmFileGenerator addCommand with comment should not throw`() {
        val gen = createTestGenerator()
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg) = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg) = "nop"
        }
        gen.addCommand(loc, cmd, "No operation")
    }

    @Test
    fun `AsmFileGenerator addCommand with args should not throw`() {
        val gen = createTestGenerator()
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg) = 2
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg) = "ldi"
        }
        val arg = ArgNumber(42)
        gen.addCommand(loc, cmd, arg)
    }

    @Test
    fun `AsmFileGenerator addMultipleCommands should not throw`() {
        val gen = createTestGenerator()
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg) = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg) = "nop"
        }
        gen.addCommand(loc, cmd)
        gen.addCommand(loc, cmd)
    }

    // ============ resolveLabels tests ============

    @Test
    fun `AsmFileGenerator resolveLabels should not throw`() {
        val gen = createTestGenerator()
        gen.addLabel("test", loc)
        gen.resolveLabels()
    }

    // ============ addValidation tests ============

    @Test
    fun `AsmFileGenerator addValidation should not throw`() {
        val gen = createTestGenerator()
        gen.addValidation(loc, "start", "end", 100)
    }

    @Test
    fun `AsmFileGenerator validate should not throw`() {
        val gen = createTestGenerator()
        gen.validate()
    }

    // ============ LabelResolver implementation ============

    @Test
    fun `AsmFileGenerator resolve should return label offset`() {
        val gen = createTestGenerator()
        gen.addLabel("test", loc)
        gen.resolve("test") shouldBe -1
    }

    @Test
    fun `AsmFileGenerator resolve unknown label should return null`() {
        val gen = createTestGenerator()
        gen.resolve("unknown") shouldBe null
    }

    // ============ Line data class tests ============

    @Test
    fun `AsmFileGenerator Line data class should store fields`() {
        val line = AsmFileGenerator.Line("text", "comment", null)
        line.str shouldBe "text"
        line.comment shouldBe "comment"
        line.asm shouldBe null
    }

    @Test
    fun `AsmFileGenerator Line with AsmCall`() {
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg) = 0
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg) = "nop"
        }
        val line = AsmFileGenerator.Line(null, null, AsmCall(cmd, emptyArray(), loc))
        line.str shouldBe null
        line.comment shouldBe null
        line.asm shouldNotBe null
    }
}
