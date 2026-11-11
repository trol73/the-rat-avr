package ru.trolsoft.compiler.generator

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.therat.arch.*
import ru.trolsoft.compiler.parser.Node

class ListingGeneratorTest {

    private val dummyLocation = SourceLocation("test.asm", 1, 1)
    private val listing = mutableListOf<ListingRec>()

    private fun createGenerator(): ListingGenerator {
        listing.clear()
        return ListingGenerator(null) { rec -> listing.add(rec) }
    }

    @Test
    fun `add byte creates listing record`() {
        createGenerator().apply {
            addByte(0x42, dummyLocation)
            done()
        }

        listing shouldHaveSize 1
        listing[0].offset shouldBe 0
        listing[0].asmCode shouldBe ".db 0x42"
    }

    @Test
    fun `add word creates listing record`() {
        createGenerator().apply {
            addWord(0x1234, dummyLocation)
            done()
        }

        listing shouldHaveSize 1
        listing[0].offset shouldBe 0
        listing[0].asmCode shouldBe ".dw 0x1234"
    }

    @Test
    fun `add dword creates listing record`() {
        createGenerator().apply {
            addDword(0x12345678.toInt(), dummyLocation)
            done()
        }

        listing shouldHaveSize 1
        listing[0].offset shouldBe 0
        listing[0].asmCode shouldBe ".dd 0x12345678"
    }

    @Test
    fun `add command creates listing entry`() {
        createGenerator().apply {
            addCommand(dummyLocation, TestAsmCmd(), ArgNumber(10))
            done()
        }

        listing shouldHaveSize 1
        listing[0].offset shouldBe 0
    }

    @Test
    fun `add label creates label record`() {
        createGenerator().apply {
            addLabel("myLabel", dummyLocation)
            done()
        }

        listing shouldHaveSize 1
        listing[0].asmCode shouldBe "myLabel:"
        listing[0].offset shouldBe 0
    }

    @Test
    fun `offset increments after byte`() {
        createGenerator().apply {
            addByte(0x01, dummyLocation)
            addByte(0x02, dummyLocation)
            done()
        }

        listing[0].offset shouldBe 0
        listing[0].asmCode shouldBe ".db 0x01"
        listing[1].offset shouldBe 1
        listing[1].asmCode shouldBe ".db 0x02"
    }

    @Test
    fun `offset increments after word`() {
        createGenerator().apply {
            addWord(0x1234, dummyLocation)
            addWord(0x5678, dummyLocation)
            done()
        }

        listing shouldHaveSize 2
        listing[0].offset shouldBe 0
        listing[0].asmCode shouldBe ".dw 0x1234"
        listing[1].offset shouldBe 2
        listing[1].asmCode shouldBe ".dw 0x5678"
    }

    @Test
    fun `offset increments after dword`() {
        createGenerator().apply {
            addDword(0x12345678.toInt(), dummyLocation)
            addDword(0x9ABCDEF0.toInt(), dummyLocation)
            done()
        }

        listing[0].offset shouldBe 0
        listing[0].asmCode shouldBe ".dd 0x12345678"
        listing[1].offset shouldBe 4
        listing[1].asmCode shouldBe ".dd 0x9ABCDEF0"
    }

    @Test
    fun `org directive changes offset`() {
        createGenerator().apply {
            addByte(0x01, dummyLocation)
            org(100)
            addByte(0x02, dummyLocation)
            done()
        }

        listing[0].offset shouldBe 0
        listing[1].offset shouldBe 100
    }

    @Test
    fun `procedural tracking with endProc`() {
        createGenerator().apply {
            startProc("myProc", dummyLocation)
            addByte(0x01, dummyLocation)
            addByte(0x02, dummyLocation)
            endProc("myProc", dummyLocation)
            done()
        }

        listing shouldHaveSize 2
        listing[0].offset shouldBe 0
        listing[0].asmCode shouldBe ".db 0x01"
        listing[1].offset shouldBe 1
        listing[1].asmCode shouldBe ".db 0x02"
    }

