package ru.trolsoft.compiler.generator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.therat.arch.*
import kotlin.test.Test

class GeneratorTest {

    private val loc = SourceLocation("test.s", 1, 1)

    // ============ Test Generator implementation ============

    private class TestGenerator : Generator() {
        var orgCalled = false
        var orgOffset = 0
        var validationAdded = false
        var validationStartLabel = ""
        var validationEndLabel = ""
        var validationMaxSize = 0
        var labelsResolved = false
        var commandCount = 0
        var labelCount = 0
        var bytesAdded = 0
        var wordsAdded = 0
        var dwordsAdded = 0

        override fun addCommand(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg) {
            commandCount++
        }

        override fun addLabel(name: String, location: SourceLocation) {
            labelCount++
        }

        override fun write(sink: kotlinx.io.RawSink) {}

        override fun org(offset: Int) {
            orgCalled = true
            orgOffset = offset
        }

        override fun addByte(b: Int, location: SourceLocation) {
            bytesAdded++
        }

        override fun addWord(w: Int, location: SourceLocation) {
            wordsAdded++
        }

        override fun addDword(dw: Int, location: SourceLocation) {
            dwordsAdded++
        }

        override fun addByte(b: DeferredNumberArg, location: SourceLocation) {
            bytesAdded++
        }

        override fun addWord(w: DeferredNumberArg, location: SourceLocation) {
            wordsAdded++
        }

        override fun addDword(dw: DeferredNumberArg, location: SourceLocation) {
            dwordsAdded++
        }

        override fun addValidation(location: SourceLocation, startLabel: String, endLabel: String, maxSize: Int) {
            validationAdded = true
            validationStartLabel = startLabel
            validationEndLabel = endLabel
            validationMaxSize = maxSize
        }

        override fun validate() {}
        override fun resolveLabels() { labelsResolved = true }
    }

    private fun createTestGenerator(): TestGenerator {
        return TestGenerator()
    }

    @Test
    fun `Generator org should store offset`() {
        val gen = createTestGenerator()
        gen.org(100)
        gen.orgCalled shouldBe true
        gen.orgOffset shouldBe 100
    }

    @Test
    fun `Generator org with different offsets`() {
        val gen = createTestGenerator()
        gen.org(0)
        gen.orgOffset shouldBe 0

        gen.org(255)
        gen.orgOffset shouldBe 255

        gen.org(0x7FFFFFFF)
        gen.orgOffset shouldBe 0x7FFFFFFF
    }

    @Test
    fun `Generator addValidation should store validation rule`() {
        val gen = createTestGenerator()
        gen.addValidation(loc, "start", "end", 100)

        gen.validationAdded shouldBe true
        gen.validationStartLabel shouldBe "start"
        gen.validationEndLabel shouldBe "end"
        gen.validationMaxSize shouldBe 100
    }

    @Test
    fun `Generator addValidation with different sizes`() {
        val gen = createTestGenerator()
        gen.addValidation(loc, "s1", "e1", 1)
        gen.validationMaxSize shouldBe 1

        gen.addValidation(loc, "s2", "e2", 1000)
        gen.validationMaxSize shouldBe 1000
    }

    @Test
    fun `Generator resolveLabels should set flag`() {
        val gen = createTestGenerator()
        gen.resolveLabels()
        gen.labelsResolved shouldBe true
    }

    @Test
    fun `Generator validate should not throw by default`() {
        val gen = createTestGenerator()
        gen.validate() // no exception
    }

    // ============ Label class tests ============

    @Test
    fun `Label should store name, offset and location`() {
        val label = Label("myLabel", 100, loc)
        label.name shouldBe "myLabel"
        label.offset shouldBe 100
        label.location shouldBe loc
    }

    @Test
    fun `Label with different offsets`() {
        val label1 = Label("label1", 0, loc)
        label1.offset shouldBe 0

        val label2 = Label("label2", 0x7FFFFFFF, loc)
        label2.offset shouldBe 0x7FFFFFFF
    }

    @Test
    fun `Label should support equality`() {
        val label1 = Label("test", 42, loc)
        val label2 = Label("test", 42, loc)
        val label3 = Label("test", 43, loc)

        label1 shouldBe label2
        label1 shouldNotBe label3
    }

    // ============ GeneratorException tests ============

