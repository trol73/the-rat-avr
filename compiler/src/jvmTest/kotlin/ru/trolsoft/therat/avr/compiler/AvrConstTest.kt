package ru.trolsoft.therat.avr.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AvrConstTest {

    @Test
    fun `global const simple`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const MAX_VAL = 10
            
            proc main() {
                r24 = MAX_VAL
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r24, 10"
    }

    @Test
    fun `global const expression`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const A = 5
            const B = 10
            const SUM = A + B
            const PROD = A * B
            
            proc main() {
                r24 = SUM
                r25 = PROD
            }
        """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "ldi r24, 15"
        r[1] shouldBe "ldi r25, 50"
    }

    @Test
    fun `global const hex`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const MASK = 0xFF
            
            proc main() {
                r24 = MASK
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r24, 0xff"
    }

    @Test
    fun `global const binary`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const BITS = 0b10101010
            
            proc main() {
                r24 = BITS
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r24, 0b10101010"
    }

    @Test
    fun `const with bitwise ops`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const PORT_MASK = (1 << 5) | (1 << 3)
            
            proc main() {
                r24 = PORT_MASK
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r24, 40"
    }

    @Test
    fun `const shift`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const VAL = 1 << 4
            
            proc main() {
                r24 = VAL
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r24, 16"
    }

    @Test
    fun `const used in expression`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const BASE = 100
            const OFFSET = 10
            
            proc main() {
                r24 = BASE + OFFSET
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r24, 110"
    }

    @Test
    fun `const in if condition`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const FLAG = 1
            
            proc main() {
                if (r24 == FLAG) r25 = 0
            }
        """.trimIndent()
        )
        r shouldHaveSize 4
        r[0] shouldBe "cpi r24, 1"
        r[1] shouldBe "brne main@if_end@6"
        r[2] shouldBe "clr r25"
        r[3] shouldBe "main@if_end@6:"
    }

    @Test
    fun `const in loop init`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const COUNT = 10
            
            proc main() {
                loop(r24 = COUNT) {
                    inc r24
                }
            }
        """.trimIndent()
        )
        r shouldHaveSize 6
        r[0] shouldBe "ldi r24, 10"
        r[1] shouldBe "main@loop@6:"
        r[2] shouldBe "inc r24"
        r[3] shouldBe "dec r24"
        r[4] shouldBe "brne main@loop@6"
        r[5] shouldBe "main@loopend@6:"
    }

    @Test
    fun `local const shadows global const`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const X = 10
            
            proc main() {
                const X = 20
                r24 = X
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r24, 20"
    }

    @Test
    fun `local const different name`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            const GLOBAL = 100
            
            proc main() {
                const LOCAL = 50
                r24 = GLOBAL
                r25 = LOCAL
            }
        """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "ldi r24, 100"
        r[1] shouldBe "ldi r25, 50"
    }

    @Test
    fun `local const with expression`() {
        val r = compileCode(
            """
            #define CPU "atmega8"
            
            proc main() {
                const A = 5
                const B = A * 3
                r24 = B
            }
        """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r24, 15"
    }
}
