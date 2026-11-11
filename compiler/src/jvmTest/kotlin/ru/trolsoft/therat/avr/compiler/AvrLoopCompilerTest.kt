package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException

class AvrLoopCompilerTest {
    @Test
    fun testLoopReg() {
        val r = compileM8("""
            loop(r24) {
                nop
            }
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "main@loop@3:"
        r[1] shouldBe "nop"
        r[2] shouldBe "dec r24"
        r[3] shouldBe "brne main@loop@3"
        r[4] shouldBe "main@loopend@3:"
    }

    @Test
    fun testLoopRegVal() {
        val r = compileM8("""
            loop(r24 = 123) {
                nop
            }
        """.trimIndent())
        r shouldHaveSize 6
        r[0] shouldBe "ldi r24, 123"
        r[1] shouldBe "main@loop@3:"
        r[2] shouldBe "nop"
        r[3] shouldBe "dec r24"
        r[4] shouldBe "brne main@loop@3"
        r[5] shouldBe "main@loopend@3:"
    }

    @Test
    fun testLoopReg0x100() {
        val r = compileM8("""
            loop(r24 = 0x100) {
                nop
            }
        """.trimIndent())
        r shouldHaveSize 6
        r[0] shouldBe "clr r24"
        r[1] shouldBe "main@loop@3:"
        r[2] shouldBe "nop"
        r[3] shouldBe "dec r24"
        r[4] shouldBe "brne main@loop@3"
        r[5] shouldBe "main@loopend@3:"
    }

    @Test
    fun testLoopRegTooBig() {
        shouldThrow<CompilerException> {
            compileM8("""
                loop(r24 = 0x101) {
                    nop
                }
            """.trimIndent())
        }.message shouldBe "Value too big: 0x101"
    }

    @Test
    fun testLoopPair() {
        val r = compileM8("""
            loop(r25.r24) {
                nop
            }
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "main@loop@3:"
        r[1] shouldBe "nop"
        r[2] shouldBe "sbiw r25:r24, 1"
        r[3] shouldBe "brne main@loop@3"
        r[4] shouldBe "main@loopend@3:"
    }

    @Test
    fun testLoopPairVal() {
        val r = compileM8("""
            loop(r25.r24 = 10) {
                nop
            }
        """.trimIndent())
        r shouldHaveSize 7
        r[0] shouldBe "ldi r25, 0"
        r[1] shouldBe "ldi r24, 10"
        r[2] shouldBe "main@loop@3:"
        r[3] shouldBe "nop"
        r[4] shouldBe "sbiw r25:r24, 1"
        r[5] shouldBe "brne main@loop@3"
        r[6] shouldBe "main@loopend@3:"
    }

    @Test
    fun `loop pair = 0x10000`() {
        val r = compileM8("""
            loop(r25.r24 = 0x10000) {
                nop
            }
        """.trimIndent())
        r shouldHaveSize 7
        r[0] shouldBe "ldi r25, 0"
        r[1] shouldBe "ldi r24, 0"
        r[2] shouldBe "main@loop@3:"
        r[3] shouldBe "nop"
        r[4] shouldBe "sbiw r25:r24, 1"
        r[5] shouldBe "brne main@loop@3"
        r[6] shouldBe "main@loopend@3:"
    }

    @Test
    fun `loop inverted pair`() {
        val r = compileM8("""
            loop(r24.r25 = 100) {
                nop
            }
        """.trimIndent())
        r shouldHaveSize 8
        r[0] shouldBe "ldi r24, 0"
        r[1] shouldBe "ldi r25, 100"
        r[2] shouldBe "main@loop@3:"
        r[3] shouldBe "nop"
        r[4] shouldBe "subi r25, 1"
        r[5] shouldBe "sbci r24, 0"
        r[6] shouldBe "brne main@loop@3"
        r[7] shouldBe "main@loopend@3:"
    }

    @Test
    fun `loop unsupported pair`() {
        shouldThrow<CompilerException> {
            compileM8("""
                loop(r1.r0 = 100) {
                    nop
                }
            """.trimIndent())
        }.message shouldBe "r16..r31 expected but r0 found. Perhaps you can do this using a temporary register."
    }

    @Test
    fun testLoopPairTooBig() {
        shouldThrow<CompilerException> {
            compileM8("""
                loop(r25.r24 = 0x10001) {
                    nop
                }
            """.trimIndent())
        }.message shouldBe "Value too big: 0x10001"
    }


    @Test
    fun `loop with if() break`() {
        val r = compileM8("""
            loop {
                nop
                if (XL != 0) break
            }
        """.trimIndent())

        r shouldHaveSize 5
        r[0] shouldBe "main@loop@3:"
        r[1] shouldBe "nop"
        r[2] shouldBe "tst r26"
        r[3] shouldBe "breq main@loop@3"
        r[4] shouldBe "main@loopend@3:"
    }

    @Test
    fun `loop with arg register`() {
        val r = compileCode("""
            #define CPU "mega8"
            proc myProc(arg: r20) {
                loop(arg) {
                    nop
                }
            }
        """.trimIndent())
        r shouldHaveSize 6

        r[0] shouldBe "myproc:"
        r[1] shouldBe "myproc@loop@3:"
        r[2] shouldBe "nop"
        r[3] shouldBe "dec r20"
        r[4] shouldBe "brne myproc@loop@3"
        r[5] shouldBe "myproc@loopend@3:"
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
        r shouldHaveSize 6
        r[0] shouldBe "myproc:"
        r[1] shouldBe "myproc@loop@3:"
        r[2] shouldBe "nop"
        r[3] shouldBe "sbiw r25:r24, 1"
        r[4] shouldBe "brne myproc@loop@3"
        r[5] shouldBe "myproc@loopend@3:"
    }


    @Test
    fun testLoopIo() {
        val r = compileM8("""
            loop(SPSR->SPIF) {}
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldBe "main@loop@3:"
        r[1] shouldBe "sbic 0xe, 7"
        r[2] shouldBe "rjmp main@loop@3"
        r[3] shouldBe "main@loopend@3:"
    }

    @Test
    fun testLoopNotIo() {
        val r = compileM8("""
            loop(!SPSR->SPIF) {}
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldBe "main@loop@3:"
        r[1] shouldBe "sbis 0xe, 7"
        r[2] shouldBe "rjmp main@loop@3"
        r[3] shouldBe "main@loopend@3:"
    }


    @Test fun `empty loop with break`() {
        val r = compileM8("""
        loop (r16) {
            break
        }
        """.trimIndent())
        r shouldHaveSize 5
        val startLabel = r[0].removeSuffix(":")
        val endLabel = r[4].removeSuffix(":")
        r[0] shouldEndWith ":"
        r[1] shouldBe "rjmp $endLabel"
        r[2] shouldBe "dec r16"
        r[3] shouldBe "brne $startLabel"
        r[4] shouldEndWith ":"
    }

    @Test fun `loop with if-break`() {
        val r = compileM8("""
        loop (r16) {
            if (r20 == 10) break
        }
        """.trimIndent())
        r shouldHaveSize 6
        val startLabel = r[0].removeSuffix(":")
        val endLabel = r[5].removeSuffix(":")
        r[0] shouldEndWith ":"
        r[1] shouldBe "cpi r20, 10"
        r[2] shouldBe "breq $endLabel"
        r[3] shouldBe "dec r16"
        r[4] shouldBe "brne $startLabel"
        r[5] shouldEndWith ":"
    }

    @Test fun `loop that ends with if (reg1 == reg2) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (r24 == r1) break
        }
        """.trimIndent())

        r shouldHaveSize 5
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "cpse r24, r1"
        r[3] shouldBe "rjmp $startLabel"
        r[4] shouldEndWith ":"
    }

    @Test fun `loop that ends with if (reg1 != reg2) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (r24 != r1) break
        }
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "cp r24, r1"
        r[3] shouldBe "breq $startLabel"
        r[4] shouldEndWith  ":"
    }

