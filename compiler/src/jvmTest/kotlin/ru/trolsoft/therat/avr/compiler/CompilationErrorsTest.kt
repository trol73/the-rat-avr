package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import ru.trolsoft.compiler.compiler.CompilerException
import kotlin.test.Test

class CompilationErrorsTest {
    @Test fun `if (!port_bit = 0) nop`() {
        shouldThrow<CompilerException> {
            compileM8("if (!DDRC->2 = 0) nop")
        }.message shouldBe "Unsupported operation"
    }

    private fun compileM8(expr: String): List<String> = compile(expr, "#define CPU \"mega8\"")
}