package ru.trolsoft.therat.avr.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AvrAsmCompilerTest {

    @Test
    fun `LDS from byte var`() {
        val r = compileCode("""
            #define CPU "atmega8"
            var sUartRxBp: byte
            proc main() {
                lds zl, sUartRxBp
            }
        """.trimIndent())
        r shouldHaveSize 1
        r[0] shouldBe "lds r30, 96"
    }

    @Test
    fun `LDS from word var with offset`() {
        val r = compileCode("""
            #define CPU "atmega8"
            var sUartRxBp: word
            proc main() {
                lds zl, sUartRxBp+1
            }
        """.trimIndent())
        r shouldHaveSize 1
        r[0] shouldBe "lds r30, 97"
    }

}