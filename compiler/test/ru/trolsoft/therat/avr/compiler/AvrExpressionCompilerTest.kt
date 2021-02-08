package ru.trolsoft.therat.avr.compiler

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class AvrExpressionCompilerTest {

    @Test fun testMoveZeroToReg() {
        val res = compile("r1 = 0")
        assertEquals(1, res.size)
        assertEquals("clr r1", res.first())
    }

    @Test fun testMoveRegToReg() {
        val res = compile("r1 = r20")

        assertEquals(1, res.size)
        assertEquals("mov r1, r20", res.first())
    }

    @Test fun testMoveRegRegShlReg() {
        val res = compile("r16 = r18 << 3")

        assertEquals(4, res.size)
        assertEquals("mov r16, r18", res[0])
        assertEquals("lsl r16", res[1])
        assertEquals("lsl r16", res[2])
        assertEquals("lsl r16", res[3])
    }

    @Test fun testComplexBinaryOp() {
        val res = compile("r1 = (r2 << 3) + r3 - r4 - 5 + 2*2")

        assertEquals(7, res.size)
        assertEquals("mov r1, r2", res[0])
        assertEquals("lsl r1", res[1])
        assertEquals("lsl r1", res[2])
        assertEquals("lsl r1", res[3])
        assertEquals("add r1, r3", res[4])
        assertEquals("sub r1, r4", res[5])
        assertEquals("dec r1", res[6])
    }

    @Test fun testMovRegPortAnd() {
        val res = compileM8("""
            r16 = PINC & 3
        """.trimIndent())

        assertEquals(2, res.size)
        assertEquals("in r16, 19", res[0])
        assertEquals("andi r16, 3", res[1])
    }

    @Test fun testMovRegRegPortAnd() {
        val res = compileM8("""
            r0 = r16 = PINC & 3
        """.trimIndent())

        assertEquals(3, res.size)
        assertEquals("in r16, 19", res[0])
        assertEquals("andi r16, 3", res[1])
        assertEquals("mov r0, r16", res[2])
    }

    @Test fun testSregBitSet() {
        val res = compileM8("""
            SREG->C = 1
        """.trimIndent())

        assertEquals(1, res.size)
        assertEquals("bset 0", res[0])
    }

    @Test fun testSregBitClr() {
        val res = compileM8("""
            SREG->Z = 0
        """.trimIndent())

        assertEquals(1, res.size)
        assertEquals("bclr 1", res[0])
    }

    @Test fun testMultiAssignRegs() {
        val res = compileM8("""
            r0 = r16 = ' '
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("ldi r16, 32", res[0])
        assertEquals("mov r0, r16", res[1])
    }

    @Test fun testSetRegBit() {
        val res = compileM8("""
            r23[2] = 1
        """.trimIndent())
        assertEquals(1, res.size)
        assertEquals("sbr r23, 4", res[0])
    }

    @Test fun testClrRegBit() {
        val res = compileM8("""
            r23[4] = 0
        """.trimIndent())
        assertEquals(1, res.size)
        assertEquals("cbr r23, 16", res[0])
    }

    @Test fun testMovePairToPairWithSameReg() {
        val res = compile("r25.r24 = r21.r24")
        assertEquals(1, res.size)
        assertEquals("mov r25, r21", res[0])
    }

    @Test fun testMovePairToPairWithMovw() {
        val res = compile("r25.r24 = r17.r16")
        assertEquals(1, res.size)
        assertEquals("movw r25:r24, r17:r16", res[0])
    }

    @Test fun testAssignRegOneMinusReg() {
        val res = compile("r23 = 1 - r20")

        assertEquals(2, res.size)
        assertEquals("ldi r23, 1", res[0])
        assertEquals("sub r23, r20", res[1])
    }

    @Test fun testAddRegToSelf() {
        val res = compile("r27 += r27")

        assertEquals(1, res.size)
        assertEquals("add r27, r27", res[0])
    }

    @Test fun testDecAliasesPair() {
        val res = compileCode("""
        use R24 as rDelL
        use R25 as rDelH

        proc main() {
            rDelH.rDelL -= 1
        }
        """.trimIndent())
        assertEquals(1, res.size)
        assertEquals("sbiw r25:r24, 1", res[0])
    }

    @Test fun testAssignSelfNegative() {
        val res = compileM8("""
            r27 = -r27
        """.trimIndent())
        assertEquals(1, res.size)
        assertEquals("neg r27", res[0])
    }

    @Test fun testAssignNegative() {
        val res = compileM8("""
            r27 = -r5
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("mov r27, r5", res[0])
        assertEquals("neg r27", res[1])
    }

    @Test fun testAssignPrgAnd() {
        val res = compileM8("""
            r26 = prg[Z++] & r29
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("lpm r26, z+", res[0])
        assertEquals("and r26, r29", res[1])
    }

    @Test fun testAddPairMemAddr() {
        val res = compileCode("""
            #define CPU "atmega8"
            var UART_RxBuf: byte[128]
            proc main() {
                Z += UART_RxBuf
            }
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("subi r30, 160", res[0])
        assertEquals("sbci r31, 255", res[1])
    }

    @Test fun testMovePairFromIoW() {
        val res = compileM8("""
            r24.r25 = OCR1B
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("in r24, 41", res[0])
        assertEquals("in r25, 40", res[1])
    }

    @Test fun testMovePairToIoW() {
        val res = compileM8("""
            OCR1B = r24.r25
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("out 41, r24", res[0])
        assertEquals("out 40, r25", res[1])
    }

    @Test fun testRegPlusEqRegReg() {
        val res = compileM8("""
            r23 += r24 + r26
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("add r23, r24", res[0])
        assertEquals("add r23, r26", res[1])
    }

    @Test fun testRegPlusEqRegRegReg() {
        val res = compileM8("""
            r23 += r24 + r25 + r26
        """.trimIndent())
        assertEquals(3, res.size)
        assertEquals("add r23, r24", res[0])
        assertEquals("add r23, r25", res[1])
        assertEquals("add r23, r26", res[2])
    }

    @Test fun testRegPlusEqRegMinusRegPlusReg() {
        val res = compileM8("""
            r23 += r24 - r25 + r26
        """.trimIndent())
        assertEquals(3, res.size)
        assertEquals("add r23, r24", res[0])
        assertEquals("sub r23, r25", res[1])
        assertEquals("add r23, r26", res[2])
    }

    @Test fun testRegMinusEqRegRegReg() {
        val res = compileM8("""
            r23 -= r24 + r25 + r26
        """.trimIndent())
        assertEquals(3, res.size)
        assertEquals("sub r23, r24", res[0])
        assertEquals("sub r23, r25", res[1])
        assertEquals("sub r23, r26", res[2])
    }

    @Test fun testRegMinusEqRegMinusRegPlusReg() {
        val res = compileM8("""
            r23 -= r24 - r25 + r26
        """.trimIndent())
        assertEquals(3, res.size)
        assertEquals("sub r23, r24", res[0])
        assertEquals("add r23, r25", res[1])
        assertEquals("sub r23, r26", res[2])
    }

    @Test fun testAddConstToPair() {
        val res = compileM8("""
            r24.r25 += 0x271
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("subi r25, 143", res[0])
        assertEquals("sbci r24, 254", res[1])
    }


    @Test fun testAssignPairToWordWithDec() {
        val res = compileCode("""
            #define CPU "atmega8"
            var counter: word
            proc main() {
                r25.r24 = counter - 1
            }
        """.trimIndent())
        assertEquals(3, res.size)
        assertEquals("lds r25, 97", res[0])
        assertEquals("lds r24, 96", res[1])
        assertEquals("sbiw r25:r24, 1", res[2])
    }

    @Test fun testAssignPairToWordMinusVal() {
        val res = compileCode("""
            #define CPU "atmega8"
            var counter: word
            proc main() {
                r25.r24 = counter - 100
            }
        """.trimIndent())
        assertEquals(4, res.size)
        assertEquals("lds r25, 97", res[0])
        assertEquals("lds r24, 96", res[1])
        assertEquals("subi r24, 100", res[2])
        assertEquals("sbci r25, 0", res[3])
    }

    @Test fun testAssignRegToVarPlusReg() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r24 = vx + r23
            }
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("lds r24, 96", res[0])
        assertEquals("add r24, r23", res[1])
    }

    @Test fun testAssignRegToVarPlusRegReg() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r24 = vx + r23 + r25
            }
        """.trimIndent())
        assertEquals(3, res.size)
        assertEquals("lds r24, 96", res[0])
        assertEquals("add r24, r23", res[1])
        assertEquals("add r24, r25", res[2])
    }

    @Test fun testAssignRegToVarMinusReg() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r24 = vx - r23
            }
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("lds r24, 96", res[0])
        assertEquals("sub r24, r23", res[1])
    }

    @Test fun testAssignRegToVarPlusRegMinusReg() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                r24 = vx + r23 - r25
            }
        """.trimIndent())
        assertEquals(3, res.size)
        assertEquals("lds r24, 96", res[0])
        assertEquals("add r24, r23", res[1])
        assertEquals("sub r24, r25", res[2])
    }


    @Test fun testAssignVarToIncrementedReg() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                vx = ++r25
            }
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("inc r25", res[0])
        assertEquals("sts 96, r25", res[1])
    }

    @Test fun testAssignVarToRegAndIncrement() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                vx = r25++
            }
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("sts 96, r25", res[0])
        assertEquals("inc r25", res[1])
    }

    @Test fun testZPlusEqBigConst() {
        val res = compileM8("""
            Z += 0x40
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("subi r30, 192", res[0])   // 0x100-0x40
        assertEquals("sbci r31, 0", res[1])
    }

    @Test fun testZPlusEqSmallConst() {
        val res = compileM8("""
            Z += 0x20
        """.trimIndent())
        assertEquals(1, res.size)
        assertEquals("adiw z, 32", res[0])
    }

    @Test fun testZMinusEqBigConst() {
        val res = compileM8("""
            Z -= 0x40
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("subi r30, 64", res[0])
        assertEquals("sbci r31, 0", res[1])
    }

    @Test fun testZMinusEqSmallConst() {
        val res = compileM8("""
            Z -= 0x20
        """.trimIndent())
        assertEquals(1, res.size)
        assertEquals("sbiw z, 32", res[0])
    }

    @Test fun testRegGroupAssignWithZ() {
        val res = compileM8("""
            r0.r1.r2.r3 = ZH.ZL.r4.r5
        """.trimIndent())
        assertEquals(3, res.size)
        assertEquals("mov r0, r31", res[0])
        assertEquals("mov r1, r30", res[1])
        assertEquals("movw r3:r2, r5:r4", res[2])
        // TODO проверить что нет лишних присвоений
    }

    @Test fun testRegXorAssignReg() {
        val res = compileM8("""
            r24 ^= r25
        """.trimIndent())
        assertEquals(1, res.size)
        assertEquals("eor r24, r25", res[0])
    }

    @Test fun testAssignVarToShrReg() {
        val res = compileCode("""
            #define CPU "atmega8"
            var vx: byte
            proc main() {
                vx = r24 >>= 1
            }
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("lsr r24", res[0])
        assertEquals("sts 96, r24", res[1])
    }

    @Test fun testAssignIoPortToRegisterBit() {
        val res = compileCode("""
            #define CPU "atmega8"
            pin led = D3
            proc main() {
                led->port = r24[1]
            }
        """.trimIndent())
        assertEquals(4, res.size)
        assertEquals("sbrs r24, 1", res[0])
        assertEquals("cbi 18, 3", res[1])
        assertEquals("sbrc r24, 1", res[2])
        assertEquals("sbi 18, 3", res[3])
    }

    @Test fun testAssignIoPortToNotRegisterBit() {
        val res = compileCode("""
            #define CPU "atmega8"
            pin led = D3
            proc main() {
                led->port = !r24[1]
            }
        """.trimIndent())
        assertEquals(4, res.size)
        assertEquals("sbrc r24, 1", res[0])
        assertEquals("cbi 18, 3", res[1])
        assertEquals("sbrs r24, 1", res[2])
        assertEquals("sbi 18, 3", res[3])
    }

//    rRes4.rRes3.rRes2.rRes1 = ZH.ZL.rDiv4.rDiv3
//    ("r5", "=", "-", "r4"), "mov\tr5, r4\nneg\tr5");


//    @Test fun testMultiAssignRegsAlias() {
//        val res = compileM8("""
//            use r16 as rtmp
//            r0 = rtmp = ' '
//        """.trimIndent())
//        assertEquals(2, res.size)
//        assertEquals("ldi r16, 32", res[0])
//        assertEquals("mov r0, r16", res[1])
//    }

    /**
     * r1 = (r2 << 3) + r3 - r4 - 5 + 2*2
     * r1 = (r2 << 3) + r3 - r4 - 1
     * (ASSIGN, r1, (PLUS, (MINUS, (MINUS, (PLUS, (SHL, r2, 3), r3), r4), 5), (MUL, 2, 2)))
     * r1 = r2, r1 <<= 3, r1 += r3, r1 -= r4, r1--
     *
     *
     *
     */
}
