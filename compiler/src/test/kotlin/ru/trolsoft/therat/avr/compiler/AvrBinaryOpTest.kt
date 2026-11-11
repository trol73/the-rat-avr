package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException

/**
 * Тесты для binaryop.kt — бинарные операции и присваивания.
 */
class AvrBinaryOpTest {

    // ==================== СДВИГИ ====================

    @Test fun `shift left single register`() {
        val res = compileM8("r24 <<= 3")
        res shouldHaveSize 3
        res[0] shouldBe "lsl r24"
        res[1] shouldBe "lsl r24"
        res[2] shouldBe "lsl r24"
    }

    @Test fun `shift right single register`() {
        val res = compileM8("r24 >>= 2")
        res shouldHaveSize 2
        res[0] shouldBe "lsr r24"
        res[1] shouldBe "lsr r24"
    }

    @Test fun `shift left invalid count zero`() {
        shouldThrow<CompilerException> {
            compileM8("r24 <<= 0")
        }.message shouldBe "Invalid argument"
    }

    @Test fun `shift left invalid count eight`() {
        shouldThrow<CompilerException> {
            compileM8("r24 <<= 8")
        }.message shouldBe "Invalid argument"
    }

    @Test fun `shift right invalid count zero`() {
        shouldThrow<CompilerException> {
            compileM8("r24 >>= 0")
        }.message shouldBe "Invalid argument"
    }

    // ==================== XOR ====================

    @Test fun `xor assign register to register`() {
        val res = compileM8("r24 ^= r25")
        res shouldHaveSize 1
        res[0] shouldBe "eor r24, r25"
    }

    @Test fun `xor assign register to number fails`() {
        shouldThrow<CompilerException> {
            compileM8("r24 ^= 5")
        }
    }

    // ==================== OR and AND ====================

    @Test fun `or assign register to immediate`() {
        val res = compileM8("r24 |= 0xf0")
        res shouldHaveSize 1
        res[0] shouldBe "ori r24, 0xf0"
    }

    @Test fun `or assign register to register`() {
        val res = compileM8("r24 |= r25")
        res shouldHaveSize 1
        res[0] shouldBe "or r24, r25"
    }

    @Test fun `and assign register to immediate`() {
        val res = compileM8("r24 &= 0x0f")
        res shouldHaveSize 1
        res[0] shouldBe "andi r24, 0xf"
    }

    @Test fun `and assign register to register`() {
        val res = compileM8("r24 &= r25")
        res shouldHaveSize 1
        res[0] shouldBe "and r24, r25"
    }

    @Test fun `or assign low register fails`() {
        shouldThrow<CompilerException> {
            compileM8("r1 |= 0xf0")
        }.message shouldBe "r16..r31 expected but r1 found. Perhaps you can do this using a temporary register."
    }

    @Test fun `and assign low register fails`() {
        shouldThrow<CompilerException> {
            compileM8("r1 &= 0xf0")
        }.message shouldBe "r16..r31 expected but r1 found. Perhaps you can do this using a temporary register."
    }

    // ==================== СДВИГИ РЕГИСТРОВЫХ ПАР ====================

    @Test fun `shift left register pair`() {
        val res = compileM8("r25.r24 <<= 1")
        res shouldHaveSize 2
        res[0] shouldBe "lsl r24"
        res[1] shouldBe "rol r25"
    }

    @Test fun `shift right register pair`() {
        val res = compileM8("r25.r24 >>= 1")
        res shouldHaveSize 2
        res[0] shouldBe "lsr r25"
        res[1] shouldBe "ror r24"
    }

    @Test fun `shift left three-byte group`() {
        val res = compileM8("r26.r25.r24 <<= 2")
        res shouldHaveSize 6
        res[0] shouldBe "lsl r24"
        res[1] shouldBe "rol r25"
        res[2] shouldBe "rol r26"
        res[3] shouldBe "lsl r24"
        res[4] shouldBe "rol r25"
        res[5] shouldBe "rol r26"
    }

    @Test fun `shift right three-byte group`() {
        val res = compileM8("r26.r25.r24 >>= 2")
        res shouldHaveSize 6
        res[0] shouldBe "lsr r26"
        res[1] shouldBe "ror r25"
        res[2] shouldBe "ror r24"
        res[3] shouldBe "lsr r26"
        res[4] shouldBe "ror r25"
        res[5] shouldBe "ror r24"
    }

    @Test fun `shift left register pair invalid count`() {
        shouldThrow<CompilerException> {
            compileM8("r25.r24 <<= 16")
        }.message shouldBe "Invalid argument"
    }

    // ==================== MEM и PRG ====================

