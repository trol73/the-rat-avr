package ru.trolsoft.therat.i8085.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class IfTest {
    @Test
    fun `if (A == const) goto`() {
        val r = compileProc("""
        lbl:
            if (A == 10) goto lbl
        """.trimIndent())
        r shouldHaveSize 3
        r[0] shouldBe "main@lbl:"
        r[1] shouldBe "cpi 10"
        r[2] shouldBe "jz main@lbl"
    }

    @Test
    fun `if (A != const) goto`() {
        val r = compileProc("""
        lbl:
            if (A != 10) goto lbl
        """.trimIndent())
        r shouldHaveSize 3
        r[0] shouldBe "main@lbl:"
        r[1] shouldBe "cpi 10"
        r[2] shouldBe "jnz main@lbl"
    }
}