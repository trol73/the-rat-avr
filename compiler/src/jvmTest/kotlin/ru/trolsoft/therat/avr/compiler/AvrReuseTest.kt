package ru.trolsoft.therat.avr.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException
import io.kotest.assertions.throwables.shouldThrow

class AvrReuseTest {

    @Test
    fun `reuse removes alias pointing to same register`() {
        val r = compileM8(
            """
            use r16 as counter
            reuse r16 as result
            result = 1
            """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r16, 1"
    }

    @Test
    fun `reuse makes old alias unavailable`() {
        shouldThrow<CompilerException> {
            compileM8(
                """
                use r16 as counter
                reuse r16 as result
                counter = 1
                """.trimIndent()
            )
        }.message shouldBe "Unknown identifier: counter"
    }

    @Test
    fun `reuse keeps other aliases intact`() {
        val r = compileM8(
            """
            use r16 as counter
            use r17 as second
            reuse r17 as result
            counter = 10
            result = 1
            """.trimIndent()
        )
        r shouldHaveSize 2
        r[0] shouldBe "ldi r16, 10"
        r[1] shouldBe "ldi r17, 1"
    }

    @Test
    fun `reuse with aliased register`() {
        val r = compileM8(
            """
            use r16 as counter
            reuse counter as result
            result = 1
            """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r16, 1"
    }

    @Test
    fun `reuse with aliased register removes old alias`() {
        shouldThrow<CompilerException> {
            compileM8(
                """
                use r16 as counter
                reuse counter as result
                counter = 1
                """.trimIndent()
            )
        }.message shouldBe "Unknown identifier: counter"
    }

    @Test
    fun `reuse with multiple declarations error when name conflicts`() {
        shouldThrow<CompilerException> {
            compileM8(
                """
                use r16 as counter, r17 as second
                reuse r18 as counter, r19 as result
                """.trimIndent()
            )
        }.message shouldBe "No alias found for register r18"
    }

    @Test
    fun `reuse error when register already has same alias`() {
        shouldThrow<CompilerException> {
            compileM8(
                """
                use r16 as counter
                reuse r16 as counter
                """.trimIndent()
            )
        }.message shouldBe "Local alias counter already defined"
    }

    @Test
    fun `reuse cannot remove global alias from procedure scope`() {
        shouldThrow<CompilerException> {
            compileCode(
                """
                #define CPU "atmega8"
                
                use r16 as counter
                
                proc main() {
                    reuse r16 as result
                }
                """.trimIndent()
            )
        }.message shouldBe "No alias found for register r16"
    }

    @Test
    fun `multiple reuse on same register`() {
        val r = compileM8(
            """
            use r16 as counter
            reuse r16 as temp
            reuse r16 as result
            result = 42
            """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "ldi r16, 42"
    }

    @Test
    fun `multiple reuse makes old aliases unavailable`() {
        shouldThrow<CompilerException> {
            compileM8(
                """
                use r16 as counter
                reuse r16 as temp
                reuse r16 as result
                counter = 1
                temp = 2
                result = 3
                """.trimIndent()
            )
        }.message shouldBe "Unknown identifier: counter"
    }

    @Test
    fun `reuse preserves capture flag`() {
        val r = compileM8(
            """
            use r16 as counter!
            reuse r16 as result
            result = PORTC
            """.trimIndent()
        )
        r shouldHaveSize 1
        r[0] shouldBe "in r16, 0x15"
    }

    @Test
    fun `reuse error when no alias for register`() {
        shouldThrow<CompilerException> {
            compileM8(
                """
                use r16 as counter
                reuse r17 as result
                """.trimIndent()
            )
        }.message shouldBe "No alias found for register r17"
    }

    @Test
    fun `reuse error when no alias for aliased register`() {
        shouldThrow<CompilerException> {
            compileM8(
                """
                use r16 as counter
                reuse r17 as counter
                """.trimIndent()
            )
        }.message shouldBe "No alias found for register r17"
    }

    @Test
    fun `reuse error when used outside procedure`() {
        shouldThrow<CompilerException> {
            compileCode(
                """
                #define CPU "atmega8"
                use r16 as counter
                reuse r17 as result
                """.trimIndent()
            )
        }.message shouldBe "reuse is only allowed inside procedures"
    }
}