    @Test fun `assign from mem X to register`() {
        val res = compileM8("r24 = mem[X]")
        res shouldHaveSize 1
        res[0] shouldBe "ld r24, x"
    }

    @Test fun `assign from mem Z++ to register`() {
        val res = compileM8("r24 = mem[Z++]")
        res shouldHaveSize 1
        res[0] shouldBe "ld r24, z+"
    }

    @Test fun `assign from mem minus Z to register`() {
        val res = compileM8("r24 = mem[--Z]")
        res shouldHaveSize 1
        res[0] shouldBe "ld r24, -z"
    }

    @Test fun `assign from prg Z to register`() {
        val res = compileM8("r24 = prg[Z]")
        res shouldHaveSize 1
        res[0] shouldBe "lpm r24, z"
    }

    @Test fun `assign from prg Z++ to register`() {
        val res = compileM8("r24 = prg[Z++]")
        res shouldHaveSize 1
        res[0] shouldBe "lpm r24, z+"
    }

    @Test fun `assign to mem X from register`() {
        val res = compileM8("mem[X] = r24")
        res shouldHaveSize 1
        res[0] shouldBe "st x, r24"
    }

    @Test fun `assign to mem Z++ from register`() {
        val res = compileM8("mem[Z++] = r24")
        res shouldHaveSize 1
        res[0] shouldBe "st z+, r24"
    }

    @Test fun `assign to mem minus Z from register`() {
        val res = compileM8("mem[--Z] = r24")
        res shouldHaveSize 1
        res[0] shouldBe "st -z, r24"
    }

    @Test fun `assign from mem to r0 with lpm`() {
        val res = compileM8("r0 = prg[Z]")
        res shouldHaveSize 1
        res[0] shouldBe "lpm"
    }

    // ==================== ПЕРЕМЕННЫЕ ====================

    @Test fun `assign register to byte variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var myVar: byte
            proc main() {
                myVar = r24
            }
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "sts 96, r24"
    }

    @Test fun `assign register pair to word variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var myVar: word
            proc main() {
                myVar = r25.r24
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "sts 97, r25"
        res[1] shouldBe "sts 96, r24"
    }

    @Test fun `assign register group to dword variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var myVar: dword
            proc main() {
                myVar = r27.r26.r25.r24
            }
        """.trimIndent())
        res shouldHaveSize 4
        res[0] shouldBe "sts 99, r27"
        res[1] shouldBe "sts 98, r26"
        res[2] shouldBe "sts 97, r25"
        res[3] shouldBe "sts 96, r24"
    }

    @Test fun `assign byte variable to register`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var myVar: byte
            proc main() {
                r24 = myVar
            }
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "lds r24, 96"
    }

    @Test fun `assign word variable to register pair`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var myVar: word
            proc main() {
                r25.r24 = myVar
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "lds r25, 97"
        res[1] shouldBe "lds r24, 96"
    }

    @Test fun `pre-increment variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var myVar: byte
            proc main() {
                myVar = ++r24
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "inc r24"
        res[1] shouldBe "sts 96, r24"
    }

    @Test fun `post-increment variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var myVar: byte
            proc main() {
                myVar = r24++
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "sts 96, r24"
        res[1] shouldBe "inc r24"
    }

    @Test fun `pre-decrement variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var myVar: byte
            proc main() {
                myVar = --r24
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "dec r24"
        res[1] shouldBe "sts 96, r24"
    }