    @Test
    fun `GeneratorException should store message and location`() {
        val ex = GeneratorException("Test error", loc)
        ex.message shouldBe "Test error"
        ex.location shouldBe loc
    }

    @Test
    fun `GeneratorException with null location`() {
        val ex = GeneratorException("Test error", null)
        ex.message shouldBe "Test error"
        ex.location shouldBe null
    }

    @Test
    fun `GeneratorException with cause`() {
        val cause = RuntimeException("Root cause")
        val ex = GeneratorException("Wrapper", loc, cause)
        ex.message shouldBe "Wrapper"
        ex.location shouldBe loc
        ex.cause shouldBe cause
        ex.cause?.message shouldBe "Root cause"
    }

    @Test
    fun `GeneratorException should be throwable`() {
        shouldThrow<GeneratorException> {
            throw GeneratorException("Error", loc)
        }
    }

    // ============ addCommand tests ============

    @Test
    fun `TestGenerator addCommand should increment counter`() {
        val gen = createTestGenerator()
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg) = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg) = "test"
        }
        gen.addCommand(loc, cmd)
        gen.commandCount shouldBe 1
    }

    @Test
    fun `TestGenerator addLabel should increment counter`() {
        val gen = createTestGenerator()
        gen.addLabel("myLabel", loc)
        gen.labelCount shouldBe 1
    }

    @Test
    fun `TestGenerator addByte should increment counter`() {
        val gen = createTestGenerator()
        gen.addByte(0xFF, loc)
        gen.bytesAdded shouldBe 1
    }

    @Test
    fun `TestGenerator addWord should increment counter`() {
        val gen = createTestGenerator()
        gen.addWord(0xFFFF, loc)
        gen.wordsAdded shouldBe 1
    }

    @Test
    fun `TestGenerator addDword should increment counter`() {
        val gen = createTestGenerator()
        gen.addDword(0x7FFFFFFF, loc)
        gen.dwordsAdded shouldBe 1
    }

    @Test
    fun `TestGenerator addWord with deferred`() {
        val gen = createTestGenerator()
        gen.addWord(0x1234, loc)
        gen.wordsAdded shouldBe 1
    }

    @Test
    fun `TestGenerator org should store offset value`() {
        val gen = createTestGenerator()
        gen.org(0x1000)
        gen.orgOffset shouldBe 0x1000
    }

    @Test
    fun `TestGenerator multiple commands`() {
        val gen = createTestGenerator()
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg) = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg) = "cmd1"
        }
        gen.addCommand(loc, cmd)
        gen.addCommand(loc, cmd)
        gen.commandCount shouldBe 2
    }

    @Test
    fun `TestGenerator mixed operations`() {
        val gen = createTestGenerator()
        gen.addLabel("start", loc)
        gen.addCommand(loc, object : AsmCmd {
            override fun size(vararg args: AsmArg) = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg) = "nop"
        })
        gen.addByte(0x00, loc)
        gen.addWord(0x1234, loc)
        gen.addDword(0x7FFFFFFF, loc)

        gen.labelCount shouldBe 1
        gen.commandCount shouldBe 1
        gen.bytesAdded shouldBe 1
        gen.wordsAdded shouldBe 1
        gen.dwordsAdded shouldBe 1
    }

    @Test
    fun `TestGenerator multiple labels`() {
        val gen = createTestGenerator()
        gen.addLabel("label1", loc)
        gen.addLabel("label2", loc)
        gen.addLabel("label3", loc)
        gen.labelCount shouldBe 3
    }

    @Test
    fun `TestGenerator multiple bytes`() {
        val gen = createTestGenerator()
        gen.addByte(0x01, loc)
        gen.addByte(0x02, loc)
        gen.addByte(0x03, loc)
        gen.bytesAdded shouldBe 3
    }

    @Test
    fun `TestGenerator multiple words`() {
        val gen = createTestGenerator()
        gen.addWord(0x1111, loc)
        gen.addWord(0x2222, loc)
        gen.wordsAdded shouldBe 2
    }

    @Test
    fun `TestGenerator multiple dwords`() {
        val gen = createTestGenerator()
        gen.addDword(0x11111111, loc)
        gen.addDword(0x22222222, loc)
        gen.dwordsAdded shouldBe 2
    }
}