    @Test
    fun `procedural tracking with wrong endProc name`() {
        createGenerator().apply {
            startProc("proc1", dummyLocation)
            addByte(0x01, dummyLocation)
            endProc("proc2", dummyLocation) // Different name
            done()
        }
        // Should not crash, but size tracking is lost
        listing shouldHaveSize 1
        listing[0].offset shouldBe 0
        listing[0].asmCode shouldBe ".db 0x01"
    }

    @Test
    fun `probe labels are skipped`() {
        createGenerator().apply {
            addLabel("?probe_test", dummyLocation)
            done()
        }

        listing shouldHaveSize 0
    }

    @Test
    fun `multiple labels in sequence`() {
        createGenerator().apply {
            addLabel("label1", dummyLocation)
            addLabel("label2", dummyLocation)
            addLabel("label3", dummyLocation)
            done()
        }

        listing shouldHaveSize 3
        listing[0].asmCode shouldBe "label1:"
        listing[1].asmCode shouldBe "label2:"
        listing[2].asmCode shouldBe "label3:"
    }

    @Test
    fun `add byte with deferred number arg`() {
        val deferred = DeferredNumberArg(
            node = object : Node {
                override fun getLocation(): SourceLocation = dummyLocation
            },
            resolver = { _, _ -> 0x42 }
        )

        createGenerator().apply {
            addByte(deferred, dummyLocation)
            done()
        }

        listing shouldHaveSize 1
        listing[0].offset shouldBe 0
    }

    @Test
    fun `add word with deferred number arg`() {
        val deferred = DeferredNumberArg(
            node = object : Node {
                override fun getLocation(): SourceLocation = dummyLocation
            },
            resolver = { _, _ -> 0x1234 }
        )

        createGenerator().apply {
            addWord(deferred, dummyLocation)
            done()
        }

        listing shouldHaveSize 1
        listing[0].offset shouldBe 0
    }

    @Test
    fun `add dword with deferred number arg`() {
        val deferred = DeferredNumberArg(
            node = object : Node {
                override fun getLocation(): SourceLocation = dummyLocation
            },
            resolver = { _, _ -> 0x12345678 }
        )
        createGenerator().apply {
            addDword(deferred, dummyLocation)
            done()
        }

        listing shouldHaveSize 1
        listing[0].offset shouldBe 0
    }

    @Test
    fun `done processes all listing items`() {
        createGenerator().apply {
            addByte(0x01, dummyLocation)
            addLabel("test", dummyLocation)
            addByte(0x02, dummyLocation)
            done()
        }

        listing shouldHaveSize 3
    }

    @Test
    fun `done with command and args`() {
        createGenerator().apply {
            addCommand(dummyLocation, TestAsmCmd(), ArgNumber(42))
            done()
        }

        listing shouldHaveSize 1
    }

    @Test
    fun `validate does nothing`() {
        val gen = createGenerator()
        // validate() should not throw
        gen.validate()
    }

    @Test
    fun `resolveLabels does nothing`() {
        val gen = createGenerator()
        // resolveLabels() should not throw
        gen.resolveLabels()
    }

    @Test
    fun `resolve returns null for any label`() {
        val gen = createGenerator()
        gen.resolve("anyLabel") shouldBe null
    }

    @Test
    fun `validation rule is not stored in ListingGenerator`() {
        createGenerator().apply {
            addValidation(dummyLocation, "start", "end", 100)
            done()
        }

        // ListingGenerator doesn't store validation rules
        listing shouldHaveSize 0
    }

    @Test
    fun `empty generator done does nothing`() {
        createGenerator().apply {
            done()
        }
        listing shouldHaveSize 0
    }

    @Test
    fun `multiple bytes with correct offsets`() {
        createGenerator().apply {
            for (i in 0 until 10) {
                addByte(i, dummyLocation)
            }
            done()
        }


        for (i in 0 until 10) {
            listing[i].offset shouldBe i
        }
    }