    @Test fun `post-decrement variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var myVar: byte
            proc main() {
                myVar = r24--
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "sts 96, r24"
        res[1] shouldBe "dec r24"
    }

    // ==================== PRE/POST INCREMENT/DECREMENT ====================

    @Test fun `post-increment register assign`() {
        val res = compileM8("r24 = r25++")
        res shouldHaveSize 2
        res[0] shouldBe "mov r24, r25"
        res[1] shouldBe "inc r25"
    }

    @Test fun `pre-decrement register assign`() {
        val res = compileM8("r24 = --r25")
        res shouldHaveSize 2
        res[0] shouldBe "dec r25"
        res[1] shouldBe "mov r24, r25"
    }

    @Test fun `post-decrement register assign`() {
        val res = compileM8("r24 = r25--")
        res shouldHaveSize 2
        res[0] shouldBe "mov r24, r25"
        res[1] shouldBe "dec r25"
    }

    // ==================== I/O PORTS ====================

    @Test fun `assign io port bit to register bit`() {
        val res = compileM8("ZH[7] = PINC->3")
        res shouldHaveSize 3
        res[0] shouldBe "cbr r31, 128"
        res[1] shouldBe "sbic 0x13, 3"
        res[2] shouldBe "sbr r31, 128"
    }

    @Test fun `assign inverted register bit to io port bit`() {
        val res = compileM8("DDRC->2 = !r24[5]")
        res shouldHaveSize 4
        res[0] shouldBe "sbrc r24, 5"
        res[1] shouldBe "cbi 0x14, 2"
        res[2] shouldBe "sbrs r24, 5"
        res[3] shouldBe "sbi 0x14, 2"
    }

    @Test fun `assign SREG T flag from register bit`() {
        val res = compileM8("SREG->T = r24[3]")
        res shouldHaveSize 1
        res[0] shouldBe "bst r24, 3"
    }

    @Test fun `assign register bit from SREG T flag`() {
        val res = compileM8("r24[5] = SREG->T")
        res shouldHaveSize 1
        res[0] shouldBe "bld r24, 5"
    }

    @Test fun `move register bit through T flag`() {
        val res = compileM8("r21[2] = SREG->T = r20[1]")
        res shouldHaveSize 2
        res[0] shouldBe "bst r20, 1"
        res[1] shouldBe "bld r21, 2"
    }

    @Test fun `assign bit 1 to register bit`() {
        val res = compileM8("r24[3] = 1")
        res shouldHaveSize 1
        res[0] shouldBe "sbr r24, 8"
    }

    @Test fun `assign bit 0 to register bit`() {
        val res = compileM8("r24[3] = 0")
        res shouldHaveSize 1
        res[0] shouldBe "cbr r24, 8"
    }

    // ==================== SREG BITS ====================

    @Test fun `set SREG C flag`() {
        val res = compileM8("SREG->C = 1")
        res shouldHaveSize 1
        res[0] shouldBe "bset 0"
    }

    @Test fun `clear SREG Z flag`() {
        val res = compileM8("SREG->Z = 0")
        res shouldHaveSize 1
        res[0] shouldBe "bclr 1"
    }

    // ==================== REVERSE AND COMPLEMENT ====================

    @Test fun `negate register`() {
        val res = compileM8("r24 = -r25")
        res shouldHaveSize 2
        res[0] shouldBe "mov r24, r25"
        res[1] shouldBe "neg r24"
    }

    @Test fun `complement register`() {
        val res = compileM8("r24 = ~r25")
        res shouldHaveSize 2
        res[0] shouldBe "mov r24, r25"
        res[1] shouldBe "com r24"
    }

    @Test fun `negate self register`() {
        val res = compileM8("r24 = -r24")
        res shouldHaveSize 1
        res[0] shouldBe "neg r24"
    }

    @Test fun `complement self register`() {
        val res = compileM8("r24 = ~r24")
        res shouldHaveSize 1
        res[0] shouldBe "com r24"
    }

    // ==================== РЕГИСТРОВЫЕ ПАРЫ ====================

    @Test fun `assign pair to pair with movw`() {
        val res = compileM8("r25.r24 = r17.r16")
        res shouldHaveSize 1
        res[0] shouldBe "movw r25:r24, r17:r16"
    }

    @Test fun `assign pair to pair with mov`() {
        val res = compileM8("r25.r24 = r21.r24")
        res shouldHaveSize 1
        res[0] shouldBe "mov r25, r21"
    }

    @Test fun `assign pair to number`() {
        val res = compileM8("r25.r24 = 0x1234")
        res shouldHaveSize 2
        res[0] shouldBe "ldi r25, 0x12"
        res[1] shouldBe "ldi r24, 0x34"
    }

    @Test fun `assign zero to pair`() {
        val res = compileM8("r25.r24 = 0")
        res shouldHaveSize 2
        // Оба регистра >= 16 → ldi для обоих
        res[0] shouldBe "ldi r25, 0"
        res[1] shouldBe "ldi r24, 0"
    }

    // ==================== MEM ARRAY ====================

    @Test fun `assign to mem Y with pre-increment`() {
        val res = compileM8("mem[Y] = ++r24")
        res shouldHaveSize 2
        res[0] shouldBe "inc r24"
        res[1] shouldBe "st y, r24"
    }

    @Test fun `assign to mem Y with post-increment`() {
        val res = compileM8("mem[Y] = r24++")
        res shouldHaveSize 2
        res[0] shouldBe "st y, r24"
        res[1] shouldBe "inc r24"
    }

    @Test fun `assign to mem Y with pre-decrement`() {
        val res = compileM8("mem[Y] = --r24")
        res shouldHaveSize 2
        res[0] shouldBe "dec r24"
        res[1] shouldBe "st y, r24"
    }

    @Test fun `assign to mem Y with post-decrement`() {
        val res = compileM8("mem[Y] = r24--")
        res shouldHaveSize 2
        res[0] shouldBe "st y, r24"
        res[1] shouldBe "dec r24"
    }

    @Test fun `assign to mem X++ from register`() {
        val res = compileM8("mem[X++] = r24")
        res shouldHaveSize 1
        res[0] shouldBe "st x+, r24"
    }

    // ==================== ARROW LEFT (BIT COPY) ====================

    @Test fun `copy register bit through T flag`() {
        val res = compileM8("r21[2] <- r17[1]")
        res shouldHaveSize 2
        res[0] shouldBe "bst r17, 1"
        res[1] shouldBe "bld r21, 2"
    }

    @Test fun `copy bit chain`() {
        val res = compileM8("r10[7] <- r24[3] <- r17[1]")
        res shouldHaveSize 3
        res[0] shouldBe "bst r17, 1"
        res[1] shouldBe "bld r24, 3"
        res[2] shouldBe "bld r10, 7"
    }

    @Test fun `copy bit 1 to register bit`() {
        val res = compileM8("r0[2] <- 1")
        res shouldHaveSize 2
        res[0] shouldBe "bset 6"
        res[1] shouldBe "bld r0, 2"
    }

    @Test fun `copy bit 0 to register bit`() {
        val res = compileM8("r0[2] <- 0")
        res shouldHaveSize 2
        res[0] shouldBe "bclr 6"
        res[1] shouldBe "bld r0, 2"
    }

    @Test fun `copy invalid bit value fails`() {
        shouldThrow<CompilerException> {
            compileM8("r0[2] <- 2")
        }.message shouldBe "1 or 0 expected, but 2 found"
    }

    @Test fun `copy register bit to Z bit`() {
        val res = compileM8("Z[10] <- r24[5]")
        res shouldHaveSize 2
        res[0] shouldBe "bst r24, 5"
        res[1] shouldBe "bld r31, 2"
    }

    // ==================== ERROR CASES ====================

    @Test fun `assign register bit to register bit fails`() {
        shouldThrow<CompilerException> {
            compileM8("r24[7] = r22[1]")
        }.message shouldBe "Can't assign register bits directly. But you can do this using the T flag:\n\t r24[7] <- r22[1]"
    }

    @Test fun `assign io bit to high port bit fails`() {
        shouldThrow<CompilerException> {
            compileM8("ZH[7] = TIFR->TOV0")
        }.message shouldBe "Can't read from high port (TIFR = 0x38) directly. You can do this using a temporary register."
    }

    // ==================== ADD/SUB WITH VARIABLES ====================
    // += и -= с переменной генерируют ошибку, т.к. add/sub не поддерживает память в AVR

    @Test fun `reg += byte variable fails`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                var vx: byte
                proc main() {
                    r24 += vx
                }
            """.trimIndent())
        }.message shouldBe "Unsupported operation"
    }

    @Test fun `reg -= byte variable fails`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                var vx: byte
                proc main() {
                    r24 -= vx
                }
            """.trimIndent())
        }.message shouldBe "Unsupported operation"
    }

    // ==================== COMPLEX EXPRESSIONS ====================

    @Test fun `assign prg with register operation`() {
        val res = compileM8("r26 = prg[Z++] & r29")
        res shouldHaveSize 2
        res[0] shouldBe "lpm r26, z+"
        res[1] shouldBe "and r26, r29"
    }

    @Test fun `assign prg complement with register operation`() {
        val res = compileM8("r26 = ~prg[Z++] & r29")
        res shouldHaveSize 3
        res[0] shouldBe "lpm r26, z+"
        res[1] shouldBe "com r26"
        res[2] shouldBe "and r26, r29"
    }

    @Test fun `assign mem complement with register operation`() {
        val res = compileM8("r26 = ~mem[Z++] & r29")
        res shouldHaveSize 3
        res[0] shouldBe "ld r26, z+"
        res[1] shouldBe "com r26"
        res[2] shouldBe "and r26, r29"
    }

    // ==================== PORT PAIRS ====================

    @Test fun `assign word port to pair`() {
        val res = compileM8("r25.r24 = ADC")
        res shouldHaveSize 2
        res[0] shouldBe "in r24, 4"
        res[1] shouldBe "in r25, 5"
    }

    @Test fun `assign pair to word port`() {
        val res = compileM8("ADC = r25.r24")
        res shouldHaveSize 2
        res[0] shouldBe "out 0x5, r25"
        res[1] shouldBe "out 0x4, r24"
    }

    @Test fun `assign port pair to Z`() {
        val res = compileM8("Z = ADC")
        res shouldHaveSize 2
        res[0] shouldBe "in r30, 4"
        res[1] shouldBe "in r31, 5"
    }
}
