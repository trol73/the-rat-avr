package ru.trolsoft.therat.avr.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AvrTemplatesTest {
    @Test
    fun `simple template`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            template my_template(name, reg) {
                proc name() {
                    reg = 0
                    ret
                }
            }
            
            my_template(name: _fun_clr_r24, reg: r24)
            my_template(name: _fun_clr_r1, reg: r1)
        """.trimIndent(), true)

        r shouldHaveSize 7
        r[1] shouldBe  "_fun_clr_r24:"
        r[2] shouldBe  "clr R24"
        r[3] shouldBe  "ret"
        r[4] shouldBe  "_fun_clr_r1:"
        r[5] shouldBe  "clr R1"
        r[6] shouldBe  "ret"
    }
}