package ru.trolsoft.therat.avr.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

class AvrIfCompilerTest {

    @Test fun `if (reg == 0) goto main`() {
        val r = compileM8("""
            if (r1 == 0) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "tst r1"
        r[1] shouldBe "breq main"
    }

    @Test fun `if (reg != 0)`() {
        val r = compileM8("""
            if (r1 != 0) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "tst r1"
        r[1] shouldBe "brne main"
    }


    @Test fun `if (reg great reg)`() {
        val r = compileM8("""
            if (r1 > r10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cp r10, r1"
        r[1] shouldBe "brlo main"
    }

    @Test fun `if (reg great eq reg)`() {
        val r = compileM8("""
            if (r1 >= r10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cp r1, r10"
        r[1] shouldBe "brsh main"
    }

    @Test fun `if (reg less reg)`() {
        val r = compileM8("""
            if (r1 < r2) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cp r1, r2"
        r[1] shouldBe "brlo main"
    }

    @Test fun `if (reg less eq reg)`() {
        val r = compileM8("""
            if (r1 <= r2) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cp r2, r1"
        r[1] shouldBe "brsh main"
    }

    @Test fun `if (reg == reg) goto`() {
        val r = compileM8("""
            if (r1 == r2) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cp r1, r2"
        r[1] shouldBe "breq main"
    }

    @Test fun `if (reg != reg) goto`() {
        val r = compileM8("""
            if (r1 != r2) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpse r1, r2"
        r[1] shouldBe "rjmp main"
        //testLine("if (r21 != r22) goto lbl", "cpse\tr21, r22\nrjmp\tlbl");
    }

    @Test fun `if (reg != reg) asm`() {
        val r = compileM8("""
            if (r1 != r2) nop
        """.trimIndent())

        r shouldHaveSize 2
        r[0] shouldBe "cpse r1, r2"
        r[1] shouldBe "nop"
        //testLine("if (r21 != r22) goto lbl", "cpse\tr21, r22\nrjmp\tlbl");
    }

    @Test fun `if (reg great val) goto`() {
        val r = compileM8("""
            if (r21 > 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 11"
        r[1] shouldBe "brsh main"
    }

    @Test fun `if (reg great eq val) goto`() {
        val r = compileM8("""
            if (r21 >= 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 10"
        r[1] shouldBe "brsh main"
    }

    @Test fun `if (reg less val) goto`() {
        val r = compileM8("""
            if (r21 < 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 10"
        r[1] shouldBe "brlo main"
    }

    @Test fun `if (reg less eq val) goto`() {
        val r = compileM8("""
            if (r21 <= 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 11"
        r[1] shouldBe "brlo main"
    }

    @Test fun `if (reg == val) goto`() {
        val r = compileM8("""
            if (r21 == 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 10"
        r[1] shouldBe "breq main"
    }

    @Test fun `if (reg != val) goto`() {
        val r = compileM8("""
            if (r21 != 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 10"
        r[1] shouldBe "brne main"
    }

    @Test fun `if (signed reg less val) goto`() {
        val r = compileM8("""
            if (signed r21 < 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 10"
        r[1] shouldBe "brlt main"
    }

    @Test fun `if (signed reg less eq val) goto`() {
        val r = compileM8("""
            if (signed r21 <= 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 11"
        r[1] shouldBe "brlt main"
    }

    @Test fun `if (signed reg great val) goto`() {
        val r = compileM8("""
            if (signed r21 > 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 11"
        r[1] shouldBe "brge main"
    }

    @Test fun `if (signed reg great eq val) goto`() {
        val r = compileM8("""
            if (signed r21 >= 10) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpi r21, 10"
        r[1] shouldBe "brge main"
    }
//
//    @Test
//    fun testIfSignedRegLessReg() {
//        val res = compileM8("""
//            if (signed r1 < r2) goto main
//        """.trimIndent())
//        assertEquals("cp r1, r2", res[0])
//        assertEquals("brlt main", res[1])
//    }
//
//    @Test
//    fun testIfSignedRegLessEqReg() {
//        val res = compileM8("""
//            if (signed r1 <= r2) goto main
//        """.trimIndent())
//        assertEquals("cp r2, r1", res[0])
//        assertEquals("brge main", res[1])
//    }

    @Test fun `if (signed reg great reg) goto`() {
        val r = compileM8("""
            if (signed r1 > r2) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cp r2, r1"
        r[1] shouldBe "brlt main"
    }

    @Test fun `if (signed reg great eq reg) goto`() {
        val r = compileM8("""
            if (signed r1 >= r2) goto main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cp r1, r2"
        r[1] shouldBe "brge main"
    }




//    @Test
//    fun testIfSignedRegGreatEqZero() {
//        val res = compileM8("""
//            if (signed r21 >= 0) goto main
//        """.trimIndent())
//        res.forEach { println(it) }
//        assertEquals(2, res.size)
//        assertEquals("cpi r21, 0", res[0])
//        assertEquals("brge main", res[1])
//    }
//
//    @Test
//    fun testIfSignedRegGreatZero() {
//        val res = compileM8("""
//            if (signed r21 > 0) goto main
//        """.trimIndent())
//        assertEquals(2, res.size)
//        assertEquals("cpi r21, 10", res[0])
//        assertEquals("brge main", res[1])
//    }
//
//    @Test
//    fun testIfSignedRegGreatNegVal() {
//        val res = compileM8("""
//            if (signed r21 > -10) goto main
//        """.trimIndent())
//        assertEquals(2, res.size)
//        assertEquals("cpi r21, 247", res[0])
//        assertEquals("brge main", res[1])
//    }



    @Test fun `simple if group with goto`() {
        val r = compileM8("""
            if (Y < Z) goto lbl
        lbl:
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldBe "cp r28, r30"
        r[1] shouldBe "cpc r29, r31"
        r[2] shouldBe "brlo main@lbl"
        r[3] shouldBe "main@lbl:"
    }

    @Test fun `if with block`() {
        val r = compileM8("""
            if (r17 > 10) {
                nop
                sei
            }
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "cpi r17, 11"
        r[1] shouldBe "brlo main@if_end@3"
        r[2] shouldBe "nop"
        r[3] shouldBe "sei"
        r[4] shouldBe "main@if_end@3:"
    }

    @Test fun `if (SREG C) then goto`() {
        val r = compileM8("""
            if (SREG->C) goto lbl
        lbl:
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "brbs 0, main@lbl"
        r[1] shouldBe "main@lbl:"
    }

    @Test fun `if (!SREG Z) then goto`() {
        val r = compileM8("""
            if (!SREG->Z) goto lbl
        lbl:
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "brbc 1, main@lbl"
        r[1] shouldBe "main@lbl:"
    }

    @Test fun `if (reg bit) then call`() {
        val r = compileM8("""
            if (r0[1]) rcall main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "sbrc r0, 1"
        r[1] shouldBe "rcall main"
    }

    @Test fun `if (!reg bit) then call`() {
        val r = compileM8("""
            if (!r0[1]) rcall main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "sbrs r0, 1"
        r[1] shouldBe "rcall main"
    }


    @Test fun `if with goto`() {
        val r = compileM8("""
            if (r0 == 0 && r1 == 0) goto main
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "tst r0"
        r[1] shouldBe "brne main@if@3@end"
        r[2] shouldBe "tst r1"
        r[3] shouldBe "breq main"
        r[4] shouldBe "main@if@3@end:"
    }

    @Test fun `if with simple asm body`() {
        val r = compileM8("""
            if (r0 == 0) nop
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldBe "tst r0"
        r[1] shouldBe "brne main@if_end@3"
        r[2] shouldBe "nop"
        r[3] shouldBe "main@if_end@3:"
    }

    @Test fun `if (reg != reg) block`() {
        val r = compileM8("""
            if (r24 == r1) {
                nop
                sei
            }
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "rjmp main@if_end@3"
        r[2] shouldBe "nop"
        r[3] shouldBe "sei"
        r[4] shouldBe "main@if_end@3:"
    }

    @Test fun `if (reg != reg) block with else`() {
        val r = compileM8("""
            if (r24 == r1) {
                nop
                sei
            } else {
                cli
                r20 = 0
            }
        """.trimIndent())
        r shouldHaveSize 9
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "rjmp main@if_else@3"
        r[2] shouldBe "nop"
        r[3] shouldBe "sei"
        r[4] shouldBe "rjmp main@if_end@3"
        r[5] shouldBe "main@if_else@3:"
        r[6] shouldBe "cli"
        r[7] shouldBe "clr r20"
        r[8] shouldBe "main@if_end@3:"
    }

    @Test fun `if-else with empty else body`() {
        val r = compileM8("""
            if (r24 == 0) {
                nop
            } else {
            }
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldBe "tst r24"
        r[1] shouldBe "brne main@if_end@3"
        r[2] shouldBe "nop"
        r[3] shouldBe "main@if_end@3:"
    }

    @Test fun `if-else with empty if body`() {
        val r = compileM8("""
            if (r24 == 0) {
            } else {
                nop
            }
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldBe "tst r24"
        r[1] shouldBe "breq main@if_end@3"
        r[2] shouldBe "nop"
        r[3] shouldBe "main@if_end@3:"
    }

    @Test fun `if (reg != reg) reg = num`() {
        val r = compileM8("""
            if (r24 != r1) r25 = 5
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "ldi r25, 5"
    }

    @Test fun `if (reg_alias != reg) reg = num`() {
        val r = compileM8("""
            use r24 as value
            if (value != r1) r25 = 5
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "ldi r25, 5"
    }

    @Test fun `if (reg != reg) reg += num`() {
        val r = compileM8("""
            if (r24 != r1) r25 -= 5
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "subi r25, 5"
    }

    @Test fun `if (reg != reg) reg++`() {
        val r = compileM8("""
            if (r24 != r1) r25++
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "inc r25"
    }

    @Test fun `if (reg != reg) Z++`() {
        val r = compileM8("""
            if (r24 != r1) Z++
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "adiw z, 1"
    }

    @Test fun `if (reg != reg) Z--`() {
        val r = compileM8("""
            if (r24 != r1) Z--
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "sbiw z, 1"
    }

    @Test fun `if (reg != reg) Z = X`() {
        val r = compileM8("""
            if (r24 != r1) Z = X
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "movw r31:r30, r27:r26"
    }

    @Test fun `if (reg != reg) movw pairs`() {
        val r = compileM8("""
            if (r24 != r1) r3.r2 = r5.r4
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "cpse r24, r1"
        r[1] shouldBe "movw r3:r2, r5:r4"
    }

    @Test fun `if (reg != reg) push multiple`() {
        val r = compileM8("""
            if (r24 != r1) push r10 r11
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "cp r24, r1"
        r[1] shouldBe "breq main@if_end@3"
        r[2] shouldBe "push r10"
        r[3] shouldBe "push r11"
        r[4] shouldBe "main@if_end@3:"
    }

    @Test fun `if (reg != reg) movw not pairs`() {
        val r = compileM8("""
            if (r24 != r1) r3.r2 = r5.r5
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "cp r24, r1"
        r[1] shouldBe "breq main@if_end@3"
        r[2] shouldBe "mov r2, r5"
        r[3] shouldBe "mov r3, r5"
        r[4] shouldBe "main@if_end@3:"
    }

    @Test fun `if (++group less group)`() {
        val r = compileM8("""
            use r21.r20.r19.r18 as size
            use r25.r24.r23.r22 as addr
            if (++addr < size) nop
        """.trimIndent())
        r shouldHaveSize 11

        r[0] shouldBe "subi r22, 255"
        r[1] shouldBe "sbci r23, 255"
        r[2] shouldBe "sbci r24, 255"
        r[3] shouldBe "sbci r25, 255"

        r[4] shouldBe "cp r22, r18"
        r[5] shouldBe "cpc r23, r19"
        r[6] shouldBe "cpc r24, r20"
        r[7] shouldBe "cpc r25, r21"

        r[8] shouldStartWith "brsh "
        r[9] shouldBe "nop"
        r[10] shouldEndWith ":"
    }

    @Test fun `if (group less ++group)`() {
        val r = compileM8("""
            use r21.r20.r19.r18 as size
            use r25.r24.r23.r22 as addr
            if (addr < ++size) nop
        """.trimIndent())
        r shouldHaveSize 11

        r[0] shouldBe "subi r18, 255"
        r[1] shouldBe "sbci r19, 255"
        r[2] shouldBe "sbci r20, 255"
        r[3] shouldBe "sbci r21, 255"

        r[4] shouldBe "cp r22, r18"
        r[5] shouldBe "cpc r23, r19"
        r[6] shouldBe "cpc r24, r20"
        r[7] shouldBe "cpc r25, r21"

        r[8] shouldStartWith "brsh "
        r[9] shouldBe "nop"
        r[10] shouldEndWith ":"
    }

    @Test fun `if (--group less ++group)`() {
        val r = compileM8("""
            use r21.r20.r19.r18 as size
            use r25.r24.r23.r22 as addr
            if (--addr < ++size) nop
        """.trimIndent())
        r shouldHaveSize 15

        r[0] shouldBe "subi r22, 1"
        r[1] shouldBe "sbci r23, 0"
        r[2] shouldBe "sbci r24, 0"
        r[3] shouldBe "sbci r25, 0"

        r[4] shouldBe "subi r18, 255"
        r[5] shouldBe "sbci r19, 255"
        r[6] shouldBe "sbci r20, 255"
        r[7] shouldBe "sbci r21, 255"

        r[8] shouldBe "cp r22, r18"
        r[9] shouldBe "cpc r23, r19"
        r[10] shouldBe "cpc r24, r20"
        r[11] shouldBe "cpc r25, r21"

        r[12] shouldStartWith "brsh "
        r[13] shouldBe "nop"
        r[14] shouldEndWith ":"
    }

    @Test fun `if (++group less group) goto`() {
        val r = compileM8("""
            use r21.r20.r19.r18 as size
            use r25.r24.r23.r22 as addr
            if (++addr < size) goto main
        """.trimIndent())
        r shouldHaveSize 9

        r[0] shouldBe "subi r22, 255"
        r[1] shouldBe "sbci r23, 255"
        r[2] shouldBe "sbci r24, 255"
        r[3] shouldBe "sbci r25, 255"

        r[4] shouldBe "cp r22, r18"
        r[5] shouldBe "cpc r23, r19"
        r[6] shouldBe "cpc r24, r20"
        r[7] shouldBe "cpc r25, r21"

        r[8] shouldBe  "brlo main"
    }

    @Test fun `if (group less ++group) goto`() {
        val r = compileM8("""
            use r21.r20.r19.r18 as size
            use r25.r24.r23.r22 as addr
            if (addr < ++size) goto main
        """.trimIndent())
        r shouldHaveSize 9

        r[0] shouldBe "subi r18, 255"
        r[1] shouldBe "sbci r19, 255"
        r[2] shouldBe "sbci r20, 255"
        r[3] shouldBe "sbci r21, 255"

        r[4] shouldBe "cp r22, r18"
        r[5] shouldBe "cpc r23, r19"
        r[6] shouldBe "cpc r24, r20"
        r[7] shouldBe "cpc r25, r21"

        r[8] shouldBe  "brlo main"
    }

    @Test fun `if (--group less ++group) goto`() {
        val r = compileM8("""
            use r21.r20.r19.r18 as size
            use r25.r24.r23.r22 as addr
            if (--addr < ++size) goto main
        """.trimIndent())
        r shouldHaveSize 13

        r[0] shouldBe "subi r22, 1"
        r[1] shouldBe "sbci r23, 0"
        r[2] shouldBe "sbci r24, 0"
        r[3] shouldBe "sbci r25, 0"

        r[4] shouldBe "subi r18, 255"
        r[5] shouldBe "sbci r19, 255"
        r[6] shouldBe "sbci r20, 255"
        r[7] shouldBe "sbci r21, 255"

        r[8] shouldBe "cp r22, r18"
        r[9] shouldBe "cpc r23, r19"
        r[10] shouldBe "cpc r24, r20"
        r[11] shouldBe "cpc r25, r21"

        r[12] shouldBe  "brlo main"
    }

    @Test fun `if (reg bit set) reg++`() {
        val r = compileM8("""
            if (r17[0]) ZL++
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "sbrc r17, 0"
        r[1] shouldBe "inc r30"
    }

    @Test fun `if (!reg bit set) reg++`() {
        val r = compileM8("""
            if (!r17[1]) ZL++
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "sbrs r17, 1"
        r[1] shouldBe "inc r30"
    }

    @Test fun `if (!reg bit set) X++`() {
        val r = compileM8("""
            if (!r17[1]) X++
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "sbrs r17, 1"
        r[1] shouldBe "adiw x, 1"
    }

    @Test fun `if (!reg bit set) Z--`() {
        val r = compileM8("""
            if (!r17[1]) Z--
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "sbrs r17, 1"
        r[1] shouldBe "sbiw z, 1"
    }

    @Test fun `if (!reg bit set) pair1 = pair2`() {
        val r = compileM8("""
            if (!r17[1]) r1.r0 = Z
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "sbrs r17, 1"
        r[1] shouldBe "movw r1:r0, r31:r30"
    }

    @Test fun `if (!reg bit set) group = pair`() {
        val r = compileM8("""
            if (!r17[1]) r1.r3 = Z
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "sbrc r17, 1"
        r[1] shouldStartWith "rjmp"
        r[2] shouldBe "mov r1, r31"
        r[3] shouldBe "mov r3, r30"
        r[4] shouldEndWith ":"
    }

    @Test fun `if (reg bit) set pin`() {
        val r = compileCode("""
            #define CPU "mega8"
            pin p = C3
            proc myProc() {
                if (r2[0]) p->port = 0
            }
        """.trimIndent())
        r shouldHaveSize 3
        r[1] shouldBe "sbrc r2, 0"
        r[2] shouldBe "cbi 0x15, 3"
    }

    @Test fun `if (reg bit) call with args`() {
        val r = compileCode("""
            #define CPU "mega8"
            
            proc myProc(a: r24) {
                if (r22[7]) {
                    rcall myProc (prg[Z++])
                }        
            }            
        """.trimIndent())
        r shouldHaveSize 6
        r[1] shouldBe "sbrs r22, 7"
        r[2] shouldBe "rjmp myproc@if_end@4"
        r[3] shouldBe "lpm r24, z+"
        r[4] shouldBe "rcall myproc"
    }

    @Test fun `if (reg bit) multiple assign`() {
        val r = compileM8(
            """
            if (r22[7]) {
                r24 = r25 = 1
            }        
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "sbrs r22, 7"
        r[1] shouldBe "rjmp main@if_end@3"
        r[2] shouldBe "ldi r25, 1"
        r[3] shouldBe "mov r24, r25"
        r[4] shouldBe "main@if_end@3:"
    }

    @Test fun `if (reg bit) move bit`() {
        val r = compileM8(
            """
            if (!r22[7]) {
                r24[1] <- r24[2]
            }        
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "sbrc r22, 7"
        r[1] shouldBe "rjmp main@if_end@3"
        r[2] shouldBe "bst r24, 2"
        r[3] shouldBe "bld r24, 1"
        r[4] shouldBe "main@if_end@3:"
    }

    @Test fun `if (reg bit != pin) goto error`() {
        val r = compileCode("""
            #define CPU "mega8"
            pin p = D5
            proc myProc(a: r24) {
                if (p->pin && !r20[1]) goto error
                if (!p->pin && r20[1]) goto error
                nop
            error:
            }            
        """.trimIndent())
        r shouldHaveSize 13
        r[0] shouldBe "myproc:"
        r[1] shouldBe "sbis 0x10, 5"
        r[2] shouldBe "rjmp myproc@if@4@end"
        r[3] shouldBe "sbrs r20, 1"
        r[4] shouldBe "rjmp myproc@error"
        r[5] shouldBe "myproc@if@4@end:"
        r[6] shouldBe "sbic 0x10, 5"
        r[7] shouldBe "rjmp myproc@if@5@end"
        r[8] shouldBe "sbrc r20, 1"
        r[9] shouldBe "rjmp myproc@error"
        r[10] shouldBe "myproc@if@5@end:"
        r[11] shouldBe "nop"
        r[12] shouldBe "myproc@error:"
    }

    @Test fun `if (aliased X != 500)`() {
        val r = compileM8(
            """
            use X as counter
            
            if (counter != 500) {
                counter = 0
            }
        """.trimIndent())

        r shouldHaveSize 7
        val lbl = r.last().removeSuffix(":")
        r[0] shouldBe "cpi r26, 244"
        r[1] shouldBe "breq $lbl"
        r[2] shouldBe "cpi r27, 1"
        r[3] shouldBe "breq $lbl"
        r[4] shouldBe "ldi r27, 0"
        r[5] shouldBe "ldi r26, 0"
        r[6] shouldBe "$lbl:"
    }

    @Test fun `if (aliased X == 500)`() {
        val r = compileM8(
            """
            use X as counter
            
            if (counter == 500) {
                counter = 0
            }
        """.trimIndent())

        r shouldHaveSize 7
        val lblEnd = r.last().removeSuffix(":")
        r[0] shouldBe "cpi r26, 244"
        r[1] shouldBe "brne $lblEnd"
        r[2] shouldBe "cpi r27, 1"
        r[3] shouldBe "brne $lblEnd"
        r[4] shouldBe "ldi r27, 0"
        r[5] shouldBe "ldi r26, 0"
        r[6] shouldBe "$lblEnd:"
    }

    @Test fun `if (aliased X == 500) with else`() {
        val r = compileM8(
            """
            use X as counter
            
            if (counter == 500) {
                counter = 0
            } else {
                counter = 1
            }
        """.trimIndent())

        r shouldHaveSize 11
        val lblElse = r[7].removeSuffix(":")
        val lblEnd = r[10].removeSuffix(":")
        r[0] shouldBe "cpi r26, 244"
        r[1] shouldBe "brne $lblElse"
        r[2] shouldBe "cpi r27, 1"
        r[3] shouldBe "brne $lblElse"
        r[4] shouldBe "ldi r27, 0"
        r[5] shouldBe "ldi r26, 0"
        r[6] shouldBe "rjmp $lblEnd"
        r[7] shouldBe "$lblElse:"
        r[8] shouldBe "ldi r27, 0"
        r[9] shouldBe "ldi r26, 1"
        r[10] shouldBe "$lblEnd:"
    }


    @Test fun `if (r25_r24 != 2) rjmp failure`() {
        val r = compileM8("""
            if (r25.r24 != 2) rjmp failure
            nop
            @failure:
        """.trimIndent())

        r shouldHaveSize 8
        val endIfLbl = r[5].removeSuffix(":")
        r[0] shouldBe "cpi r24, 2"
        r[1] shouldBe "breq $endIfLbl"
        r[2] shouldBe "cpi r25, 0"
        r[3] shouldBe "breq $endIfLbl"
        r[4] shouldBe "rjmp failure"
        r[5] shouldBe "$endIfLbl:"
        r[6] shouldBe "nop"
        r[7] shouldBe "failure:"
    }

    @Test fun `if (r25_r24 != 2) goto failure`() {
        val r = compileM8("""
            if (r25.r24 != 2) goto failure
            nop
            @failure:
        """.trimIndent())

        r shouldHaveSize 6

        r[0] shouldBe "cpi r24, 2"
        r[1] shouldBe "brne failure"
        r[2] shouldBe "cpi r25, 0"
        r[3] shouldBe "brne failure"
        r[4] shouldBe "nop"
        r[5] shouldBe "failure:"
    }

    @Test fun `if (++r25 == 0) goto main`() {
        val r = compileM8("""
            if (++r25 == 0) {
                goto main
            }
        """.trimIndent())

        r shouldHaveSize 2
        r[0] shouldBe "inc r25"
        r[1] shouldBe "brbs 1, main"
    }

    @Test fun `if (++r25 != 0) goto main`() {
        val r = compileM8("""
            if (++r25 != 0) {
                goto main
            }
        """.trimIndent())

        r shouldHaveSize 2
        r[0] shouldBe "inc r25"
        r[1] shouldBe "brbc 1, main"
    }

    @Test fun `if (--r25 == 0) goto main`() {
        val r = compileM8("""
            if (--r25 == 0) {
                goto main
            }
        """.trimIndent())

        r shouldHaveSize 2
        r[0] shouldBe "dec r25"
        r[1] shouldBe "brbs 1, main"
    }

    @Test fun `if (--r25 != 0) goto main`() {
        val r = compileM8("""
            if (--r25 != 0) {
                goto main
            }
        """.trimIndent())

        r shouldHaveSize 2
        r[0] shouldBe "dec r25"
        r[1] shouldBe "brbc 1, main"
    }


    @Test fun `if (++r25_r24 == 0) goto main`() {
        val r = compileM8("""
            if (++r25.r24 == 0) {
                goto main
            }
        """.trimIndent())

        r shouldHaveSize 2
        r[0] shouldBe "adiw r25:r24, 1"
        r[1] shouldBe "brbs 1, main"
    }

    @Test fun `if (++r25_r24 != 0) goto main`() {
        val r = compileM8("""
            if (++r25.r24 != 0) {
                goto main
            }
        """.trimIndent())

        r shouldHaveSize 2
        r[0] shouldBe "adiw r25:r24, 1"
        r[1] shouldBe "brbc 1, main"
    }

    @Test fun `if (--r25_r24 == 0) goto main`() {
        val r = compileM8("""
            if (--r25.r24 == 0) {
                goto main
            }
        """.trimIndent())

        r shouldHaveSize 2
        r[0] shouldBe "sbiw r25:r24, 1"
        r[1] shouldBe "brbs 1, main"
    }

    @Test fun `if (--r25_r24 != 0) goto main`() {
        val r = compileM8("""
            if (--r25.r24 != 0) {
                goto main
            }
        """.trimIndent())

        r shouldHaveSize 2
        r[0] shouldBe "sbiw r25:r24, 1"
        r[1] shouldBe "brbc 1, main"
    }
}