    @Test
    fun `mixed commands and labels`() {
        createGenerator().apply {
            addLabel("start", dummyLocation)
            addByte(0x01, dummyLocation)
            addLabel("middle", dummyLocation)
            addByte(0x02, dummyLocation)
            addLabel("end", dummyLocation)

            done()
        }

        listing shouldHaveSize 5
        listing[0].asmCode shouldBe "start:"
        listing[0].offset shouldBe 0
        listing[1].offset shouldBe 0
        listing[2].asmCode shouldBe "middle:"
        listing[2].offset shouldBe 1
        listing[3].offset shouldBe 1
        listing[4].asmCode shouldBe "end:"
        listing[4].offset shouldBe 2
    }

    @Test
    fun `procedure with bytes in between`() {
        createGenerator().apply {
            startProc("dataProc", dummyLocation)
            addByte(0x01, dummyLocation)
            addByte(0x02, dummyLocation)
            addByte(0x03, dummyLocation)
            endProc("dataProc", dummyLocation)

            done()
        }

        listing shouldHaveSize 3
        listing[0].offset shouldBe 0
        listing[0].asmCode shouldBe ".db 0x01"
        listing[1].offset shouldBe 1
        listing[1].asmCode shouldBe ".db 0x02"
        listing[2].offset shouldBe 2
        listing[2].asmCode shouldBe ".db 0x03"
    }

    @Test
    fun `procedure with no bytes`() {
        createGenerator().apply {
            startProc("emptyProc", dummyLocation)
            endProc("emptyProc", dummyLocation)
            done()
        }

        listing shouldHaveSize 0
//        listing[0].asmCode shouldBe "emptyProc: ; 0 bytes"
    }

    @Test
    fun `add command with location tracking`() {
        val loc = SourceLocation("file.asm", 10, 5)
        createGenerator().apply {
            addCommand(loc, TestAsmCmd(), ArgNumber(100))
            done()
        }

        listing shouldHaveSize 1
    }

    @Test
    fun `byte value 0x00`() {
        createGenerator().apply {
            addByte(0x00, dummyLocation)
            done()
        }

        listing[0].asmCode shouldBe ".db 0x00"
    }

    @Test
    fun `byte value 0xFF`() {
        createGenerator().apply {
            addByte(0xFF, dummyLocation)
            done()
        }

        listing[0].asmCode shouldBe ".db 0xFF"
    }

    @Test
    fun `word value 0x0000`() {
        createGenerator().apply {
            addWord(0x0000, dummyLocation)
            done()
        }
        listing shouldHaveSize 1
        listing[0].asmCode shouldBe ".dw 0x0000"
    }

    @Test
    fun `word value 0xFFFF`() {
        createGenerator().apply {
            addWord(0xFFFF, dummyLocation)
            done()
        }

        listing shouldHaveSize 1
        listing[0].asmCode shouldBe ".dw 0xFFFF"
    }

    @Test
    fun `dword value 0x00000000`() {
        createGenerator().apply {
            addDword(0x00000000, dummyLocation)
            done()
        }

        listing[0].asmCode shouldBe ".dd 0x00000000"
    }

    @Test
    fun `dword value 0xFFFFFFFF`() {
        createGenerator().apply {
            addDword(-1, dummyLocation)
            done()
        }

        listing[0].asmCode shouldBe ".dd 0xFFFFFFFF"
    }

    @Test
    fun `multiple procedures`() {
        createGenerator().apply {
            startProc("proc1", dummyLocation)
            addByte(0x01, dummyLocation)
            endProc("proc1", dummyLocation)

            startProc("proc2", dummyLocation)
            addByte(0x02, dummyLocation)
            addByte(0x03, dummyLocation)
            endProc("proc2", dummyLocation)

            done()
        }

        listing shouldHaveSize 3
    }

    @Test
    fun `offset after org and bytes`() {
        createGenerator().apply {
            org(50)
            addByte(0x01, dummyLocation)
            addByte(0x02, dummyLocation)
            done()
        }
        listing shouldHaveSize 2
        listing[0].offset shouldBe 50
        listing[1].offset shouldBe 51
    }
}

// Test command for listing tests
private class TestAsmCmd : AsmCmd {
    override fun size(vararg args: AsmArg): Int = 1
    override fun compile(out: CompilerOutput, vararg args: AsmArg) {
        out.addByte(0x00)
    }
    override fun str(vararg args: AsmArg): String = "\ttest\t;"
}
