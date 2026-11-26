package ru.trolsoft.therat.viper.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AsmCompilerTest {
    @Test fun `proc with nop`() {
        val r = compileProc("nop")
        r shouldHaveSize 1
        r.first() shouldBe "nop"
    }

    @Test fun `hex proc with nop`() {
        val r = compileHexProc("nop")
        r shouldBe "00"
    }

    @Test fun `proc with mov R0, R7`() {
        val r = compileProc("mov R0, R7")
        r shouldHaveSize 1
        r.first() shouldBe "mov R0, R7"
    }

    @Test fun `hex proc with mov R0, R7`() {
        val r = compileHexProc("mov R0, R7")
        r shouldBe "07"
    }

    @Test fun `push R1`() {
        val r = compileHexProc("push R1")
        r shouldBe "71"
    }

    @Test fun `call absolute`() {
        val r = compileProc("call 0x1234")
        r shouldHaveSize 1
        r.first() shouldBe "call 0x1234"
    }

    @Test fun `call proc`() {
        val r = compileProc("call main")
        r shouldHaveSize 1
        r.first() shouldBe "call main"

        val hex = compileHexProc("call main")
        println(hex)
        hex shouldBe "CB0000"
    }

    @Test fun `call local label`() {
        val code = """
            call lbl
          lbl:
        """
        val r = compileProc(code)
        r shouldHaveSize 2
        r[0] shouldBe "call main@lbl"
        r[1] shouldBe "main@lbl:"
        val hex = compileHexProc(code)
        hex shouldBe "CB0003"
    }

    @Test fun `cmp R3, R0`() {
        val r = compileHexProc("cmp R3, R0")
        r shouldBe "5F"
    }

}