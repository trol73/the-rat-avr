package ru.trolsoft.therat.i8085.compiler

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
        r shouldBe ":0100000000FF"
    }

    @Test fun `proc with mov A, B`() {
        val r = compileProc("mov A, B")
        r shouldHaveSize 1
        r.first() shouldBe "mov A, B"
    }

    @Test fun `hex proc with mov A, B`() {
        val r = compileHexProc("mov A, B")
        r shouldBe ":010000007887"
    }

    @Test fun `push PSW`() {
        val r = compileHexProc("push PSW")
        r shouldBe ":01000000F50A"
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
        hex shouldBe ":03000000CD000030"
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
        hex shouldBe ":03000000CD03002D"
    }

    @Test fun `call external proc()`() {
        val code = """
            extern proc outChar(ch: C) absolute 0xF806
            
            proc main() {
                call outChar
            }
        """.trimIndent()
        val r = compileCode(code)
        r shouldHaveSize 1
        r.first() shouldBe "call 0xF806"
        val hex = compileHex(code)
        hex shouldBe ":03000000CD06F832"
    }

    @Test fun `cmp mem HL `() {
        val r = compileHexProc("cmp mem[HL]")
        r shouldBe ":01000000BE41"
    }

}