    @Test fun `loop that ends with if (SREG flag) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (SREG->Z) break
        }
        """.trimIndent())

        r shouldHaveSize 4
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "brbc 1, $startLabel"
        r[3] shouldEndWith  ":"
    }

    @Test fun `loop that ends with if (!SREG flag) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (!SREG->Z) break
        }
        """.trimIndent())

        r shouldHaveSize 4
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "brbs 1, $startLabel"
        r[3] shouldEndWith  ":"
    }

    @Test fun `loop that ends with if (pair != pair) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (X != Y) break
        }
        """.trimIndent())

        r shouldHaveSize 6
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "cp r26, r28"
        r[3] shouldBe "cpc r27, r29"
        r[4] shouldBe "breq $startLabel"
        r[5] shouldEndWith  ":"
    }

    @Test fun `loop that ends with if (pair == pair) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (X == Y) break
        }
        """.trimIndent())

        r shouldHaveSize 6
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "cp r26, r28"
        r[3] shouldBe "cpc r27, r29"
        r[4] shouldBe "brne $startLabel"
        r[5] shouldEndWith  ":"
    }

    @Test fun `loop that ends with if (reg bit set) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (r24[1]) break
        }
        """.trimIndent())

        r shouldHaveSize 5
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "sbrs r24, 1"
        r[3] shouldBe "rjmp $startLabel"
        r[4] shouldEndWith  ":"
    }

    @Test fun `loop that ends with if (!reg bit set) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (!r24[1]) break
        }
        """.trimIndent())

        r shouldHaveSize 5
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "sbrc r24, 1"
        r[3] shouldBe "rjmp $startLabel"
        r[4] shouldEndWith  ":"
    }

    @Test fun `loop that ends with if (io bit) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (DDRC->2) break
        }
        """.trimIndent())

        r shouldHaveSize 5
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "sbis 0x14, 2"
        r[3] shouldBe "rjmp $startLabel"
        r[4] shouldEndWith  ":"
    }

    @Test fun `loop that ends with if (!io bit) break`() {
        val r = compileM8("""
        loop {
            r24 = mem[X]
            if (!DDRC->2) break
        }
        """.trimIndent())

        r shouldHaveSize 5
        r[0] shouldEndWith ":"
        val startLabel = r[0].removeSuffix(":")
        r[1] shouldBe "ld r24, x"
        r[2] shouldBe "sbic 0x14, 2"
        r[3] shouldBe "rjmp $startLabel"
        r[4] shouldEndWith  ":"
    }

    @Test fun `loop that ends with if (!pin) break`() {
        val r = compileCode("""
            #define CPU "mega8"
            pin p = C0
            proc myProc() {
                loop {
                    r24 = mem[X]
                    if (!p->pin) break
                }
            }
        """.trimIndent())
        r shouldHaveSize 6
        val startLabel = r[1].removeSuffix(":")
        r[1] shouldEndWith ":"
        r[2] shouldBe "ld r24, x"
        r[3] shouldBe "sbic 0x13, 0"
        r[4] shouldBe "rjmp $startLabel"
        r[5] shouldEndWith  ":"
    }

    @Test fun `loops with if break`() {
        val r = compileM8("""
            loop {
                if (r25 == 0b10000000) break
            }
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldEndWith ":"
        r[1] shouldBe "cpi r25, 0b10000000"
        r[2] shouldBe "brne main@loop@3"
        r[3] shouldEndWith ":"
    }

    @Test fun `loops with if (Z == const) break`() {
        val r = compileM8("""
            loop {
                if (Z == 0x1234) break
                ;if (ZH == 0x12 && ZL == 0x34) break
                nop
            }
        """.trimIndent())

        r shouldHaveSize 9

        val lblLoopStart = r[0].removeSuffix(":")
        val lblIfEnd = r[5].removeSuffix(":")
        val lblLoopEnd = r[8].removeSuffix(":")

        r[0] shouldBe "$lblLoopStart:"
        r[1] shouldBe "cpi r30, 52"
        r[2] shouldBe "brne $lblIfEnd"
        r[3] shouldBe "cpi r31, 18"
        r[4] shouldBe "breq $lblLoopEnd"
        r[5] shouldBe "$lblIfEnd:"
        r[6] shouldBe "nop"
        r[7] shouldBe "rjmp main@loop@3"
        r[8] shouldBe "$lblLoopEnd:"
    }

    @Test fun `loop with if-not-bit-break`() {
        val res = compileM8("""
            loop {
                if (!r24[3]) break
            }
        """.trimIndent())

        res shouldHaveSize 4
        val startLabel = res[0].removeSuffix(":")
        res[0] shouldEndWith ":"
        res[1] shouldBe "sbrc r24, 3"
        res[2] shouldBe "rjmp $startLabel"
    }

    @Test fun `loop with if-not-bit-break with brackets`() {
        val res = compileM8("""
            loop {
                if (!r24[3]) {
                    break
                }
            }
        """.trimIndent())

        res shouldHaveSize 4
        val startLabel = res[0].removeSuffix(":")
        res[0] shouldEndWith ":"
        res[1] shouldBe "sbrc r24, 3"
        res[2] shouldBe "rjmp $startLabel"
    }

    @Test fun `loop with if (r27_r26 == 0) break` () {
        val res = compileM8("""
            loop {
                if (r27.r26 == 0) break	
            }
        """.trimIndent())

        val startLabel = res[0].removeSuffix(":")
        res[0] shouldEndWith ":"
        res[1] shouldBe "cpi r26, 0"
        res[2] shouldBe "brne $startLabel"
        res[3] shouldBe "cpi r27, 0"
        res[4] shouldBe "brne $startLabel"
    }

    @Test fun `loops with if reg_pair break`() {
        val r = compileM8("""
            loop {
                if (Z == 0) break
            }
        """.trimIndent())

        r shouldHaveSize 6
        val startLabel = r[0].removeSuffix(":")
        r[0] shouldEndWith ":"
        r[1] shouldBe "cpi r30, 0"
        r[2] shouldBe "brne $startLabel"
        r[3] shouldBe "cpi r31, 0"
        r[4] shouldBe "brne $startLabel"
        r[5] shouldEndWith ":"
    }

    @Test fun `loops with if reg_pair break st middle`() {
        val r = compileM8("""
            loop {
                if (Z == 0) break
                nop
            }
        """.trimIndent())

        r shouldHaveSize 9
        val loopStartLabel = r[0].removeSuffix(":")
        val ifEndLabel = r[5].removeSuffix(":")
        val loopEndLabel = r[8].removeSuffix(":")
        r[0] shouldBe "$loopStartLabel:"
        r[1] shouldBe "cpi r30, 0"
        r[2] shouldBe "brne $ifEndLabel"
        r[3] shouldBe "cpi r31, 0"
        r[4] shouldBe "breq $loopEndLabel"
        r[5] shouldBe "$ifEndLabel:"
        r[6] shouldBe "nop"
        r[7] shouldBe "rjmp $loopStartLabel"
        r[8] shouldBe "$loopEndLabel:"
    }

    @Test fun `loops with return at end`() {
        val r = compileM8("""
            loop {
                if (++r24 == 0b10000000) ret
            }
        """.trimIndent())

        r shouldHaveSize 6
        val lblStart = r[0].removeSuffix(":")
        r[0] shouldBe "$lblStart:"
        r[1] shouldBe "inc r24"
        r[2] shouldBe "cpi r24, 0b10000000"
        r[3] shouldBe "brne $lblStart"
        r[4] shouldBe "ret"
        r[5] shouldEndWith ":"
    }

    @Test fun `loops with return block at end`() {
        val r = compileM8("""
            loop {
                if (++r24 == 0b10000000) {
                    ret
                }
            }
        """.trimIndent())

        r shouldHaveSize 6
        val lblStart = r[0].removeSuffix(":")
        r[0] shouldBe "$lblStart:"
        r[1] shouldBe "inc r24"
        r[2] shouldBe "cpi r24, 0b10000000"
        r[3] shouldBe "brne $lblStart"
        r[4] shouldBe "ret"
        r[5] shouldEndWith ":"
    }

    @Test fun `loops with return at middle`() {
        val r = compileM8("""
            loop {
                if (++r24 == 0b10000000) ret
                nop
            }
        """.trimIndent())

        r shouldHaveSize 9
        val lblLoopStart = r[0].removeSuffix(":")
        val lblIfEnd = r[5].removeSuffix(":")
        val lblLoopEnd = r[8].removeSuffix(":")
        r[0] shouldBe "$lblLoopStart:"
        r[1] shouldBe "inc r24"
        r[2] shouldBe "cpi r24, 0b10000000"
        r[3] shouldBe "brne $lblIfEnd"
        r[4] shouldBe "ret"
        r[5] shouldBe  "$lblIfEnd:"
        r[6] shouldBe "nop"
        r[7] shouldBe "rjmp $lblLoopStart"
        r[8] shouldBe  "$lblLoopEnd:"
    }

    @Test fun `loops with ijmp block at end`() {
        val r = compileM8("""
            loop {
                if (++r24 == 0b10000000) {
                    ijmp
                }
            }
        """.trimIndent())

        r shouldHaveSize 6
        val lblStart = r[0].removeSuffix(":")
        r[0] shouldBe "$lblStart:"
        r[1] shouldBe "inc r24"
        r[2] shouldBe "cpi r24, 0b10000000"
        r[3] shouldBe "brne $lblStart"
        r[4] shouldBe "ijmp"
        r[5] shouldEndWith ":"
    }

    @Test fun `loops with ijmp at middle`() {
        val r = compileM8("""
            loop {
                if (++r24 == 0b10000000) ijmp
                nop
            }
        """.trimIndent())

        r shouldHaveSize 9
        val lblLoopStart = r[0].removeSuffix(":")
        val lblIfEnd = r[5].removeSuffix(":")
        val lblLoopEnd = r[8].removeSuffix(":")
        r[0] shouldBe "$lblLoopStart:"
        r[1] shouldBe "inc r24"
        r[2] shouldBe "cpi r24, 0b10000000"
        r[3] shouldBe "brne $lblIfEnd"
        r[4] shouldBe "ijmp"
        r[5] shouldBe  "$lblIfEnd:"
        r[6] shouldBe "nop"
        r[7] shouldBe "rjmp $lblLoopStart"
        r[8] shouldBe  "$lblLoopEnd:"
    }

//    @Test fun `trivial loop with continue`() {
//        val r = compileM8("""
//            loop {
//                continue
//              @lbl:
//                nop
//            }
//        """.trimIndent())
//        println(r)
//        r shouldHaveSize 4
//    }

}
