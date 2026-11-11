package ru.trolsoft.therat.avr.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test


class AvrUnaryOpTest {

    // ==================== SUFIX INCREMENT/DECREMENT SINGLE REGISTER ====================

    @Test fun `post-increment register standalone`() {
        val res = compileM8("r24++")
        res shouldHaveSize 1
        res[0] shouldBe "inc r24"
    }

    @Test fun `post-decrement register standalone`() {
        val res = compileM8("r24--")
        res shouldHaveSize 1
        res[0] shouldBe "dec r24"
    }

    @Test fun `post-increment high register`() {
        val res = compileM8("r31++")
        res shouldHaveSize 1
        res[0] shouldBe "inc r31"
    }

    @Test fun `post-decrement high register`() {
        val res = compileM8("r31--")
        res shouldHaveSize 1
        res[0] shouldBe "dec r31"
    }

    @Test fun `post-increment aliased register`() {
        val res = compileM8("""
            use r20 as myReg
            myReg++
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "inc r20"
    }

    @Test fun `post-decrement aliased register`() {
        val res = compileM8("""
            use r21 as myReg
            myReg--
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "dec r21"
    }

    // ==================== SUFIX INCREMENT/DECREMENT REGISTER PAIR ====================

    @Test fun `post-increment register pair r25 r24`() {
        val res = compileM8("r25.r24++")
        res shouldHaveSize 1
        res[0] shouldBe "adiw r25:r24, 1"
    }

    @Test fun `post-decrement register pair r25 r24`() {
        val res = compileM8("r25.r24--")
        res shouldHaveSize 1
        res[0] shouldBe "sbiw r25:r24, 1"
    }

    @Test fun `post-increment aliased pair`() {
        val res = compileM8("""
            use r25.r24 as counter
            counter++
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "adiw r25:r24, 1"
    }

    @Test fun `post-decrement aliased pair`() {
        val res = compileM8("""
            use r25.r24 as counter
            counter--
        """.trimIndent())
        res shouldHaveSize 1
        res[0] shouldBe "sbiw r25:r24, 1"
    }

    @Test fun `post-increment Y pair`() {
        val res = compileM8("Y++")
        res shouldHaveSize 1
        res[0] shouldBe "adiw y, 1"
    }

    @Test fun `post-decrement Z pair`() {
        val res = compileM8("Z--")
        res shouldHaveSize 1
        res[0] shouldBe "sbiw z, 1"
    }

    @Test fun `post-increment Z pair`() {
        val res = compileM8("Z++")
        res shouldHaveSize 1
        res[0] shouldBe "adiw z, 1"
    }

    @Test fun `post-increment X pair`() {
        val res = compileM8("X++")
        res shouldHaveSize 1
        res[0] shouldBe "adiw x, 1"
    }

    @Test fun `post-decrement Y pair`() {
        val res = compileM8("Y--")
        res shouldHaveSize 1
        res[0] shouldBe "sbiw y, 1"
    }

    // ==================== REGISTER PAIR WITH DIFFERENT SIZES ====================

    @Test fun `post-increment three byte group`() {
        val res = compileM8("r26.r25.r24++")
        res shouldHaveSize 3
        res[0] shouldBe "subi r24, 255"
        res[1] shouldBe "sbci r25, 255"
        res[2] shouldBe "sbci r26, 255"
    }

    @Test fun `post-decrement three byte group`() {
        val res = compileM8("r26.r25.r24--")
        res shouldHaveSize 3
        res[0] shouldBe "subi r24, 1"
        res[1] shouldBe "sbci r25, 0"
        res[2] shouldBe "sbci r26, 0"
    }

    // ==================== ERROR CASES ====================

    @Test fun `post-increment low register works`() {
        val res = compileM8("r1++")
        res shouldHaveSize 1
        res[0] shouldBe "inc r1"
    }

    @Test fun `post-decrement low register works`() {
        val res = compileM8("r0--")
        res shouldHaveSize 1
        res[0] shouldBe "dec r0"
    }

    @Test fun `post-increment pair low register works`() {
        val res = compileM8("r1.r0++")
        res shouldHaveSize 2
        res[0] shouldBe "subi r0, 255"
        res[1] shouldBe "sbci r1, 255"
    }

    // ==================== COMBINED WITH MEMORY ACCESS ====================

    @Test fun `post-increment mem with register`() {
        val res = compileM8("mem[X] = r24++")
        res shouldHaveSize 2
        res[0] shouldBe "st x, r24"
        res[1] shouldBe "inc r24"
    }

    @Test fun `post-decrement mem with register`() {
        val res = compileM8("mem[X] = r24--")
        res shouldHaveSize 2
        res[0] shouldBe "st x, r24"
        res[1] shouldBe "dec r24"
    }

    // ==================== COMBINED WITH POINTER POST-INCREMENT ====================

    @Test fun `post-increment pointer register`() {
        val res = compileM8("r24 = mem[X++]")
        res shouldHaveSize 1
        res[0] shouldBe "ld r24, x+"
    }

    @Test fun `post-increment pointer register store`() {
        val res = compileM8("mem[X++] = r24")
        res shouldHaveSize 1
        res[0] shouldBe "st x+, r24"
    }

    @Test fun `post-increment Z pointer lpm`() {
        val res = compileM8("r24 = prg[Z++]")
        res shouldHaveSize 1
        res[0] shouldBe "lpm r24, z+"
    }

    // ==================== IN LOOP CONTEXT ====================

    @Test fun `post-decrement loop register`() {
        val res = compileM8("""
            loop(r24) {
                nop
            }
        """.trimIndent())
        res shouldHaveSize 5
        res[0] shouldBe "main@loop@3:"
        res[1] shouldBe "nop"
        res[2] shouldBe "dec r24"
        res[3] shouldBe "brne main@loop@3"
        res[4] shouldBe "main@loopend@3:"
    }

    // ==================== CHAINED OPERATIONS ====================

    @Test fun `post-increment in expression`() {
        val res = compileM8("r20 = (r19++) | r24")
        res shouldHaveSize 3
        res[0] shouldBe "mov r20, r19"
        res[1] shouldBe "inc r19"
        res[2] shouldBe "or r20, r24"
    }

    @Test fun `post-decrement in expression`() {
        val res = compileM8("r20 = (--r19) | r24")
        res shouldHaveSize 3
        res[0] shouldBe "dec r19"
        res[1] shouldBe "mov r20, r19"
        res[2] shouldBe "or r20, r24"
    }
}
