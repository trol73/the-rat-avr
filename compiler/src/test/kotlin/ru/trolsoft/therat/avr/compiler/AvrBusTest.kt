package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.avr.AvrRatCompiler

class AvrBusTest {
    private val testConsole = ConsoleReporter({ }, { })

    @Test fun `define simple bus`() {
        val c = compilerM8("bus b1 = [D3, D2, D1, D0]")
        c.buses.size shouldBe 1
        val bus = c.buses["b1"]!!
        bus.size shouldBe 4
        bus[0].port shouldBe 'D'
        bus[0].pin shouldBe 3
        bus[1].port shouldBe 'D'
        bus[1].pin shouldBe 2
        bus[2].port shouldBe 'D'
        bus[2].pin shouldBe 1
        bus[3].port shouldBe 'D'
        bus[3].pin shouldBe 0
    }

    @Test fun `define simple range bus`() {
        val c = compilerM8("bus b1 = [D0..D3]")
        c.buses.size shouldBe 1
        val bus = c.buses["b1"]!!
        bus.size shouldBe 4
        bus[0].port shouldBe 'D'
        bus[0].pin shouldBe 0
        bus[1].port shouldBe 'D'
        bus[1].pin shouldBe 1
        bus[2].port shouldBe 'D'
        bus[2].pin shouldBe 2
        bus[3].port shouldBe 'D'
        bus[3].pin shouldBe 3
    }

    @Test fun `define simple range rev bus`() {
        val c = compilerM8("bus b1 = [D3..D0]")
        c.buses.size shouldBe 1
        val bus = c.buses["b1"]!!
        bus.size shouldBe 4
        bus[0].port shouldBe 'D'
        bus[0].pin shouldBe 3
        bus[1].port shouldBe 'D'
        bus[1].pin shouldBe 2
        bus[2].port shouldBe 'D'
        bus[2].pin shouldBe 1
        bus[3].port shouldBe 'D'
        bus[3].pin shouldBe 0
    }

    @Test fun `define mixed bus`() {
        val c = compilerM8("bus b1 = [D0..D3, C7]")
        c.buses.size shouldBe 1
        val bus = c.buses["b1"]!!
        bus.size shouldBe 5
        bus[0].port shouldBe 'D'
        bus[0].pin shouldBe 0
        bus[1].port shouldBe 'D'
        bus[1].pin shouldBe 1
        bus[2].port shouldBe 'D'
        bus[2].pin shouldBe 2
        bus[3].port shouldBe 'D'
        bus[3].pin shouldBe 3
        bus[4].port shouldBe 'C'
        bus[4].pin shouldBe 7
    }

    @Test fun `define wrong bus size`() {
        val exception = shouldThrow<CompilerException> {
            compilerM8("bus b1[3] = [B1, B2]")
        }
        exception.message shouldBe "Invalid bus size: b1, expected 3 but found 2"
    }

    @Test fun `define wrong bus range`() {
        val exception = shouldThrow<CompilerException> {
            compilerM8("bus b1 = [B3..D0]")
        }
        exception.message shouldBe "Wrong pins range"
    }

    @Test fun `define bus with pin`() {
        val c = compilerM8("""
            pin pin1 = B2
            bus b1 = [D0, pin1]
        """.trimIndent())
        c.buses.size shouldBe 1
        val bus = c.buses["b1"]!!
        bus.size shouldBe 2
        bus[0].port shouldBe 'D'
        bus[0].pin shouldBe 0
        bus[1].port shouldBe 'B'
        bus[1].pin shouldBe 2
    }

    @Test fun `define bus with wrong port`() {
        val exception = shouldThrow<CompilerException> {
            compilerM8("bus b1[3] = [A1..A2]")
        }
        exception.message shouldBe "Device ATmega8 doesn't have port A"
    }


    private fun compilerM8(code: String): AvrRatCompiler {
        val loader = TestFileLoaderStub("#define CPU \"mega8\"\n$code")
        return AvrRatCompiler("", "", testConsole, loader).apply {
            deviceLoader = TestMega8LoaderStub()
            addAsmGenerator()
            addGccAsmGenerator()
            compile("file.art")
        }
    }
}