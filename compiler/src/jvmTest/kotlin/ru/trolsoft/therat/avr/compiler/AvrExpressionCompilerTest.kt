package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException


class AvrExpressionCompilerTest {

    @Test fun `r1 = 0`() {
        val res = compile("r1 = 0")
        res shouldHaveSize 1
        res[0] shouldBe "clr r1"
    }

    @Test fun `reg = reg`() {
        val res = compile("r1 = r20")
        res shouldHaveSize 1
        res[0] shouldBe "mov r1, r20"
    }

    @Test fun `reg = reg shl const`() {
        val res = compile("r16 = r18 << 3")
        res shouldHaveSize 4

        res[0] shouldBe "mov r16, r18"
        res[1] shouldBe "lsl r16"
        res[2] shouldBe "lsl r16"
        res[3] shouldBe "lsl r16"
    }

    @Test fun `complex binary op`() {
        val res = compile("r1 = (r2 << 3) + r3 - r4 - 5 + 2*2")
        res shouldHaveSize 7
        res[0] shouldBe "mov r1, r2"
        res[1] shouldBe "lsl r1"
        res[2] shouldBe "lsl r1"
        res[3] shouldBe "lsl r1"
        res[4] shouldBe "add r1, r3"
        res[5] shouldBe "sub r1, r4"
        res[6] shouldBe "dec r1"
    }

