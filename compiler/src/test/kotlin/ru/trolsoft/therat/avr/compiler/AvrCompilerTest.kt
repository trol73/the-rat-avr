package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.generator.Endian
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.avr.AvrRatCompiler

class AvrCompilerTest {
    private val testConsole = ConsoleReporter({ }, { })

    @Test
    fun `byte var assign`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            var sUartRxBp: byte
            proc main() {
                sUartRxBp = ZL
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "sts 96, r30"
    }

    @Test
    fun `jmp table`() {
        val r = compileHex(
            """
        #define CPU "atmega8"
        proc main() {
            Z = SetModeTab/2
            ijmp
        SetModeTab:
            rjmp		main
        }
        """.trimIndent()
        )
        r.trim() shouldBe ":08000000F0E0E3E00994FCCFFD\n:00000001FF".trim()
    }

    @Test
    fun `assign prgptr`() {
        val r = compileHex(
            """
        #define CPU "atmega8"
        proc main() {
            Z = SetModeTab
        SetModeTab:
            byte[] {0x12, 0x34}
        }
        """.trimIndent()
        )
        r.trim() shouldBe ":06000000F0E0E4E0123420\n:00000001FF".trim()
    }

    @Test
    fun `simple inline`() {
        val r = compileCode(
            """
            #define CPU "atmega8"

            proc main() {
                cli
                nop2()
                sei
            }

            inline proc nop2() {
                nop
                nop
            }
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "cli"
        r[1] shouldBe "nop"
        r[2] shouldBe "nop"
        r[3] shouldBe "sei"
    }

    @Test
    fun `inline with one arg`() {
        val r = compileCode(
            """
            #define CPU "atmega8"

            proc main() {
                outPort(0x0f)
                nop
            }

            inline proc outPort(val: r24) {
                PORTD = val
            }
        """.trimIndent()
        )
        r shouldHaveSize 3
        r[0] shouldBe "ldi r24, 0xf"
        r[1] shouldBe "out 0x12, r24"
        r[2] shouldBe "nop"
    }

    @Test
    fun `inline with two args`() {
        val r = compileCode(
            """
            #define CPU "atmega8"

            proc main() {
                outPorts(val1: 0x0f, val2: 0)
                nop
            }

            inline proc outPorts(val1: r24, val2: r25) {
                PORTD = val1
                PORTC = val2
            }
        """.trimIndent()
        )
        r shouldHaveSize 5
        r[0] shouldBe "ldi r24, 0xf"
        r[1] shouldBe "clr r25"
        r[2] shouldBe "out 0x12, r24"
        r[3] shouldBe "out 0x15, r25"
        r[4] shouldBe "nop"
    }

    @Test
    fun `inline recursion does not allowed`() {
        shouldThrow<CompilerException> {
            compileCode(
                """
            #define CPU "atmega8"

            proc main() {
                myproc()
            }

            inline proc myproc() {
                myproc()
            }
        """.trimIndent()
            )
        }.message shouldBe "Recursive inline call"
    }

    @Test
    fun `single local use`() {
        val r = compileM8(
            """
            use r16 as tmp
            tmp = 10
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r16, 10"
    }

    @Test
    fun `multiple local use`() {
        val r = compileM8(
            """
            use r16 as tmp, r17 as second
            tmp = 10
            second = 20
        """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "ldi r16, 10"
        r[1] shouldBe "ldi r17, 20"
    }

    @Test
    fun `complex global use`() {
        val r = compileCode(
            """
                #define CPU "atmega8"
                
                use r24 as vR0
                use r25 as vR1
                use vR1.vR0 as vX
                
                proc main() {
                    vX = 0x1234
                }
            """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "ldi r25, 0x12"
        r[1] shouldBe "ldi r24, 0x34"
    }

    @Test
    fun `pin-pin access`() {
        val r = compileCode(
            """
                #define CPU "atmega8"
                pin pinKeyLeft = C1
                proc main() {
                    if (pinKeyLeft->pin) r24 = 0
                }
            """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "sbic 0x13, 1"
        r[1] shouldBe "clr r24"
    }

    @Test
    fun `inverted pin-pin access`() {
        val r = compileCode(
            """
                #define CPU "atmega8"
                pin pinKeyLeft = C1
                proc main() {
                    if (!pinKeyLeft->pin) r24 = 0
                }
            """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "sbis 0x13, 1"
        r[1] shouldBe "clr r24"
    }

    @Test
    fun `pin alias access`() {
        val r = compileCode(
            """
                #define CPU "atmega8"
                pin pin1 = C1
                pin pinKeyLeft = pin1
                proc main() {
                    if (pinKeyLeft->pin) r24 = 0
                }
            """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "sbic 0x13, 1"
        r[1] shouldBe "clr r24"
    }

    @Test
    fun `call func with args`() {
        val r = compileCode(
            """
                #define CPU "atmega8"
                proc main() {
                    rcall	setAddrWindow (y1: r18+1, x1: r24)
                }

                proc setAddrWindow (x0: r24, y0:r22, x1:r20, y1:r18) {
                }
            """.trimIndent()
        )

        r shouldHaveSize 4
        r[0] shouldBe "inc r18"
        r[1] shouldBe "mov r20, r24"
        r[2] shouldBe "rcall setaddrwindow"
        r[3] shouldBe "setaddrwindow:"
    }

    @Test
    fun `call func with pair arg`() {
        val r = compileCode(
            """
                #define CPU "atmega8"
                proc main() {
                    rjmp	command(r21.r24)
                }

                proc command(w: r25.r24) {
                }
            """.trimIndent()
        )
        r shouldHaveSize 3
        r[0] shouldBe "mov r25, r21"
        r[1] shouldBe "rjmp command"
        r[2] shouldBe "command:"
    }

    @Test
    fun `simple next function pseudo-call`() {
        val r = compileCode(
            """
                #define CPU "atmega8"
                proc func_0() {
                    r24 = 0
                    func()
                }
                proc func(a: r24) {
                    nop
                }
            """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "func_0:"
        r[1] shouldBe "clr r24"
        r[2] shouldBe "func:"
        r[3] shouldBe "nop"
    }

    @Test
    fun `next function pseudo-call with arg`() {
        val r = compileCode(
            """
                #define CPU "atmega8"
                proc func_0() {
                    func(0)
                }
                proc func(a: r24) {
                    nop
                }
            """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "func_0:"
        r[1] shouldBe "clr r24"
        r[2] shouldBe "func:"
        r[3] shouldBe "nop"
    }

    @Test
    fun `next function pseudo-call with args`() {
        val r = compileCode(
            """
                #define CPU "atmega8"
                proc func_0() {
                    func(a: 0, b: 1)
                }
                proc func(a: r24, b: r25) {
                    nop
                }
            """.trimIndent()
        )
        r shouldHaveSize 5
        r[0] shouldBe "func_0:"
        r[1] shouldBe "clr r24"
        r[2] shouldBe "ldi r25, 1"
        r[3] shouldBe "func:"
        r[4] shouldBe "nop"
    }

    @Test
    fun `no-return warning`() {
        val warnings = mutableListOf<String>()
        compileCode(
            """
                #define CPU "atmega8"
                proc func() {
                    r1 = 0
                }
            """.trimIndent(), false, warnings
        )
        warnings shouldHaveSize 1
        warnings.first() shouldEndWith "function does not end with a return or an unconditional jump"
    }

    @Test
    fun `function with if-else at end shows no warnings`() {
        val warnings = mutableListOf<String>()
        compileCode(
            """
                #define CPU "atmega8"
                proc func() {
                    if (r1 == 0) {
                        ret
                    } else {
                        Z = 1
                        ijmp
                    }
                }
            """.trimIndent(), false, warnings
        )
        warnings.shouldBeEmpty()
    }

    @Test
    fun `function with data at end shows no warnings`() {
        val warnings = mutableListOf<String>()
        compileCode(
            """
                #define CPU "atmega8"
                proc func() {
                    ret
                    data1: byte[] {1, 2, 3, 4}
                    data2: byte[] {5, 6, 7, 8}
                }
            """.trimIndent(), false, warnings
        )
        warnings.shouldBeEmpty()
    }

    @Test
    fun `duplicate proc arg register warning`() {
        val warnings = mutableListOf<String>()
        compileCode(
            """
                #define CPU "atmega8"
                proc drawPixel(x: r24, y: r24) {
                    ret
                }
            """.trimIndent(), false, warnings
        )
        warnings shouldHaveSize 1
        warnings.first() shouldEndWith "Proc argument r24 already used"
    }

    @Test
    fun `duplicate prox arg name error`() {
        shouldThrow<CompilerException> {
            compileCode(
                """
                    #define CPU "atmega8"
                    proc drawPixel(x: r24, x: r25) {
                        ret
                    }
                """.trimIndent()
            )
        }.message shouldBe "Argument already exists: x"
    }


// TODO need to implements bit arguments and uses

//    @Test fun `call func with single set bit arg`() {
//        val r = compileCode("""
//                #define CPU "atmega8"
//                proc main() {
//                    rjmp	command(12, 1)
//                }
//
//                proc command(data: r24, flag: r25[0]) {
//                }
//            """.trimIndent())
//        r shouldHaveSize 3
//        r[0] shouldBe "ldi r24, 12"
//        r[1] shouldBe "rjmp command"
//        r[2] shouldBe "command:"
//    }
//
//    @Test fun `call func with two set bit args`() {
//        val r = compileCode("""
//                #define CPU "atmega8"
//                proc main() {
//                    rjmp	command(12, 1, 1)
//                }
//
//                proc command(data: r24, flag1: r25[0], flag2: r25[1]) {
//                }
//            """.trimIndent())
//        r shouldHaveSize 3
//        r[0] shouldBe "ldi r24, 12"
//        r[1] shouldBe "rjmp command"
//        r[2] shouldBe "command:"
//    }
//
//    @Test fun `call func with single clear bit arg`() {
//        val r = compileCode("""
//                #define CPU "atmega8"
//                proc main() {
//                    rjmp	command(12, 0)
//                }
//
//                proc command(data: r24, flag: r25[0]) {
//                }
//            """.trimIndent())
//        r shouldHaveSize 3
//        r[0] shouldBe "ldi r24, 12"
//        r[1] shouldBe "rjmp command"
//        r[2] shouldBe "command:"
//    }
//
//    @Test fun `call func with two clear bit args`() {
//        val r = compileCode("""
//                #define CPU "atmega8"
//                proc main() {
//                    rjmp	command(12, 0, 0)
//                }
//
//                proc command(data: r24, flag1: r25[0], flag2: r25[1]) {
//                }
//            """.trimIndent())
//        r shouldHaveSize 3
//        r[0] shouldBe "ldi r24, 12"
//        r[1] shouldBe "rjmp command"
//        r[2] shouldBe "command:"
//    }
//
//    @Test fun `call func with two different bit args`() {
//        val r = compileCode("""
//                #define CPU "atmega8"
//                proc main() {
//                    rjmp	command(12, 0, 1)
//                }
//
//                proc command(data: r24, flag1: r25[0], flag2: r25[1]) {
//                }
//            """.trimIndent())
//        r shouldHaveSize 3
//        r[0] shouldBe "ldi r24, 12"
//        r[1] shouldBe "rjmp command"
//        r[2] shouldBe "command:"
//    }

    @Test
    fun `add word table offset to Z`() {
        val r = compileHex(
            """
            #define CPU "atmega8"
            proc main() {
                Z += word_data_table
            }
            
            word_data_table: word[] {
                0
            }
            """.trimIndent()
        )
        r shouldBe ":06000000EC5FFF4F000061\n:00000001FF\n"
    }


    @Test
    fun `data block of byte`() {
        val r = compileHex(
            """
                #define CPU "atmega8"
                proc main() {
                    nop
                }
                byte[] {
                    0x12
                    0x34
                }
        """.trimIndent()
        )
        r.trim() shouldBe ":0400000000001234B6\n:00000001FF".trim()
    }

    @Test
    fun `data block of byte with char and strings`() {
        val r = compileHex(
            """
                #define CPU "atmega8"
                proc main() {
                    nop
                }
                byte[] {
                    'a', 'b'
                    "Str\n"
                }

        """.trimIndent()
        )
        r.trim() shouldBe ":08000000000061625374720AF2\n:00000001FF".trim()
    }

    @Test
    fun `data block of word`() {
        val r = compileHex(
            """
                #define CPU "atmega8"
                proc main() {
                    ret
                }
                word[] {
                    0x1234
                    0xefab
                }
        """.trimIndent()
        )
        r.trim() shouldBe ":0600000008953412ABEF7D\n:00000001FF".trim()
    }

    @Test
    fun `data block of dword`() {
        val r = compileHex(
            """
                #define CPU "atmega8"
                proc main() {
                    ret
                }
                dword[] {
                    0x12345678
                    0xABCDEF01
                }

        """.trimIndent()
        )
        r.trim() shouldBe ":0A00000008957856341201EFCDABDD\n:00000001FF".trim()
    }

    @Test
    fun `data block address`() {
        val r = compileHex(
            """
                #define CPU "atmega8"
                proc main() {
                    ret
                }
                proc p1() {
                    ret
                }
                proc p2() {
                    reti
                }
                word[] {
                    main
                    p1
                    p2
                }
        """.trimIndent()
        )

        r.trim() shouldBe ":0C00000008950895189500000200040007\n:00000001FF".trim()
    }

    @Test
    fun `data block address in words`() {
        val r = compileHex(
            """
                #define CPU "atmega8"
                proc main() {
                    ret
                }
                proc p1() {
                    ret
                }
                proc p2() {
                    reti
                }
                word[] {
                    main / 2
                    p1 / 2
                    p2 / 2
                }
        """.trimIndent()
        )
        r.trim() shouldBe ":0C0000000895089518950000010002000A\n:00000001FF".trim()
    }

    @Test
    fun `reg = bitmask`() {
        val r = compileM8(
            """
            r30 = bitmask(1)
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r30, 0b10"
    }

    @Test
    fun `reg or= bitmask`() {
        val r = compileM8(
            """
            r30 |= bitmask(7)
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ori r30, 0b10000000"
    }

    @Test
    fun `reg &= ~bitmask`() {
        val r = compileM8(
            """
            r30 &= ~bitmask(1)
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "andi r30, 253"
    }

    @Test
    fun `r24 = ~bitmask(5, 6, 7)`() {
        val r = compileM8(
            """
            r24 = ~bitmask(5, 6, 7)
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r24, 31"
    }


    @Test
    fun `var = ++reg`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            var v : byte
            proc main() {
                v = ++r30
            }
        """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "inc r30"
        r[1] shouldBe "sts 96, r30"
    }

    @Test
    fun `var = reg++`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            var v : byte
            proc main() {
                v = r30++
            }
        """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "sts 96, r30"
        r[1] shouldBe "inc r30"
    }

    @Test
    fun `if (flag) reg = mem`() {
        val r = compileM8(
            """
            if (ADCSRA->ADPS1) r24 = mem[Z++]
        """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "sbic 0x6, 1"
        r[1] shouldBe "ld r24, z+"
    }

    @Test
    fun `pair += pair + pair`() {
        val r = compileM8(
            """
            Z += r1.r0 + r3.r2
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "add r30, r0"
        r[1] shouldBe "adc r31, r1"
        r[2] shouldBe "add r30, r2"
        r[3] shouldBe "adc r31, r3"
    }

    @Test
    fun `pair += pair - pair`() {
        val r = compileM8(
            """
            Z += r1.r0 - r3.r2
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "add r30, r0"
        r[1] shouldBe "adc r31, r1"
        r[2] shouldBe "sub r30, r2"
        r[3] shouldBe "sbc r31, r3"
    }

    @Test
    fun `pair -= pair + pair`() {
        val r = compileM8(
            """
            Z -= r1.r0 + r3.r2
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "sub r30, r0"
        r[1] shouldBe "sbc r31, r1"
        r[2] shouldBe "sub r30, r2"
        r[3] shouldBe "sbc r31, r3"
    }

    @Test
    fun `pair -= pair - pair`() {
        val r = compileM8(
            """
            Z -= r1.r0 - r3.r2
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "sub r30, r0"
        r[1] shouldBe "sbc r31, r1"
        r[2] shouldBe "add r30, r2"
        r[3] shouldBe "adc r31, r3"
    }

    @Test
    fun `r1_r10 += r2_r20 + r3_r30`() {
        val r = compileM8(
            """
            r1.r10 += r2.r20 + r3.r30
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "add r10, r20"
        r[1] shouldBe "adc r1, r2"
        r[2] shouldBe "add r10, r30"
        r[3] shouldBe "adc r1, r3"
    }

    @Test
    fun `r1_r10 += r2_r20 - r3_r30`() {
        val r = compileM8(
            """
            r1.r10 += r2.r20 - r3.r30
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "add r10, r20"
        r[1] shouldBe "adc r1, r2"
        r[2] shouldBe "sub r10, r30"
        r[3] shouldBe "sbc r1, r3"
    }

    @Test
    fun `r1_r10 -= r2_r20 + r3_r30`() {
        val r = compileM8(
            """
            r1.r10 -= r2.r20 + r3.r30
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "sub r10, r20"
        r[1] shouldBe "sbc r1, r2"
        r[2] shouldBe "sub r10, r30"
        r[3] shouldBe "sbc r1, r3"
    }

    @Test
    fun `r1_r10 -= r2_r20 - r3_r30`() {
        val r = compileM8(
            """
            r1.r10 -= r2.r20 - r3.r30
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "sub r10, r20"
        r[1] shouldBe "sbc r1, r2"
        r[2] shouldBe "add r10, r30"
        r[3] shouldBe "adc r1, r3"
    }

    @Test
    fun `pair += aliased pair + pair`() {
        val r = compileM8(
            """
            use r1 as hi1
            use r3 as hi2
            Z += hi1.r0 + hi2.r2
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "add r30, r0"
        r[1] shouldBe "adc r31, r1"
        r[2] shouldBe "add r30, r2"
        r[3] shouldBe "adc r31, r3"
    }

    @Test
    fun `simple saveregs`() {
        val r = compileM8(
            """
            saveregs(r1, r10) {
                nop
            }
        """.trimIndent()
        )
        r shouldHaveSize 5
        r[0] shouldBe "push r1"
        r[1] shouldBe "push r10"
        r[2] shouldBe "nop"
        r[3] shouldBe "pop r10"
        r[4] shouldBe "pop r1"
    }

    @Test
    fun `saveregs with alias`() {
        val r = compileM8(
            """
            use r5 as x
            saveregs(r1, x) {
                nop
            }
        """.trimIndent()
        )
        r shouldHaveSize 5
        r[0] shouldBe "push r1"
        r[1] shouldBe "push r5"
        r[2] shouldBe "nop"
        r[3] shouldBe "pop r5"
        r[4] shouldBe "pop r1"
    }

    @Test
    fun `saveregs with SREG`() {
        val r = compileM8(
            """
            saveregs(r1, SREG) {
                nop
            }
        """.trimIndent()
        )
        r shouldHaveSize 7
        r[0] shouldBe "push r1"
        r[1] shouldBe "in r1, 0x3f"
        r[2] shouldBe "push r1"
        r[3] shouldBe "nop"
        r[4] shouldBe "pop r1"
        r[5] shouldBe "out 0x3f, r1"
        r[6] shouldBe "pop r1"
    }

    @Test
    fun `saveregs with pair`() {
        val r = compileM8(
            """
            saveregs(r1, Z) {
                nop
            }
        """.trimIndent()
        )
        r shouldHaveSize 7
        r[0] shouldBe "push r1"
        r[1] shouldBe "push r31"
        r[2] shouldBe "push r30"
        r[3] shouldBe "nop"
        r[4] shouldBe "pop r30"
        r[5] shouldBe "pop r31"
        r[6] shouldBe "pop r1"
    }

    @Test
    fun `saveregs with group`() {
        val r = compileM8(
            """
            saveregs(r25.r24, SREG) {
                nop
            }
        """.trimIndent()
        )
        r shouldHaveSize 9
        r[0] shouldBe "push r25"
        r[1] shouldBe "push r24"
        r[2] shouldBe "in r25, 0x3f"
        r[3] shouldBe "push r25"
        r[4] shouldBe "nop"
        r[5] shouldBe "pop r24"
        r[6] shouldBe "out 0x3f, r24"
        r[7] shouldBe "pop r24"
        r[8] shouldBe "pop r25"
    }

    @Test
    fun `saveregs with first SREG error`() {
        shouldThrow<CompilerException> {
            compileM8(
                """
                saveregs(SREG, r1) {
                    nop
                }
            """.trimIndent()
            )
        }.message shouldBe "SREG cannot be the first argument here"
    }

    @Test
    fun `Z -= prg offset`() {
        val r = compileHex(
            $$"""
            #define CPU "atmega8"
            
            proc main() {
                Z -= data_table
            }
            
            $org(0x0CE8)
            proc empty() {}
            
            data_table:
            byte[] {
                0x12, 0x34
            }
        """.trimIndent()
        )
        r shouldBe ":04000000E85EFC407A\n:020CE8001234C4\n:00000001FF\n"
    }


    @Test
    fun `Z += prg offset`() {
        val r = compileHex(
            $$"""
            #define CPU "atmega8"
            
            proc main() {
                Z += data_table
            }
            
            $org(0x0CE8)
            proc empty() {
            }
            
            data_table:
            byte[] {
                0x12, 0x34
            }
        """.trimIndent()
        )
        r shouldBe ":04000000E851F34F81\n:020CE8001234C4\n:00000001FF\n"
    }
    // TODO этот тест должен работать и без пустой функции

    @Test
    fun defines() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            #define UART_LCD_RX_BUFFER_SIZE	    16
            #define UART_LCD_RX_BUFFER_MASK		(UART_LCD_RX_BUFFER_SIZE - 1)

            proc main() {
                r20 = UART_LCD_RX_BUFFER_MASK
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r20, 15"
    }

    @Test
    fun `compileHex for loop with register pair`() {
        val r = compileHex(
            """
            #define CPU "atmega8"
            
            proc delay_ms_long() {
                loop (r25.r24) {
                    nop
                }
                ret
            }
        """.trimIndent()
        )
        r.trim() shouldBe ":0800000000000197E9F70895E3\n:00000001FF".trim()
    }

    @Test
    fun `compileHex for loop with ZHZL pair`() {
        val r = compileHex(
            """
            #define CPU "atmega8"
            
            proc delay_ms_long() {
                loop (ZH.ZL) {
                    nop
                }
                ret
            }
        """.trimIndent()
        )
        r.trim() shouldBe ":0800000000003197E9F70895B3\n:00000001FF".trim()
    }

    @Test
    fun `inline call with aliased argument`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            use r16 as tr1
            
            inline proc set_ddr(v: tr1) {
                DDRB = v
            }
            
            proc main() {
                set_ddr(123)
            }
        """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "ldi r16, 123"
        r[1] shouldBe "out 0x17, r16"
    }

    @Test
    fun `inline call with two aliased arguments`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            use r16 as tr1
            use r17 as tr2
            
            inline proc set_ddrs(v1: tr1, v2: tr2) {
                DDRB = v1
                DDRC = v2
            }
            
            proc main() {
                set_ddrs(v1: 123, v2: 0)
            }
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "ldi r16, 123"
        r[1] shouldBe "clr r17"
        r[2] shouldBe "out 0x17, r16"
        r[3] shouldBe "out 0x14, r17"
    }

    @Test
    fun `loop with X pair initialization`() {
        val r = compileM8(
            """
            loop (X = 3200) {
            }
        """.trimIndent()
        )

        r shouldHaveSize 6
        r[0] shouldBe "ldi r27, 12"
        r[1] shouldBe "ldi r26, 128"
        r[2] shouldBe "main@loop@3:"
        r[3] shouldBe "sbiw x, 1"
        r[4] shouldBe "brne main@loop@3"
        r[5] shouldBe "main@loopend@3:"
    }

    @Test
    fun `loop with any pair initialization`() {
        val r = compileM8(
            """
            loop (r21.r20 = r1.r0) {
            }
        """.trimIndent()
        )

        r shouldHaveSize 6
        r[0] shouldBe "movw r21:r20, r1:r0"
        r[1] shouldBe "main@loop@3:"
        r[2] shouldBe "subi r20, 1"
        r[3] shouldBe "sbci r21, 0"
        r[4] shouldBe "brne main@loop@3"
        r[5] shouldBe "main@loopend@3:"
    }


    @Test
    fun `reg alias of alias`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            use r20 as tempReg
            
            proc main() {
                use tempReg as c
                c = PORTC
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "in r20, 0x15"
    }

    @Test
    fun vectors() {
        val r = compileCode(
            """
            #define CPU "atmega8"

            vectors {
                RESET:
                    rjmp	main
                INT1:
                    r24 = 2
                TIMER2_COMP:
                    PORTB = r20
            }
            proc main() {
            }
        """.trimIndent()
        )
        r shouldHaveSize 20
        r[0] shouldBe "rjmp main"
        r[1] shouldBe "reti"
        r[2] shouldBe "ldi r24, 2"
        r[3] shouldBe "out 0x18, r20"
        r[4] shouldBe "reti"
        r[18] shouldBe "reti"
        r[19] shouldBe "main:"
    }

    @Test
    fun `vectors with big item`() {
        shouldThrow<CompilerException> {
            compileCode(
                """
            #define CPU "atmega8"

            vectors {
                INT1:
                    r24 = r25+1
                TIMER2_COMP:
                    PORTB = r20
            }
        """.trimIndent()
            )
        }.message shouldBe "Invalid interrupt handler size. Expected 1 word but found 2 words"
    }

    @Test
    fun `vectors with small item`() {
        shouldThrow<CompilerException> {
            compileCodeM128(
                """
            #define CPU "atmega128"

            vectors {
                INT1:
                    r24 = r25+1
                TIMER2_COMP:
                    PORTB = r20
            }
        """.trimIndent()
            )
        }.message shouldBe "Invalid interrupt handler size. Expected 2 words but found 1 word"
    }

    @Test
    fun `unexpected identifier`() {
        shouldThrow<CompilerException> {
            compileM8(
                """
                SOMETHING                
            """.trimIndent()
            )
        }.message shouldBe "Unexpected expression"
    }


    @Test
    fun `multiple inline procedures with loops`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            pin pin1 = C7
            pin pin2 = C6
            
            inline proc waitPin1() {
                loop (!pin1->pin) {}
            }
            
            inline proc waitPin2() {
                loop (!pin2->pin) {}
            }
            
            proc main() {
                waitPin1()
                waitPin2()
                waitPin1()
            }
        """.trimIndent()
        )
        r shouldHaveSize 12

        val lblLoopStart1 = r[0].removeSuffix(":")
        r[1] shouldBe "sbis 0x13, 7"
        r[2] shouldBe "rjmp $lblLoopStart1"

        val lblLoopStart2 = r[4].removeSuffix(":")
        r[5] shouldBe "sbis 0x13, 6"
        r[6] shouldBe "rjmp $lblLoopStart2"

        val lblLoopStart3 = r[8].removeSuffix(":")
        r[9] shouldBe "sbis 0x13, 7"
        r[10] shouldBe "rjmp $lblLoopStart3"
    }

    @Test
    fun `assign CARRY flag from IO bit`() {
        val r = compileM8(
            """
            SREG->C = PINC->1
        """.trimIndent()
        )
        r shouldHaveSize 3
        r[0] shouldBe "bclr 0"
        r[1] shouldBe "sbic 0x13, 1"
        r[2] shouldBe "bset 0"
    }

    @Test
    fun `assign ZERO flag from inverted IO bit`() {
        val r = compileM8(
            """
            SREG->Z = !PINC->1
        """.trimIndent()
        )
        r shouldHaveSize 3
        r[0] shouldBe "bclr 1"
        r[1] shouldBe "sbis 0x13, 1"
        r[2] shouldBe "bset 1"
    }

    @Test
    fun `use pin as bit number`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            pin pin1 = C7

            proc main() {
                r24[pin1] = 1
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "sbr r24, 128"
    }

    @Test
    fun `error on no memory`() {
        shouldThrow<CompilerException> {
            compileCode(
                """
                #define CPU "atmega8"
                
                var buf: byte[1024]
                var b: byte
                proc main() {
                    r24 = b
                }
            """.trimIndent()
            )
        }.message shouldBe "No RAM (used 1025 from 1024 bytes)"
    }

    @Test
    fun `output directive`() {
        val loader = TestFileLoaderStub(
            $$"""
            $output("file.hex")
        """.trimIndent()
        )
        val compiler = AvrRatCompiler("", "", testConsole, loader)
        compiler.compile("file.art")
        compiler.getOutputName() shouldBe "file.hex"
    }

    @Test
    fun `push and pop Y`() {
        val r = compileM8(
            """
            push    Y
            pop     Y
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "push r29"
        r[1] shouldBe "push r28"
        r[2] shouldBe "pop r28"
        r[3] shouldBe "pop r29"
    }

    @Test
    fun `push and pop pair`() {
        val r = compileM8(
            """
            push    r21.r10
            pop     r21.r10
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "push r21"
        r[1] shouldBe "push r10"
        r[2] shouldBe "pop r10"
        r[3] shouldBe "pop r21"
    }

    @Test
    fun `test ram size calculation`() {
        val loader = TestFileLoaderStub(
            """
            #define CPU "atmega8"
            var x: byte
            var y: word
            var ab: byte[10]
            var p: ptr
        """.trimIndent()
        )
        val compiler = AvrRatCompiler("", "", testConsole, loader).apply {
            deviceLoader = TestMega8LoaderStub()
            addIntelHexGenerator(Endian.LITTLE)
            compile("file.art")
        }
        compiler.getRamSize() shouldBe 15
    }

    @Test
    fun `test ram size calculation with struct`() {
        val loader = TestFileLoaderStub(
            """
            #define CPU "atmega8"
            
            struct {
                byte x
                word y
            } my_struct_t
            
            var ms: my_struct_t
        """.trimIndent()
        )
        val compiler = AvrRatCompiler("", "", testConsole, loader).apply {
            deviceLoader = TestMega8LoaderStub()
            addIntelHexGenerator(Endian.LITTLE)
            compile("file.art")
        }
        compiler.getRamSize() shouldBe 3
    }

    @Test
    fun `test ram size calculation with array of structs`() {
        val loader = TestFileLoaderStub(
            """
            #define CPU "atmega8"
            
            struct {
                byte x
                word y
            } my_struct_t
            
            var ms: my_struct_t[5]
        """.trimIndent()
        )
        val compiler = AvrRatCompiler("", "", testConsole, loader).apply {
            deviceLoader = TestMega8LoaderStub()
            addIntelHexGenerator(Endian.LITTLE)
            compile("file.art")
        }
        compiler.getRamSize() shouldBe 15
    }

    @Test
    fun `test ram size calculation with array of structs with arrays`() {
        val loader = TestFileLoaderStub(
            """
            #define CPU "atmega8"
            
            struct {
                byte x[8]
                word y
            } my_struct_t
            
            var ms: my_struct_t[5]
        """.trimIndent()
        )
        val compiler = AvrRatCompiler("", "", testConsole, loader).apply {
            deviceLoader = TestMega8LoaderStub()
            addIntelHexGenerator(Endian.LITTLE)
            compile("file.art")
        }
        compiler.getRamSize() shouldBe 50
    }

    @Test
    fun `pad_to directive`() {
        val r = compileCode(
            $$"""
            #define CPU "atmega8"
                        
            $pad_to(20)
            proc main() {
                r21 = 0
                ret
            }
        """.trimIndent()
        )

        r shouldHaveSize 10
        r[0] shouldBe "clr r21"
        r[1] shouldBe "ret"
        r[2] shouldBe "nop"
        r[3] shouldBe "nop"
        r[4] shouldBe "nop"
        r[5] shouldBe "nop"
        r[6] shouldBe "nop"
        r[7] shouldBe "nop"
        r[8] shouldBe "nop"
        r[9] shouldBe "nop"
    }

    @Test
    fun `pad_to directive not enough size`() {
        val warnings = mutableListOf<String>()
        val r = compileCode(
            $$"""
            #define CPU "atmega8"
                        
            $pad_to(2)
            proc main() {
                r21 = 0
                ret
            }
        """.trimIndent(), false, warningsList = warnings
        )
        warnings shouldHaveSize 1
        warnings.first() shouldBe $$"file.art:3:1: $pad_to(1) violated for 'main': body is 4 bytes, target is 2"

        r shouldHaveSize 2
        r[0] shouldBe "clr r21"
        r[1] shouldBe "ret"

    }

    @Test
    fun `proc with $org`() {
        compileHex(
            $$"""
            #define CPU "atmega8"

            $org(0x1000)
            proc main() {
                nop
            }
        """.trimIndent()
        ).trim() shouldBe ":021000000000EE\n:00000001FF"
    }

    @Test
    fun `inline proc with loop`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            inline proc wait() {
                loop (r24 = 10) {}
            }
            
            proc main() {
                wait()
                r1 = 0
                wait()
                ret
            }
        """.trimIndent()
        )

        r shouldHaveSize 12
        r[0] shouldBe "ldi r24, 10"
        r[1] shouldEndWith ":"
        val lblLoop1 = r[1].removeSuffix(":")
        r[2] shouldBe "dec r24"
        r[3] shouldBe "brne $lblLoop1"
        r[4] shouldEndWith ":"
        r[5] shouldBe "clr r1"
        r[6] shouldBe "ldi r24, 10"
        r[7] shouldEndWith ":"
        val lblLoop2 = r[7].removeSuffix(":")
        r[8] shouldBe "dec r24"
        r[9] shouldBe "brne $lblLoop2"
        r[10] shouldEndWith ":"
        r[11] shouldBe "ret"
    }

    @Test
    fun `simple proc with result param as alias`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            proc read_uart() -> (res: r24) {
                res = 1
                ret
            }
        """.trimIndent()
        )

        r shouldHaveSize 3
        r[0] shouldBe "read_uart:"
        r[1] shouldBe "ldi r24, 1"
        r[2] shouldBe "ret"
    }

    @Test
    fun `simple proc with result call`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            proc read_uart() -> (res: r24) {
                res = 1
                ret
            }
            
            proc main() {
                rcall read_uart() -> r0
                ret
            }
        """.trimIndent()
        )

        r shouldHaveSize 7
        r[0] shouldBe "read_uart:"
        r[1] shouldBe "ldi r24, 1"
        r[2] shouldBe "ret"
        r[3] shouldBe "main:"
        r[4] shouldBe "rcall read_uart"
        r[5] shouldBe "mov r0, r24"
        r[6] shouldBe "ret"
    }

    @Test
    fun `simple proc with two results call`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            proc read_uart_word() -> (lo: r24, hi: r25) {
                lo = 1
                hi = 10
                ret
            }
            
            proc main() {
                rcall read_uart_word() -> (lo: r0, hi: r1)
                ret
            }
        """.trimIndent()
        )

        r shouldHaveSize 9
        r[0] shouldBe "read_uart_word:"
        r[1] shouldBe "ldi r24, 1"
        r[2] shouldBe "ldi r25, 10"
        r[3] shouldBe "ret"
        r[4] shouldBe "main:"
        r[5] shouldBe "rcall read_uart_word"
        r[6] shouldBe "mov r0, r24"
        r[7] shouldBe "mov r1, r25"
        r[8] shouldBe "ret"
    }
}