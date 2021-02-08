package ru.trolsoft.therat.avr.compiler

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AvrIfCompilerTest {

    @Test
    fun testIfRegEqZero() {
        val res = compileM8("""
            if (r1 == 0) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("tst r1", res[0])
        assertEquals("breq main", res[1])
    }

    @Test
    fun testIfRegNotEqZero() {
        val res = compileM8("""
            if (r1 != 0) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("tst r1", res[0])
        assertEquals("brne main", res[1])
    }


    @Test
    fun testIfRegGreatReg() {
        val res = compileM8("""
            if (r1 > r10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cp r10, r1", res[0])
        assertEquals("brlo main", res[1])
    }

    @Test
    fun testIfRegGreatEqReg() {
        val res = compileM8("""
            if (r1 >= r10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cp r1, r10", res[0])
        assertEquals("brsh main", res[1])
    }

    @Test
    fun testIfRegLessReg() {
        val res = compileM8("""
            if (r1 < r2) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cp r1, r2", res[0])
        assertEquals("brlo main", res[1])
    }

    @Test
    fun testIfRegLessEqReg() {
        val res = compileM8("""
            if (r1 <= r2) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cp r2, r1", res[0])
        assertEquals("brsh main", res[1])
    }

    @Test
    fun testIfRegEqReg() {
        val res = compileM8("""
            if (r1 == r2) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cp r1, r2", res[0])
        assertEquals("breq main", res[1])
    }

    @Test
    fun testIfRegNotEqReg() {
        val res = compileM8("""
            if (r1 != r2) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpse r1, r2", res[0])
        assertEquals("rjmp main", res[1])
        //testLine("if (r21 != r22) goto lbl", "cpse\tr21, r22\nrjmp\tlbl");
    }

    @Test
    fun testIfRegNotEqRegAsm() {
        val res = compileM8("""
            if (r1 != r2) nop
        """.trimIndent())

        assertEquals(2, res.size)
        assertEquals("cpse r1, r2", res[0])
        assertEquals("nop", res[1])
        //testLine("if (r21 != r22) goto lbl", "cpse\tr21, r22\nrjmp\tlbl");
    }

    @Test
    fun testIfRegGreatVal() {
        val res = compileM8("""
            if (r21 > 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 11", res[0])
        assertEquals("brsh main", res[1])
    }

    @Test
    fun testIfRegGreatEqVal() {
        val res = compileM8("""
            if (r21 >= 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 10", res[0])
        assertEquals("brsh main", res[1])
    }

    @Test
    fun testIfRegLessVal() {
        val res = compileM8("""
            if (r21 < 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 10", res[0])
        assertEquals("brlo main", res[1])
    }

    @Test
    fun testIfRegLessEqVal() {
        val res = compileM8("""
            if (r21 <= 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 11", res[0])
        assertEquals("brlo main", res[1])
    }

    @Test
    fun testIfRegEqVal() {
        val res = compileM8("""
            if (r21 == 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 10", res[0])
        assertEquals("breq main", res[1])
    }

    @Test
    fun testIfRegNotEqVal() {
        val res = compileM8("""
            if (r21 != 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 10", res[0])
        assertEquals("brne main", res[1])
    }

    @Test
    fun testIfSignedRegLessVal() {
        val res = compileM8("""
            if (signed r21 < 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 10", res[0])
        assertEquals("brlt main", res[1])
    }

    @Test
    fun testIfSignedRegLessEqVal() {
        val res = compileM8("""
            if (signed r21 <= 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 11", res[0])
        assertEquals("brlt main", res[1])
    }

    @Test
    fun testIfSignedRegGreatVal() {
        val res = compileM8("""
            if (signed r21 > 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 11", res[0])
        assertEquals("brge main", res[1])
    }

    @Test
    fun testIfSignedRegGreatEqVal() {
        val res = compileM8("""
            if (signed r21 >= 10) goto main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("cpi r21, 10", res[0])
        assertEquals("brge main", res[1])
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

    @Test
    fun testIfSignedRegGreatReg() {
        val res = compileM8("""
            if (signed r1 > r2) goto main
        """.trimIndent())
        assertEquals("cp r2, r1", res[0])
        assertEquals("brlt main", res[1])
    }

    @Test
    fun testIfSignedRegGreatEqReg() {
        val res = compileM8("""
            if (signed r1 >= r2) goto main
        """.trimIndent())
        assertEquals("cp r1, r2", res[0])
        assertEquals("brge main", res[1])
    }


    //testLine("if (r21 != r22) r23 = 14", "cpse\tr21, r22\nldi\tr23, 14");



//    @Test
//    fun testIfSignedRegGreatEqZero() {
//        val res = compileM8("""
//            if (signed r21 >= 0) goto main
//        """.trimIndent())
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





    @Test
    fun testSimpleIfGroupsGoto() {
        val res = compileM8("""
            if (Y < Z) goto lbl
        lbl:
        """.trimIndent())
        assertEquals(4, res.size)
        assertEquals("cp r28, r30", res[0])
        assertEquals("cpc r29, r31", res[1])
        assertEquals("brlo main@lbl", res[2])
        assertEquals("main@lbl:", res[3])
    }

    @Test
    fun testIfBlock() {
        val res = compileM8("""
            if (r17 > 10) {
                nop
                sei
            }
        """.trimIndent())
        assertEquals(5, res.size)
        assertEquals("cpi r17, 11", res[0])
        assertEquals("brlo main@if_end@3", res[1])
        assertEquals("nop", res[2])
        assertEquals("sei", res[3])
        assertEquals("main@if_end@3:", res[4])
    }

    @Test
    fun testIfSregGoto() {
        val res = compileM8("""
            if (SREG->C) goto lbl
        lbl:
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("brbs 0, main@lbl", res[0])
        assertEquals("main@lbl:", res[1])
    }

    @Test
    fun testIfNotSregGoto() {
        val res = compileM8("""
            if (!SREG->Z) goto lbl
        lbl:
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("brbc 1, main@lbl", res[0])
        assertEquals("main@lbl:", res[1])
    }

    @Test
    fun testIfRegBitThenCall() {
        val res = compileM8("""
            if (r0[1]) rcall main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("sbrc r0, 1", res[0])
        assertEquals("rcall main", res[1])
    }

    @Test
    fun testIfNotRegBitThenCall() {
        val res = compileM8("""
            if (!r0[1]) rcall main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("sbrs r0, 1", res[0])
        assertEquals("rcall main", res[1])
    }


    @Test
    fun testIfAndGoto() {
        val res = compileM8("""
            if (r0 == 0 && r1 == 0) goto main
        """.trimIndent())
        assertEquals(5, res.size)
        assertEquals("tst r0", res[0])
        assertEquals("brne main@if@3@end", res[1])
        assertEquals("tst r1", res[2])
        assertEquals("breq main", res[3])
        assertEquals("main@if@3@end:", res[4])
    }

    @Test
    fun testIfSimpleAsm() {
        val res = compileM8("""
            if (r0 == 0) nop
        """.trimIndent())
        assertEquals(4, res.size)
        assertEquals("tst r0", res[0])
        assertEquals("brne main@if_end@3", res[1])
        assertEquals("nop", res[2])
        assertEquals("main@if_end@3:", res[3])
    }

    @Test
    fun testIfRegNotEqRegBlock() {
        val res = compileM8("""
            if (r24 == r1) {
                nop
                sei
            }
        """.trimIndent())
        assertEquals(5, res.size)
        assertEquals("cpse r24, r1", res[0])
        assertEquals("rjmp main@if_end@3", res[1])
        assertEquals("nop", res[2])
        assertEquals("sei", res[3])
        assertEquals("main@if_end@3:", res[4])
    }

    @Test
    fun testIfRegNotEqRegBlockWithElse() {
        val res = compileM8("""
            if (r24 == r1) {
                nop
                sei
            } else {
                cli
                r20 = 0
            }
        """.trimIndent())
        assertEquals(9, res.size)
        assertEquals("cpse r24, r1", res[0])
        assertEquals("rjmp main@if_else@3", res[1])
        assertEquals("nop", res[2])
        assertEquals("sei", res[3])
        assertEquals("rjmp main@if_end@3", res[4])
        assertEquals("main@if_else@3:", res[5])
        assertEquals("cli", res[6])
        assertEquals("clr r20", res[7])
        assertEquals("main@if_end@3:", res[8])
    }

    @Test
    fun testIfElseWithEmptyElseBody() {
        val res = compileM8("""
            if (r24 == 0) {
                nop
            } else {
            }
        """.trimIndent())
        assertEquals(4, res.size)
        assertEquals("tst r24", res[0])
        assertEquals("brne main@if_end@3", res[1])
        assertEquals("nop", res[2])
        assertEquals("main@if_end@3:", res[3])
    }

    @Test
    fun testIfElseWithEmptyIfBody() {
        val res = compileM8("""
            if (r24 == 0) {
            } else {
                nop
            }
        """.trimIndent())
        assertEquals(4, res.size)
        assertEquals("tst r24", res[0])
        assertEquals("breq main@if_end@3", res[1])
        assertEquals("nop", res[2])
        assertEquals("main@if_end@3:", res[3])
    }
/*
 */

//    @Test
//    fun testIfPortBitBlockAsm() {
//        val res = compileM8("""
//            if (PORTD->1 == 0) {
//             nop
//            }
//        """.trimIndent())
//        assertEquals(4, res.size)
//        assertEquals("tst r0", res[0])
//        assertEquals("brne main@if_end@3", res[1])
//        assertEquals("nop", res[2])
//        assertEquals("main@if_end@3:", res[3])
//    }



}