    @Test fun `reg = PINC and const`() {
        val res = compileM8("""
            r16 = PINC & 3
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "in r16, 0x13"
        res[1] shouldBe "andi r16, 3"
    }

    @Test fun `reg = reg = port and const`() {
        val res = compileM8("""
            r0 = r16 = PINC & 3
        """.trimIndent())

        res shouldHaveSize 3
        res[0] shouldBe "in r16, 0x13"
        res[1] shouldBe "andi r16, 3"
        res[2] shouldBe "mov r0, r16"
    }

    @Test fun `SREG bit = 1`() {
        val res = compileM8("""
            SREG->C = 1
        """.trimIndent())

        res shouldHaveSize 1
        res[0] shouldBe "bset 0"
    }

    @Test fun `SREG bit = 0`() {
        val res = compileM8("""
            SREG->Z = 0
        """.trimIndent())

        res shouldHaveSize 1
        res[0] shouldBe "bclr 1"
    }

    @Test fun `multi assign regs`() {
        val res = compileM8("""
            r0 = r16 = ' '
        """.trimIndent())

        res shouldHaveSize 2
        res[0] shouldBe "ldi r16, 32"
        res[1] shouldBe "mov r0, r16"
    }

    @Test fun `set reg bit`() {
        val res = compileM8("""
            r23[2] = 1
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "sbr r23, 4"
    }

    @Test fun `clr reg bit`() {
        val res = compileM8("""
            r23[4] = 0
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "cbr r23, 16"
    }

    @Test fun `assign pair to pair with same reg`() {
        val res = compile("r25.r24 = r21.r24")
        res shouldHaveSize 1
        res[0] shouldBe "mov r25, r21"
    }

    @Test fun `assign pair to pair with movw`() {
        val res = compile("r25.r24 = r17.r16")
        res shouldHaveSize 1
        res[0] shouldBe "movw r25:r24, r17:r16"
    }

    @Test fun testAssignRegOneMinusReg() {
        val res = compile("r23 = 1 - r20")

        res shouldHaveSize 2
        res[0] shouldBe "ldi r23, 1"
        res[1] shouldBe "sub r23, r20"
    }

    @Test fun testAddRegToSelf() {
        val res = compile("r27 += r27")

        res shouldHaveSize 1
        res[0] shouldBe "add r27, r27"
    }

    @Test fun testDecAliasesPair() {
        val res = compileCode("""
        use R24 as rDelL
        use R25 as rDelH

        proc main() {
            rDelH.rDelL -= 1
        }
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "sbiw r25:r24, 1"
    }

    @Test fun `reg = -self`() {
        val res = compileM8("""
            r27 = -r27
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "neg r27"
    }

    @Test fun `reg1 = -reg2`() {
        val res = compileM8("""
            r27 = -r5
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "mov r27, r5"
        res[1] shouldBe "neg r27"
    }

    @Test fun `reg = prg & reg`() {
        val res = compileM8("""
            r26 = prg[Z++] & r29
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "lpm r26, z+"
        res[1] shouldBe "and r26, r29"
    }

    @Test fun `reg = ~prg & reg`() {
        val res = compileM8("""
            r26 = ~prg[Z++] & r29
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "lpm r26, z+"
        res[1] shouldBe "com r26"
        res[2] shouldBe "and r26, r29"
    }

    @Test fun `reg = ~mem & reg`() {
        val res = compileM8("""
            r26 = ~mem[Z++] & r29
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "ld r26, z+"
        res[1] shouldBe "com r26"
        res[2] shouldBe "and r26, r29"
    }

    @Test fun `add mem addr to pair`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var UART_RxBuf: byte[128]
            proc main() {
                Z += UART_RxBuf
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "subi r30, 160"
        res[1] shouldBe "sbci r31, 255"
    }

    @Test fun `read byte from array`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var buf: byte[32]
            proc main() {
                r24 = buf[0]
                r25 = buf[2]
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "lds r24, 96"
        res[1] shouldBe "lds r25, 98"
    }

    @Test fun `write byte array cell`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var buf: byte[32]
            proc main() {
                buf[0] = r24
                buf[2] = r25
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "sts 96, r24"
        res[1] shouldBe "sts 98, r25"
    }

    @Test fun `move pair from IO word`() {
        val res = compileM8("""
            r24.r25 = OCR1B
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "in r25, 40"
        res[1] shouldBe "in r24, 41"
    }

    @Test fun `move pair to word port`() {
        val res = compileM8("""
            OCR1B = r24.r25
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "out 0x29, r24"
        res[1] shouldBe "out 0x28, r25"
    }

    @Test fun `write reg to word port error`() {
        shouldThrow<CompilerException> {
            compileM8("OCR1B = r24")
        }.message shouldBe "Size mismatch: r24 (8-bit) cannot be directly assigned to OCR1B (16-bit)"
    }

    @Test fun `write byte to word port error`() {
        shouldThrow<CompilerException> {
            compileM8("r24 = OCR1B")
        }.message shouldBe "Size mismatch: OCR1B (16-bit) cannot be directly assigned to r24 (8-bit)"
    }

    @Test fun `write reg pair to byte port error`() {
        shouldThrow<CompilerException> {
            compileM8("SREG = r25.r24")
        }.message shouldBe "Size mismatch: r25.r24 (16-bit) cannot be directly assigned to SREG (8-bit)"
    }

    @Test fun `write byte port to reg pair error`() {
        shouldThrow<CompilerException> {
            compileM8("r25.r24 = SREG")
        }.message shouldBe "Size mismatch: SREG (8-bit) cannot be directly assigned to r25.r24 (16-bit)"
    }

    @Test fun `r23 += r24 + r26`() {
        val res = compileM8("""
            r23 += r24 + r26
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "add r23, r24"
        res[1] shouldBe "add r23, r26"
    }

    @Test fun testRegPlusEqRegRegReg() {
        val res = compileM8("""
            r23 += r24 + r25 + r26
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "add r23, r24"
        res[1] shouldBe "add r23, r25"
        res[2] shouldBe "add r23, r26"
    }

    @Test fun testRegPlusEqRegMinusRegPlusReg() {
        val res = compileM8("""
            r23 += r24 - r25 + r26
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "add r23, r24"
        res[1] shouldBe "sub r23, r25"
        res[2] shouldBe "add r23, r26"
    }

    @Test fun testRegMinusEqRegRegReg() {
        val res = compileM8("""
            r23 -= r24 + r25 + r26
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "sub r23, r24"
        res[1] shouldBe "sub r23, r25"
        res[2] shouldBe "sub r23, r26"
    }

    @Test fun `reg -= reg - reg + reg`() {
        val res = compileM8("""
            r23 -= r24 - r25 + r26
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "sub r23, r24"
        res[1] shouldBe "add r23, r25"
        res[2] shouldBe "sub r23, r26"
    }


    @Test fun `increment pair`() {
        val res = compileM8("""
            r24.r25++
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "subi r25, 255"
        res[1] shouldBe "sbci r24, 255"
    }

    @Test fun `r1r0 += 2`() {
        val res = compileM8("""
            r1.r0 += 2
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "subi r0, 254"
        res[1] shouldBe "sbci r1, 255"
    }



    @Test fun `pair = word_var - small_const`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var counter: word
            proc main() {
                r25.r24 = counter - 1
            }
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "lds r25, 97"
        res[1] shouldBe "lds r24, 96"
        res[2] shouldBe "sbiw r25:r24, 1"
    }

    @Test fun `pair = word_var - const`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var counter: word
            proc main() {
                r25.r24 = counter - 100
            }
        """.trimIndent())
        res shouldHaveSize 4
        res[0] shouldBe "lds r25, 97"
        res[1] shouldBe "lds r24, 96"
        res[2] shouldBe "subi r24, 100"
        res[3] shouldBe "sbci r25, 0"
    }

    @Test fun `reg = var + reg`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r24 = vx + r23
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "lds r24, 96"
        res[1] shouldBe "add r24, r23"
    }

    @Test fun `reg = var + reg + reg`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r24 = vx + r23 + r25
            }
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "lds r24, 96"
        res[1] shouldBe "add r24, r23"
        res[2] shouldBe "add r24, r25"
    }

    @Test fun `reg = var - reg`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r24 = vx - r23
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "lds r24, 96"
        res[1] shouldBe "sub r24, r23"
    }

    @Test fun `reg = var + reg - reg`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r24 = vx + r23 - r25
            }
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "lds r24, 96"
        res[1] shouldBe "add r24, r23"
        res[2] shouldBe "sub r24, r25"
    }


    @Test fun `var = ++reg`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                vx = ++r25
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "inc r25"
        res[1] shouldBe "sts 96, r25"
    }

    @Test fun `var = reg++`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                vx = r25++
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "sts 96, r25"
        res[1] shouldBe "inc r25"
    }

    @Test fun `pair += const16`() {
        val res = compileM8("""
            r24.r25 += 0x271
        """.trimIndent())
        res shouldHaveSize 2            // 0x10000 - 0x271 = 0xfd8f
        res[0] shouldBe "subi r25, 143" // 0x8f
        res[1] shouldBe "sbci r24, 253" // 0xfd
    }

    @Test fun `Z += 0x40`() {
        val res = compileM8("""
            Z += 0x40
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "subi r30, 192"   // 0x10000-0x40
        res[1] shouldBe "sbci r31, 255"
    }

    @Test fun `Z += small const`() {
        val res = compileM8("""
            Z += 0x20
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "adiw z, 32"
    }

    @Test fun `Z -= big const`() {
        val res = compileM8("""
            Z -= 0x40
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "subi r30, 64"
        res[1] shouldBe "sbci r31, 0"
    }

    @Test fun `Z -= small const`() {
        val res = compileM8("""
            Z -= 0x20
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "sbiw z, 32"
    }

    @Test fun `reg group assign with Z`() {
        val res = compileM8("""
            r0.r1.r2.r3 = ZH.ZL.r4.r5
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "mov r0, r31"
        res[1] shouldBe "mov r1, r30"
        res[2] shouldBe "movw r3:r2, r5:r4"
        // TODO проверить что нет лишних присвоений
    }

    @Test fun `reg ^= reg`() {
        val res = compileM8("""
            r24 ^= r25
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "eor r24, r25"
    }

    @Test fun `assign var to shr reg`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                vx = r24 >>= 1
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "lsr r24"
        res[1] shouldBe "sts 96, r24"
    }

    @Test fun `shift group left`() {
        val res = compileM8("""
            r25.r24.r23 <<= 1
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "lsl r23"
        res[1] shouldBe "rol r24"
        res[2] shouldBe "rol r25"
    }

    @Test fun `shift group right`() {
        val res = compileM8("""
            r25.r24.r23 >>= 1
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "lsr r25"
        res[1] shouldBe "ror r24"
        res[2] shouldBe "ror r23"
    }

    @Test fun `double shift group left`() {
        val res = compileM8("""
            r25.r24.r23 <<= 2
        """.trimIndent())
        res shouldHaveSize 6
        res[0] shouldBe "lsl r23"
        res[1] shouldBe "rol r24"
        res[2] shouldBe "rol r25"
        res[3] shouldBe "lsl r23"
        res[4] shouldBe "rol r24"
        res[5] shouldBe "rol r25"
    }

    @Test fun `double shift group right`() {
        val res = compileM8("""
            r25.r24.r23 >>= 2
        """.trimIndent())
        res shouldHaveSize 6
        res[0] shouldBe "lsr r25"
        res[1] shouldBe "ror r24"
        res[2] shouldBe "ror r23"
        res[3] shouldBe "lsr r25"
        res[4] shouldBe "ror r24"
        res[5] shouldBe "ror r23"
    }

    @Test fun `assign io port to register bit`() {
        val res = compileCode("""
            #define CPU "atmega8"
            pin led = D3
            proc main() {
                led->port = r24[1]
            }
        """.trimIndent())
        res shouldHaveSize 4
        res[0] shouldBe "sbrs r24, 1"
        res[1] shouldBe "cbi 0x12, 3"
        res[2] shouldBe "sbrc r24, 1"
        res[3] shouldBe "sbi 0x12, 3"
    }

    @Test fun `assign io port to not register bit`() {
        val res = compileCode("""
            #define CPU "atmega8"
            pin led = D3
            proc main() {
                led->port = !r24[1]
            }
        """.trimIndent())
        res shouldHaveSize 4
        res[0] shouldBe "sbrc r24, 1"
        res[1] shouldBe "cbi 0x12, 3"
        res[2] shouldBe "sbrs r24, 1"
        res[3] shouldBe "sbi 0x12, 3"
    }

    @Test fun `assign reg to mem with pre-increment`() {
        val res = compileM8("""
            r15 = ++mem[Z]
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "ld r15, z"
        res[1] shouldBe "inc r15"
        res[2] shouldBe "st z, r15"
    }

    @Test fun `assign reg to mem with pre-increment and post-incremented addr`() {
        val res = compileM8("""
            r15 = ++mem[Z++]
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "ld r15, z"
        res[1] shouldBe "inc r15"
        res[2] shouldBe "st z+, r15"
    }

    @Test fun `assign reg to mem with pre-decrement and pre-decremented addr`() {
        val res = compileM8("""
            r15 = --mem[--Z]
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "ld r15, -z"
        res[1] shouldBe "dec r15"
        res[2] shouldBe "st z, r15"
    }

    @Test fun `assign reg to port with pre-decrement`() {
        val res = compileM8("""
            r15 = ++PORTD
        """.trimIndent())
        res shouldHaveSize 3
        res[0] shouldBe "in r15, 0x12"
        res[1] shouldBe "inc r15"
        res[2] shouldBe "out 0x12, r15"
    }

    @Test fun `assign alias pair to 0`() {
        val res = compileM8("""
            use r2.r1 as rvU16
            rvU16 = 0
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "clr r2"
        res[1] shouldBe "clr r1"
    }

    @Test fun `assign T flag from reg bit`() {
        val res = compileM8("""
            SREG->T = r24[3]
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "bst r24, 3"
    }

    @Test fun `assign reg bit from T flag`() {
        val res = compileM8("""
            r24[4] = SREG->T
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "bld r24, 4"
    }

    @Test fun `move register bit through T flag`() {
        val res = compileM8("""
            r21[2] = SREG->T = r20[1] 
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "bst r20, 1"
        res[1] shouldBe "bld r21, 2"
    }

    @Test fun `bytes block with simple plus calculation`() {
        val res = compileM8("""
            byte[] {
                1+2, 1+2+3
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe ".db 3"
        res[1] shouldBe ".db 6"
    }

    @Test fun `bytes block with simple or calculation`() {
        val res = compileM8("""
            byte[] {
                1|2, 4|12
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe ".db 3"
        res[1] shouldBe ".db 12"
    }

    @Test fun `bytes block with valid size`() {
        val res = compileM8("""
            byte[2] {
                1, 2
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe ".db 1"
        res[1] shouldBe ".db 2"
    }

    @Test fun `bytes block with expression size`() {
        val res = compileM8("""
            byte[1+1] {
                1, 2
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe ".db 1"
        res[1] shouldBe ".db 2"
    }

    @Test fun `bytes block with invalid size`() {
        shouldThrow<CompilerException> {
            compileM8("""
                byte[2] {
                    1, 2, 3
                }
            """.trimIndent())
        }.message shouldBe "Wrong array size. Expected 2 but found 3 items"
    }

    @Test fun `assign reg to const expression with bit indexes`() {
        val res = compileM8("""
            r21 = 0b11110000[6] + (0b11110000[7] << 2)
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r21, 5"
    }

    @Test fun `assign reg to const expression with bit indexes with calculation`() {
        val res = compileM8("""
            r21 = 0b11110000[4+1]
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "ldi r21, 1"
    }

    @Test fun `assign reversed value`() {
        val res = compileM8("""
            #define rev(v)  (v[0]<<7)|(v[1]<<6)|(v[2]<<5)|(v[3]<<4)|(v[4]<<3)|(v[5]<<2)|(v[6]<<1)|(v[7]<<0)
            r21 = rev(0b11110000)
            r22 = rev(0b00001111)
            r23 = rev(0b10101010)
            r24 = rev(0b01010101)
        """.trimIndent())
        res shouldHaveSize 4
        res[0] shouldBe "ldi r21, 15"
        res[1] shouldBe "ldi r22, 240"
        res[2] shouldBe "ldi r23, 85"
        res[3] shouldBe "ldi r24, 170"
    }

    @Test fun `assign reg to const expression with cleared bit`() {
        val res = compileM8("""
            r1 = 0b11110000[1]
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "clr r1"
    }


    @Test fun `get bits of int value`() {
        val res = compileM8("""
            byte[] {
                (1+2), 0b11110000[0] + (0b11110000[7] << 1)
            }
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe ".db 3"
        res[1] shouldBe ".db 2"
    }

    @Test fun `assign Z to label bytes`() {
        val res = compileHex("""
                #define CPU "atmega8"
                proc main() {
                    Z = data_table
                }
                data_table:
                byte[] {
                    0x12
                    0x34
                }
        """.trimIndent())
        res.trim() shouldBe ":06000000F0E0E4E0123420\n:00000001FF"
    }

    @Test fun `test assign Z to local label`() {
        val res = compileHex("""
                #define CPU "atmega8"
                proc main() {
                    Z = data_table
                    ijmp
                data_table:
                    nop                    
                }
        """.trimIndent())
        res.trim() shouldBe ":08000000F0E0E6E009940000C5\n:00000001FF"
    }

    @Test fun `assign Z to local label words`() {
        val res = compileHex("""
                #define CPU "atmega8"
                proc main() {
                    Z = data_table/2
                    ijmp
                data_table:
                    nop                    
                }
        """.trimIndent())
        res.trim() shouldBe ":08000000F0E0E3E009940000C8\n:00000001FF"
    }

    @Test fun `access to register group argument bits`() {
        val res = compileCode("""
                #define CPU "atmega8"
                proc setAddress(address: r25.r24) {
                    ZL = PORTD
                    ZL[4] = SREG->T = address[0]
                    ZL[1] = SREG->T = address[10]
                    PORTD = ZL
                }
        """.trimIndent())

        res shouldHaveSize 7
        res[1] shouldBe "in r30, 0x12"
        res[2] shouldBe "bst r24, 0"
        res[3] shouldBe "bld r30, 4"
        res[4] shouldBe "bst r25, 2"
        res[5] shouldBe "bld r30, 1"
        res[6] shouldBe "out 0x12, r30"
    }

    @Test fun `assign T flag to reg group bit`() {
        val res = compileM8("""
            use r25.r24 as v
            SREG->T = v[12]
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "bst r25, 4"
    }

    @Test fun `assign reg group bit to 1`() {
        val res = compileM8("""
            use r25.r24 as v
            v[12] = 1
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "sbr r25, 16"
    }

    @Test fun `inverse register`() {
        val res = compileM8("""
            r24 = ~r24
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "com r24"
    }

    @Test fun `inverse register after assign`() {
        val res = compileM8("""
            r24 = ~r25
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "mov r24, r25"
        res[1] shouldBe "com r24"
    }

    @Test fun `move reg bits with T flag`() {
        val res = compileM8("""
            r24[3] <- r17[1]
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "bst r17, 1"
        res[1] shouldBe "bld r24, 3"
    }

    @Test fun `move reg bits with T flag chain`() {
        val res = compileM8("r10[7] <- r24[3] <- r17[1]")
        res shouldHaveSize 3
        res[0] shouldBe "bst r17, 1"
        res[1] shouldBe "bld r24, 3"
        res[2] shouldBe "bld r10, 7"
    }

    @Test fun `move Z bit with T flag chain`() {
        val res = compileM8("Z[10] <- r24[5]")
        res shouldHaveSize 2
        res[0] shouldBe "bst r24, 5"
        res[1] shouldBe "bld r31, 2"
    }

    @Test fun `move reg group bit with T flag`() {
        val res = compileM8("""
            use r1.r2.r3 as a, r10.r11.r12 as b
            a[1] <- b[20]
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "bst r10, 4"
        res[1] shouldBe "bld r3, 1"
    }

    @Test fun `move 1 to reg bit with T flag`() {
        val res = compileM8("""
            r0[2] <- 1
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "bset 6"
        res[1] shouldBe "bld r0, 2"
    }

    @Test fun `move 0 to reg bit with T flag`() {
        val res = compileM8("""
            r0[2] <- 0
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "bclr 6"
        res[1] shouldBe "bld r0, 2"
    }

    @Test fun `move wrong bit const to reg bit with T flag`() {
        shouldThrow<CompilerException> {
            compileM8("""
                r0[2] <- 2
            """.trimIndent())
        }.message shouldBe "1 or 0 expected, but 2 found"
    }

    @Test fun `move 1 to reg group bit with T flag`() {
        val res = compileM8("""
            use r1.r2.r3 as g
            g[20] <- 1
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "bset 6"
        res[1] shouldBe "bld r1, 4"
    }

    @Test fun `move 0 to reg group bit with T flag`() {
        val res = compileM8("""
            use r1.r2.r3 as g
            g[20] <- 0
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "bclr 6"
        res[1] shouldBe "bld r1, 4"
    }

    @Test fun `assign reg bit to low port bit`() {
        val res = compileM8("ZH[7] = PINC->1")
        res shouldHaveSize 3
        res[0] shouldBe "cbr r31, 128"
        res[1] shouldBe "sbic 0x13, 1"
        res[2] shouldBe "sbr r31, 128"
    }

    @Test fun `assign Z bit to low port bit`() {
        val res = compileM8("Z[13] = PINC->1")
        res shouldHaveSize 3
        res[0] shouldBe "cbr r31, 32"
        res[1] shouldBe "sbic 0x13, 1"
        res[2] shouldBe "sbr r31, 32"
    }

    @Test fun `assign reg bit to high port bit returns error`() {
        shouldThrow<CompilerException> {
            compileM8("ZH[7] = TIFR->TOV0")
        }.message shouldBe "Can't read from high port (TIFR = 0x38) directly. You can do this using a temporary register."
    }

    @Test fun `assign reg bit to reg bit returns error`() {
        shouldThrow<CompilerException> {
            compileM8("r24[7] = r22[1]")
        }.message shouldBe "Can't assign register bits directly. But you can do this using the T flag:\n\t r24[7] <- r22[1]"
    }

    @Test fun `assign reg to io with and and or`() {
        val res = compileM8("ZL = (DDRD & 0b00001111) | r24")
        res shouldHaveSize 3
        res[0] shouldBe "in r30, 0x11"
        res[1] shouldBe "andi r30, 0b1111"
        res[2] shouldBe "or r30, r24"
    }

    @Test fun `r20 = (r19++) or r24`() {
        val res = compileM8("r20 = (r19++) | r24")
        res shouldHaveSize 3
        res[0] shouldBe "mov r20, r19"
        res[1] shouldBe "inc r19"
        res[2] shouldBe "or r20, r24"
    }

    @Test fun `r20 = (--r19) or r24`() {
        val res = compileM8("r20 = (--r19) | r24")
        res shouldHaveSize 3
        res[0] shouldBe "dec r19"
        res[1] shouldBe "mov r20, r19"
        res[2] shouldBe "or r20, r24"
    }

    @Test fun `r21 or eq 10`() {
        val res = compileM8("r21 |= 10")
        res shouldHaveSize 1
        res[0] shouldBe "ori r21, 10"
    }

    @Test fun `r21 &= 10`() {
        val res = compileM8("r21 &= 10")
        res shouldHaveSize 1
        res[0] shouldBe "andi r21, 10"
    }

    @Test fun `assign reg bit to reg bit returns error 2`() {
        shouldThrow<CompilerException> {
            compileM8("r24 = DDRD++")
        }.message shouldBe "Wrong expression"
    }

    @Test fun `r1 and eq 10`() {
        shouldThrow<CompilerException> {
            compileM8("r1 &= 10")
        }.message shouldBe "r16..r31 expected but r1 found. Perhaps you can do this using a temporary register."
    }

    @Test fun `r0 or eq 10`() {
        shouldThrow<CompilerException> {
            compileM8("r0 |= 10")
        }.message shouldBe "r16..r31 expected but r0 found. Perhaps you can do this using a temporary register."
    }

    @Test fun `get high and low for reg_pair`() {
        val res = compileM8("""
            use r25.r24 as w
            r1 = high(w)
            r0 = low(w)
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "mov r1, r25"
        res[1] shouldBe "mov r0, r24"
    }

    @Test fun `assign high and low for reg_pair`() {
        val res = compileM8("""
            use r25.r24 as w
            high(w) = r1 
            low(w) = r0 
        """.trimIndent())
        res shouldHaveSize 2
        res[0] shouldBe "mov r25, r1"
        res[1] shouldBe "mov r24, r0"
    }

    @Test fun `assign word port to Z`() {
        val res = compileM8("Z = ADC")
        res shouldHaveSize 2
        res[0] shouldBe "in r30, 4"
        res[1] shouldBe "in r31, 5"
    }

    @Test fun `r25r24 = ADC`() {
        val res = compileM8("r25.r24 = ADC")
        res shouldHaveSize 2
        res[0] shouldBe "in r24, 4"
        res[1] shouldBe "in r25, 5"
    }


    @Test fun `increment word var`() {
        val res = compileCode("""
                #define CPU "atmega8"
                var word_var: word
                proc main() {
                    use r19.r18 as rw
                    rw = word_var
                    rw++
                    word_var = rw 
                }
        """.trimIndent())
        res shouldHaveSize 6
        res[0] shouldBe "lds r19, 97"
        res[1] shouldBe "lds r18, 96"
        res[2] shouldBe "subi r18, 255"
        res[3] shouldBe "sbci r19, 255"
        res[4] shouldBe "sts 97, r19"
        res[5] shouldBe "sts 96, r18"
    }

    @Test fun `increment dword var`() {
        val res = compileCode("""
                #define CPU "atmega8"
                var dword_var: dword
                proc main() {
                    use r23.r22.r21.r20 as rdw
                    rdw = dword_var
                    rdw++
                    dword_var = rdw 
                }
        """.trimIndent())
        res shouldHaveSize 12

        res[0] shouldBe "lds r23, 99"
        res[1] shouldBe "lds r22, 98"
        res[2] shouldBe "lds r21, 97"
        res[3] shouldBe "lds r20, 96"

        res[4] shouldBe "subi r20, 255"
        res[5] shouldBe "sbci r21, 255"
        res[6] shouldBe "sbci r22, 255"
        res[7] shouldBe "sbci r23, 255"

        res[8] shouldBe "sts 99, r23"
        res[9] shouldBe "sts 98, r22"
        res[10] shouldBe "sts 97, r21"
        res[11] shouldBe "sts 96, r20"
    }

    @Test fun `decrement dword var`() {
        val res = compileCode("""
                #define CPU "atmega8"
                var dword_var: dword
                proc main() {
                    use r23.r22.r21.r20 as rdw
                    rdw = dword_var
                    rdw--
                    dword_var = rdw 
                }
        """.trimIndent())
        res shouldHaveSize 12

        res[0] shouldBe "lds r23, 99"
        res[1] shouldBe "lds r22, 98"
        res[2] shouldBe "lds r21, 97"
        res[3] shouldBe "lds r20, 96"

        res[4] shouldBe "subi r20, 1"
        res[5] shouldBe "sbci r21, 0"
        res[6] shouldBe "sbci r22, 0"
        res[7] shouldBe "sbci r23, 0"

        res[8] shouldBe "sts 99, r23"
        res[9] shouldBe "sts 98, r22"
        res[10] shouldBe "sts 97, r21"
        res[11] shouldBe "sts 96, r20"
    }

    @Test fun `mem_Y = --reg`() {
        val res = compileM8("mem[Y] = --r24")
        res shouldHaveSize 2
        res[0] shouldBe "dec r24"
        res[1] shouldBe "st y, r24"
    }

    @Test fun `mem_Y = ++reg`() {
        val res = compileM8("mem[Y] = ++r24")
        res shouldHaveSize 2
        res[0] shouldBe "inc r24"
        res[1] shouldBe "st y, r24"
    }

    @Test fun `mem_Y = reg--`() {
        val res = compileM8("mem[Y] = r24--")
        res shouldHaveSize 2
        res[0] shouldBe "st y, r24"
        res[1] shouldBe "dec r24"
    }

    @Test fun `mem_Y = reg++`() {
        val res = compileM8("mem[Y] = r24++")
        res shouldHaveSize 2
        res[0] shouldBe "st y, r24"
        res[1] shouldBe "inc r24"
    }

    @Test fun `invert port bit with single-line if`() {
        val res = compileM8("if (PORTC->3) { PORTC->3 = 0 } else { PORTC->3 = 1 }")

        res shouldHaveSize 7
        val startElse = res[4].removeSuffix(":")
        val end = res[6].removeSuffix(":")
        res[0] shouldBe "sbis 0x15, 3"
        res[1] shouldBe "rjmp $startElse"
        res[2] shouldBe "cbi 0x15, 3"
        res[3] shouldBe "rjmp $end"
        res[4] shouldBe "$startElse:"
        res[5] shouldBe "sbi 0x15, 3"
        res[6] shouldBe "$end:"
    }

    @Test fun `invert port bit with single-line if without brackets`() {
        val res = compileM8("if (PORTC->3) PORTC->3 = 0 else PORTC->3 = 1")

        res shouldHaveSize 7
        val startElse = res[4].removeSuffix(":")
        val end = res[6].removeSuffix(":")
        res[0] shouldBe "sbis 0x15, 3"
        res[1] shouldBe "rjmp $startElse"
        res[2] shouldBe "cbi 0x15, 3"
        res[3] shouldBe "rjmp $end"
        res[4] shouldBe "$startElse:"
        res[5] shouldBe "sbi 0x15, 3"
        res[6] shouldBe "$end:"
    }

    @Test fun `if else if without {}`() {
        val res = compileM8("""
            if (ZL == 0xfc) r24 = 0 
            else if (ZL == 0xfd) r24 = 1
        """.trimIndent())

        res shouldHaveSize 10
        val elseLabel = res[4].removeSuffix(":")
        val endLabel1 = res[8].removeSuffix(":")
        val endLabel2 = res[9].removeSuffix(":")
        res[0] shouldBe "cpi r30, 0xfc"
        res[1] shouldBe "brne $elseLabel"
        res[2] shouldBe "clr r24"
        res[3] shouldBe "rjmp $endLabel2"
        res[4] shouldBe "$elseLabel:"
        res[5] shouldBe "cpi r30, 0xfd"
        res[6] shouldBe "brne $endLabel1"
        res[7] shouldBe "ldi r24, 1"
        res[8] shouldBe "$endLabel1:"
        res[9] shouldBe "$endLabel2:"
    }

    @Test fun `reg = mem++Y`() {
        val res = compileM8("r24 = ++mem[Y]")

        res shouldHaveSize 3
        res[0] shouldBe "ld r24, y"
        res[1] shouldBe "inc r24"
        res[2] shouldBe "st y, r24"
    }

    @Test fun `reg = mem--Y`() {
        val res = compileM8("r24 = --mem[Y]")

        res shouldHaveSize 3
        res[0] shouldBe "ld r24, y"
        res[1] shouldBe "dec r24"
        res[2] shouldBe "st y, r24"
    }

    @Test fun `reg = ~prg Z`() {
        val res = compileM8("r25 = ~prg[Z]")
        res shouldHaveSize 2
        res[0] shouldBe "lpm r25, z"
        res[1] shouldBe "com r25"
    }

    @Test fun `reg = ~mem Z`() {
        val res = compileM8("r25 = ~mem[Z]")
        res shouldHaveSize 2
        res[0] shouldBe "ld r25, z"
        res[1] shouldBe "com r25"
    }
    
    @Test fun `reg = ~byte_var`() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r25 = ~vx
            }
        """.trimIndent())

        res shouldHaveSize 2
        res[0] shouldBe "lds r25, 96"
        res[1] shouldBe "com r25"
    }

    @Test fun `low pair = ++Z`() {
        val res = compileM8("r1.r0 = ++Z")

        res shouldHaveSize 2
        res[0] shouldBe "adiw z, 1"
        res[1] shouldBe "movw r1:r0, r31:r30"
    }

    @Test fun `low pair = --Y`() {
        val res = compileM8("r1.r0 = --Y")

        res shouldHaveSize 2
        res[0] shouldBe "sbiw y, 1"
        res[1] shouldBe "movw r1:r0, r29:r28"
    }

    @Test fun `low pair = X++`() {
        val res = compileM8("r1.r0 = X++")

        res shouldHaveSize 2
        res[0] shouldBe "movw r1:r0, r27:r26"
        res[1] shouldBe "adiw x, 1"
    }

    @Test fun `low pair = Z--`() {
        val res = compileM8("r1.r0 = Z--")

        res shouldHaveSize 2
        res[0] shouldBe "movw r1:r0, r31:r30"
        res[1] shouldBe "sbiw z, 1"
    }

    @Test fun `low pair = ++X`() {
        val res = compileM8("r1.r0 = ++X")

        res shouldHaveSize 2
        res[0] shouldBe "adiw x, 1"
        res[1] shouldBe "movw r1:r0, r27:r26"
    }

    @Test fun `low pair = ++Y`() {
        val res = compileM8("r1.r0 = ++Y")

        res shouldHaveSize 2
        res[0] shouldBe "adiw y, 1"
        res[1] shouldBe "movw r1:r0, r29:r28"
    }

    @Test fun `low pair = --Z`() {
        val res = compileM8("r1.r0 = --Z")

        res shouldHaveSize 2
        res[0] shouldBe "sbiw z, 1"
        res[1] shouldBe "movw r1:r0, r31:r30"
    }

    @Test fun `low pair = --X`() {
        val res = compileM8("r1.r0 = --X")

        res shouldHaveSize 2
        res[0] shouldBe "sbiw x, 1"
        res[1] shouldBe "movw r1:r0, r27:r26"
    }

    @Test fun `low pair = Y++`() {
        val res = compileM8("r1.r0 = Y++")

        res shouldHaveSize 2
        res[0] shouldBe "movw r1:r0, r29:r28"
        res[1] shouldBe "adiw y, 1"
    }

    @Test fun `low pair = X--`() {
        val res = compileM8("r1.r0 = X--")

        res shouldHaveSize 2
        res[0] shouldBe "movw r1:r0, r27:r26"
        res[1] shouldBe "sbiw x, 1"
    }

    @Test fun `low pair = Y--`() {
        val res = compileM8("r1.r0 = Y--")

        res shouldHaveSize 2
        res[0] shouldBe "movw r1:r0, r29:r28"
        res[1] shouldBe "sbiw y, 1"
    }

    @Test fun `low pair = Z++`() {
        val res = compileM8("r1.r0 = Z++")

        res shouldHaveSize 2
        res[0] shouldBe "movw r1:r0, r31:r30"
        res[1] shouldBe "adiw z, 1"
    }

    @Test fun `pair += pair`() {
        val res = compileM8("r25.r24 += r21.r20")

        res shouldHaveSize 2
        res[0] shouldBe "add r24, r20"
        res[1] shouldBe "adc r25, r21"
    }

    @Test fun `pair -= pair`() {
        val res = compileM8("r25.r24 -= r21.r20")

        res shouldHaveSize 2
        res[0] shouldBe "sub r24, r20"
        res[1] shouldBe "sbc r25, r21"
    }

    @Test fun `r24 or= r25`() {
        val res = compileM8("r24 |= r25")

        res shouldHaveSize 1
        res[0] shouldBe "or r24, r25"
    }

    @Test fun `r24 &= r25`() {
        val res = compileM8("r24 &= r25")

        res shouldHaveSize 1
        res[0] shouldBe "and r24, r25"
    }
}
