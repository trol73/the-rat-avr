package ru.trolsoft.therat.i8085.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test

class LoopTest {

    @Test
    fun `loop`() {
        val r = compileProc("""
        loop {
            nop
        }
        """.trimIndent())
        r shouldHaveSize 3
        val lbl = r[0].removeSuffix(":")
        r[1] shouldBe "nop"
        r[2] shouldBe "jmp $lbl"
    }

    @Test
    fun `loop (B)`() {
        val r = compileProc("""
        loop (B) {
            nop
        }
        """.trimIndent())
        r shouldHaveSize 5
        val lbl = r[0].removeSuffix(":")
        r[1] shouldBe "nop"
        r[2] shouldBe "dcr B"
        r[3] shouldBe "jnz $lbl"
        r[4].shouldEndWith(":")
    }

    @Test
    fun `loop (B = 5)`() {
        val r = compileProc("""
        loop (B = 5) {
            nop
        }
        """.trimIndent())
        r shouldHaveSize 6
        r[0] shouldBe "mvi B, 5"
        val lbl = r[1].removeSuffix(":")
        r[2] shouldBe "nop"
        r[3] shouldBe "dcr B"
        r[4] shouldBe "jnz $lbl"
        r[5].shouldEndWith(":")
    }

}