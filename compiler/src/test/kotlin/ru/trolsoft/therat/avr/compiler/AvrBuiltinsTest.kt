package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException

class AvrBuiltinsTest {

    @Test fun `high register pair`() {
        val res = compileM8("""
            use r25.r24 as w
            r1 = high(w)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "mov r1, r25"
    }

    @Test fun `high of constant`() {
        val res = compileM8("""
            r20 = high(0x1234)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r20, 0x12"
    }

    @Test fun `high of constant with hex`() {
        val res = compileM8("""
            r20 = high(0xFF00)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r20, 0xff"
    }

    @Test fun `high of small constant`() {
        val res = compileM8("""
            r20 = high(0x56)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "clr r20"
    }

    @Test fun `high of aliased pair`() {
        val res = compileM8("""
            use r27.r26 as addr
            r0 = high(addr)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "mov r0, r27"
    }

    // ==================== LOW ====================

    @Test fun `low register pair`() {
        val res = compileM8("""
            use r25.r24 as w
            r0 = low(w)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "mov r0, r24"
    }

    @Test fun `low of constant`() {
        val res = compileM8("""
            r20 = low(0x1234)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r20, 0x34"
    }

    @Test fun `low of constant with hex`() {
        val res = compileM8("""
            r20 = low(0xFF00)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "clr r20"
    }

    @Test fun `low of constant 0xABCD`() {
        val res = compileM8("""
            r20 = low(0xABCD)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r20, 0xcd"
    }

    @Test fun `low of aliased pair`() {
        val res = compileM8("""
            use r27.r26 as addr
            r0 = low(addr)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "mov r0, r26"
    }

    // ==================== HIGH and LOW TOGETHER ====================

    @Test fun `high and low of register pair together`() {
        val res = compileM8("""
            use r25.r24 as w
            r1 = high(w)
            r0 = low(w)
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "mov r1, r25"
        res[1] shouldBe "mov r0, r24"
    }

    @Test fun `high and low of constant together`() {
        val res = compileM8("""
            r20 = high(0x1234)
            r21 = low(0x1234)
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "ldi r20, 0x12"
        res[1] shouldBe "ldi r21, 0x34"
    }

    // ==================== HIGH and LOW ASSIGNMENT ====================

    @Test fun `assign high of register pair`() {
        val res = compileM8("""
            use r25.r24 as w
            high(w) = r1
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "mov r25, r1"
    }

    @Test fun `assign low of register pair`() {
        val res = compileM8("""
            use r25.r24 as w
            low(w) = r0
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "mov r24, r0"
    }

    @Test fun `assign high and low of register pair`() {
        val res = compileM8("""
            use r25.r24 as w
            high(w) = r1
            low(w) = r0
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "mov r25, r1"
        res[1] shouldBe "mov r24, r0"
    }

    // ==================== SIZEOF ====================

    @Test fun `sizeof byte variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r24 = sizeof(vx)
            }
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r24, 1"
    }

    @Test fun `sizeof word variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var wx: word
            proc main() {
                r24 = sizeof(wx)
            }
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r24, 2"
    }

    @Test fun `sizeof dword variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var dx: dword
            proc main() {
                r24 = sizeof(dx)
            }
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r24, 4"
    }

    @Test fun `sizeof qword variable`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var qx: qword
            proc main() {
                r24 = sizeof(qx)
            }
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r24, 8"
    }

    // ==================== BITMASK ====================

    @Test fun `bitmask single bit number`() {
        val res = compileM8("""
            r24 = bitmask(3)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r24, 0b1000"
    }

    @Test fun `bitmask multiple bit numbers`() {
        val res = compileM8("""
            r24 = bitmask(0, 1, 2)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r24, 0b111"
    }

    @Test fun `bitmask two bits`() {
        val res = compileM8("""
            r24 = bitmask(2, 5)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r24, 0b100100"
    }

    @Test fun `bitmask all eight bits`() {
        val res = compileM8("""
            r24 = bitmask(0, 1, 2, 3, 4, 5, 6, 7)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r24, 0b11111111"
    }

    @Test fun `bitmask for pair`() {
        val res = compileM8("Z = bitmask(1, 6, 10, 15)")
        res shouldHaveSize 2
        res[0] shouldBe "ldi r31, 0b10000100"
        res[1] shouldBe "ldi r30, 0b1000010"
    }

    @Test fun `bitmask with pin`() {
        val res = compileCode("""
            #define CPU "atmega8"
            pin p = D3
            proc main() {
                r24 = bitmask(p)
            }
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r24, 0b1000"
    }


    @Test fun `high with no arguments fails`() {
        shouldThrow<CompilerException> {
            compileM8("r1 = high()")
        }.message shouldBe "One argument expected but 0 found"
    }

    @Test fun `high with two arguments fails`() {
        shouldThrow<CompilerException> {
            compileM8("r1 = high(r24, r25)")
        }.message shouldBe "One argument expected but 2 found"
    }

    @Test fun `low with no arguments fails`() {
        shouldThrow<CompilerException> {
            compileM8("r1 = low()")
        }.message shouldBe "One argument expected but 0 found"
    }

    @Test fun `low with two arguments fails`() {
        shouldThrow<CompilerException> {
            compileM8("r1 = low(r24, r25)")
        }.message shouldBe "One argument expected but 2 found"
    }

    @Test fun `sizeof with no arguments fails`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                proc main() {
                    r24 = sizeof()
                }
            """.trimIndent())
        }.message shouldBe "One argument expected but 0 found"
    }

    @Test fun `sizeof with two arguments fails`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                var vx: byte
                proc main() {
                    r24 = sizeof(vx, vx)
                }
            """.trimIndent())
        }.message shouldBe "One argument expected but 2 found"
    }

    @Test fun `sizeof with invalid argument fails`() {
        shouldThrow<CompilerException> {
            compileM8("r24 = sizeof(123)")
        }
    }

    @Test fun `bitmask with no arguments returns zero`() {
        val res = compileM8("r24 = bitmask()")
        res shouldHaveSize 1
        res[0] shouldBe "clr r24"
    }
}
