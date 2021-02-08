package ru.trolsoft.therat.avr.compiler

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AvrAsmCompilerTest {

    @Test
    fun testLds() {
        val r = compileCode("""
            #define CPU "atmega8"
            var sUartRxBp: byte
            proc main() {
                lds zl, sUartRxBp
            }
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("lds r30, 96", r[0])
    }

    @Test
    fun testLdsPlusConst() {
        val r = compileCode("""
            #define CPU "atmega8"
            var sUartRxBp: word
            proc main() {
                lds zl, sUartRxBp+1
            }
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("lds r30, 97", r[0])
    }



}