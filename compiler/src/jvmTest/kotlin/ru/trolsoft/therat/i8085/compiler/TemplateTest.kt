package ru.trolsoft.therat.i8085.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TemplateTest {
    @Test
    fun `simple template`() {
        val r = compileCode(
            """
            template my_template(name, reg) {
                proc name() {
                    reg = 0
                    ret
                }
            }
            
            my_template(name: _fun_clr_A, reg: A)
            my_template(name: _fun_clr_B, reg: B)
        """.trimIndent())

        r shouldHaveSize 6
        r[0] shouldBe  "_fun_clr_A:"
        r[1] shouldBe  "xra A"
        r[2] shouldBe  "ret"
        r[3] shouldBe  "_fun_clr_B:"
        r[4] shouldBe  "mvi B, 0"
        r[5] shouldBe  "ret"
    }
}