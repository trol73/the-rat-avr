package ru.trolsoft.therat.avr.compiler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.trolsoft.compiler.compiler.CompilerException
import kotlin.test.assertEquals

class AvrLoopCompilerTest {
    @Test
    fun testLoopReg() {
        val r = compileM8("""
            loop(r24) {
                nop
            }
        """.trimIndent())

        assertEquals(5, r.size)
        assertEquals("main@loop@3:", r[0])
        assertEquals("nop", r[1])
        assertEquals("dec r24", r[2])
        assertEquals("brne main@loop@3", r[3])
        assertEquals("main@loopend@3:", r[4])
    }

    @Test
    fun testLoopRegVal() {
        val r = compileM8("""
            loop(r24 = 123) {
                nop
            }
        """.trimIndent())
        assertEquals(6, r.size)
        assertEquals("ldi r24, 123", r[0])
        assertEquals("main@loop@3:", r[1])
        assertEquals("nop", r[2])
        assertEquals("dec r24", r[3])
        assertEquals("brne main@loop@3", r[4])
        assertEquals("main@loopend@3:", r[5])
    }

    @Test
    fun testLoopReg0x100() {
        val r = compileM8("""
            loop(r24 = 0x100) {
                nop
            }
        """.trimIndent())
        assertEquals(6, r.size)
        assertEquals("clr r24", r[0])
        assertEquals("main@loop@3:", r[1])
        assertEquals("nop", r[2])
        assertEquals("dec r24", r[3])
        assertEquals("brne main@loop@3", r[4])
        assertEquals("main@loopend@3:", r[5])
    }

    @Test
    fun testLoopRegTooBig() {
        val exception = assertThrows<CompilerException> {
            compileM8("""
                loop(r24 = 0x101) {
                    nop
                }
            """.trimIndent())
        }
        assertEquals("Value too big: 0x101", exception.message)
    }

    @Test
    fun testLoopPair() {
        val r = compileM8("""
            loop(r25.r24) {
                nop
            }
        """.trimIndent())
        assertEquals(5, r.size)
        assertEquals("main@loop@3:", r[0])
        assertEquals("nop", r[1])
        assertEquals("sbiw r24, 1", r[2])
        assertEquals("brne main@loop@3", r[3])
        assertEquals("main@loopend@3:", r[4])
    }

    @Test
    fun testLoopPairVal() {
        val r = compileM8("""
            loop(r25.r24 = 10) {
                nop
            }
        """.trimIndent())
        assertEquals(7, r.size)
        assertEquals("ldi r25, 0", r[0])
        assertEquals("ldi r24, 10", r[1])
        assertEquals("main@loop@3:", r[2])
        assertEquals("nop", r[3])
        assertEquals("sbiw r24, 1", r[4])
        assertEquals("brne main@loop@3", r[5])
        assertEquals("main@loopend@3:", r[6])
    }

    @Test
    fun testLoopPair10000() {
        val r = compileM8("""
            loop(r25.r24 = 0x10000) {
                nop
            }
        """.trimIndent())
        assertEquals(7, r.size)
        assertEquals("ldi r25, 0", r[0])
        assertEquals("ldi r24, 0", r[1])
        assertEquals("main@loop@3:", r[2])
        assertEquals("nop", r[3])
        assertEquals("sbiw r24, 1", r[4])
        assertEquals("brne main@loop@3", r[5])
        assertEquals("main@loopend@3:", r[6])
    }

    @Test
    fun testLoopNotPair1() {
        val exception = assertThrows<CompilerException> {
            compileM8("""
                loop(r24.r25 = 100) {
                    nop
                }
            """.trimIndent())
        }
        assertEquals("Register or pair expected: r24.r25", exception.message)
    }

    @Test
    fun testLoopNotPair2() {
        val exception = assertThrows<CompilerException> {
            compileM8("""
                loop(r1.r0 = 100) {
                    nop
                }
            """.trimIndent())
        }
        assertEquals("Register or pair expected: r1.r0", exception.message)
    }

    @Test
    fun testLoopPairTooBig() {
        val exception = assertThrows<CompilerException> {
            compileM8("""
                loop(r25.r24 = 0x10001) {
                    nop
                }
            """.trimIndent())
        }
        assertEquals("Value too big: 0x10001", exception.message)
    }


    @Test
    fun testLoopIfBreak() {
        val r = compileM8("""
            loop {
                nop
                if (XL != 0) break
            }
        """.trimIndent())

        assertEquals(6, r.size)
        assertEquals("main@loop@3:", r[0])
        assertEquals("nop", r[1])
        assertEquals("tst r26", r[2])
        assertEquals("brne main@loopend@3", r[3])
        assertEquals("rjmp main@loop@3", r[4])
        assertEquals("main@loopend@3:", r[5])
    }

    @Test
    fun testLoopArgReg() {
        val r = compileCode("""
            #define CPU "mega8"
            proc myProc(arg: r20) {
                loop(arg) {
                    nop
                }
            }
        """.trimIndent())
        assertEquals(6, r.size)
        assertEquals("myproc:", r[0])
        assertEquals("myproc@loop@3:", r[1])
        assertEquals("nop", r[2])
        assertEquals("dec r20", r[3])
        assertEquals("brne myproc@loop@3", r[4])
        assertEquals("myproc@loopend@3:", r[5])
    }

    @Test
    fun testLoopArgRegPair() {
        val r = compileCode("""
            #define CPU "mega8"
            proc myProc(arg: r25.r24) {
                loop(arg) {
                    nop
                }
            }
        """.trimIndent())
        assertEquals(6, r.size)
        assertEquals("myproc:", r[0])
        assertEquals("myproc@loop@3:", r[1])
        assertEquals("nop", r[2])
        assertEquals("sbiw r24, 1", r[3])
        assertEquals("brne myproc@loop@3", r[4])
        assertEquals("myproc@loopend@3:", r[5])
    }


    @Test
    fun testLoopIo() {
        val r = compileM8("""
            loop(SPSR->SPIF) {}
        """.trimIndent())
        assertEquals(4, r.size)
        assertEquals("main@loop@3:", r[0])
        assertEquals("sbic 14, 7", r[1])
        assertEquals("rjmp main@loop@3", r[2])
        assertEquals("main@loopend@3:", r[3])
    }

    @Test
    fun testLoopNotIo() {
        val r = compileM8("""
            loop(!SPSR->SPIF) {}
        """.trimIndent())
        assertEquals(4, r.size)
        assertEquals("main@loop@3:", r[0])
        assertEquals("sbis 14, 7", r[1])
        assertEquals("rjmp main@loop@3", r[2])
        assertEquals("main@loopend@3:", r[3])
    }

}
