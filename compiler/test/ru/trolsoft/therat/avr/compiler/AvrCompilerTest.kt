package ru.trolsoft.therat.avr.compiler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.trolsoft.compiler.compiler.CompilerException
import kotlin.test.assertEquals

class AvrCompilerTest {
    @Test fun testByteVarAssign() {
        val r = compileCode("""
            #define CPU "atmega8"
            var sUartRxBp: byte
            proc main() {
                sUartRxBp = ZL
            }
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("sts 96, r30", r[0])
    }

    @Test fun testJmpTable() {
        val r = compileHex("""
        #define CPU "atmega8"
        proc main() {
            Z = SetModeTab/2
            ijmp
        SetModeTab:
            rjmp		main
        }
        """.trimIndent())
        assertEquals(":08000000F0E0E3E00994FCCFFD\n:00000001FF".trim(), r.trim())
    }

    @Test fun testAssignPtr() {
        val r = compileHex("""
        #define CPU "atmega8"
        proc main() {
            Z = SetModeTab
        SetModeTab:
            byte[] {0x12, 0x34}
        }
        """.trimIndent())
        assertEquals(":06000000F0E0E4E0123420\n:00000001FF".trim(), r.trim())
    }

    @Test fun testSimpleInline() {
        val r = compileCode("""
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
        """.trimIndent())
        assertEquals(4, r.size)
        assertEquals("cli", r[0])
        assertEquals("nop", r[1])
        assertEquals("nop", r[2])
        assertEquals("sei", r[3])
    }

    @Test fun testOneArgInline() {
        val r = compileCode("""
            #define CPU "atmega8"

            proc main() {
                outPort(0x0f)
                nop
            }

            inline proc outPort(val: r24) {
                PORTD = val
            }
        """.trimIndent())
        assertEquals(3, r.size)
        assertEquals("ldi r24, 15", r[0])
        assertEquals("out 18, r24", r[1])
        assertEquals("nop", r[2])
    }

    @Test fun testNestedSelfInlineError() {
        val exception = assertThrows<CompilerException> {
                val r = compileCode("""
                #define CPU "atmega8"

                proc main() {
                    myproc()
                }

                inline proc myproc() {
                    myproc()
                }
            """.trimIndent())
        }
        assertEquals("Recursive inline call", exception.message)
    }

    @Test fun testSingleLocalUse() {
        val r = compileM8("""
            use r16 as tmp
            tmp = 10
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("ldi r16, 10", r[0])
    }

    @Test fun testMultipleLocalUse() {
        val r = compileM8("""
            use r16 as tmp, r17 as second
            tmp = 10
            second = 20
        """.trimIndent())
        assertEquals(2, r.size)
        assertEquals("ldi r16, 10", r[0])
        assertEquals("ldi r17, 20", r[1])
    }

    @Test fun testPinPinAccess() {
        val r = compileCode("""
                #define CPU "atmega8"
                pin pinKeyLeft = C1
                proc main() {
                    if (pinKeyLeft->pin) r24 = 0
                }
            """.trimIndent())
        assertEquals(2, r.size)
        assertEquals("sbic 19, 1", r[0])
        //assertTrue(r[1].startsWith("?") && r[1].endsWith(":"))
        assertEquals("clr r24", r[1])
        //assertTrue(r[3].startsWith("?") && r[3].endsWith(":"))
    }

    @Test fun testPinPinAccessInv() {
        val r = compileCode("""
                #define CPU "atmega8"
                pin pinKeyLeft = C1
                proc main() {
                    if (!pinKeyLeft->pin) r24 = 0
                }
            """.trimIndent())
        assertEquals(2, r.size)
        assertEquals("sbis 19, 1", r[0])
        //assertTrue(r[1].startsWith("?") && r[1].endsWith(":"))
        assertEquals("clr r24", r[1])
        //assertTrue(r[3].startsWith("?") && r[3].endsWith(":"))
    }

    @Test fun testCallFuncWithArgs() {
        val r = compileCode("""
                #define CPU "atmega8"
                proc main() {
                    rcall	setAddrWindow (y1: r18+1, x1: r24)
                }

                proc setAddrWindow (x0: r24, y0:r22, x1:r20, y1:r18) {
                }
            """.trimIndent())

        assertEquals(4, r.size)
        assertEquals("inc r18", r[0])
        assertEquals("mov r20, r24", r[1])
        assertEquals("rcall setaddrwindow", r[2])
        assertEquals("setaddrwindow:", r[3])
    }

    @Test fun testCallFuncWithPairArg() {
        val r = compileCode("""
                #define CPU "atmega8"
                proc main() {
                    rjmp	command(r21.r24)
                }

                proc command(w: r25.r24) {
                }
            """.trimIndent())

        assertEquals(3, r.size)
        assertEquals("mov r25, r21", r[0])
        assertEquals("rjmp command", r[1])
        assertEquals("command:", r[2])
    }

    @Test fun testAddWordTableOffsetToZ() {
        val r = compileCode("""
            #define CPU "atmega8"
            proc main() {
                Z += word_data_table
            }
            
            word_data_table: word[] {
                0
            }
            """.trimIndent())

        assertEquals(4, r.size)
        assertEquals("subi r30, 1", r[0])
        assertEquals("sbci r31, 257", r[1])
        assertEquals("word_data_table:", r[2])
        assertEquals(".dw 0", r[3])
    }


    @Test fun testDataBlockByte() {
        val r = compileHex("""
                #define CPU "atmega8"
                proc main() {
                    nop
                }
                byte[] {
                    0x12
                    0x34
                }

        """.trimIndent())
        assertEquals(":0400000000001234B6\n:00000001FF".trim(), r.trim())
    }

    @Test fun testDataBlockByteChar() {
        val r = compileHex("""
                #define CPU "atmega8"
                proc main() {
                    nop
                }
                byte[] {
                    'a', 'b'
                    "Str\n"
                }

        """.trimIndent())
        assertEquals(":08000000000061625374720AF2\n:00000001FF".trim(), r.trim())
    }

    @Test fun testDataBlockWord() {
        val r = compileHex("""
                #define CPU "atmega8"
                proc main() {
                    ret
                }
                word[] {
                    0x1234
                    0xefab
                }

        """.trimIndent())
        assertEquals(":0600000008953412ABEF7D\n:00000001FF".trim(), r.trim())
    }

    @Test fun testDataBlockDword() {
        val r = compileHex("""
                #define CPU "atmega8"
                proc main() {
                    ret
                }
                dword[] {
                    0x12345678
                    0xABCDEF01
                }

        """.trimIndent())

        assertEquals(":0A00000008957856341201EFCDABDD\n:00000001FF".trim(), r.trim())
    }

    @Test fun testDataBlockAddr() {
        val r = compileHex("""
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
        """.trimIndent())

        assertEquals(":0C00000008950895189500000200040007\n:00000001FF".trim(), r.trim())
    }

    @Test fun testDataBlockAddrInWords() {
        val r = compileHex("""
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
        """.trimIndent())

        assertEquals(":0C0000000895089518950000010002000A\n:00000001FF".trim(), r.trim())
    }

    @Test fun testAssignBitmaskToReg() {
        val r = compileM8("""
            r30 = bitmask(1)
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("ldi r30, 2", r[0])
    }

    @Test fun testOrRegWithBitmask() {
        val r = compileM8("""
            r30 |= bitmask(7)
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("ori r30, 128", r[0])
    }

    @Test fun testAndRegWithInvertedBitmask() {
        val r = compileM8("""
            r30 &= ~bitmask(1)
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("andi r30, 253", r[0])
    }

    @Test fun testAssignVarToIncReg() {
        val r = compileCode("""
            #define CPU "atmega8"
            var v : byte
            proc main() {
                v = ++r30
            }
        """.trimIndent())
        assertEquals(2, r.size)
        assertEquals("inc r30", r[0])
        assertEquals("sts 96, r30", r[1])
    }

    @Test fun testAssignVarToRegAnd() {
        val r = compileCode("""
            #define CPU "atmega8"
            var v : byte
            proc main() {
                v = r30++
            }
        """.trimIndent())
        assertEquals(2, r.size)
        assertEquals("sts 96, r30", r[0])
        assertEquals("inc r30", r[1])
    }

    @Test fun testSaveregs() {
        val r = compileM8("""
            saveregs(r1, r10) {
                nop
            }
        """.trimIndent())
        assertEquals(5, r.size)
        assertEquals("push r1", r[0])
        assertEquals("push r10", r[1])
        assertEquals("nop", r[2])
        assertEquals("pop r10", r[3])
        assertEquals("pop r1", r[4])
    }

    @Test fun testSaveregsWithAlias() {
        val r = compileM8("""
            use r5 as x
            saveregs(r1, x) {
                nop
            }
        """.trimIndent())
        assertEquals(5, r.size)
        assertEquals("push r1", r[0])
        assertEquals("push r5", r[1])
        assertEquals("nop", r[2])
        assertEquals("pop r5", r[3])
        assertEquals("pop r1", r[4])
    }
}
