package ru.trolsoft.therat.i8085.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException

class ErrorsTest {
    @Test fun `call proc with wrong size arguments (byte instead of word)`() {
        shouldThrow<CompilerException> {
            compileCode("""
            proc main() {
                call	myproc(size: B)
            }
            
            proc myproc(size: BC) {
            }
            """.trimIndent())
        }.message shouldBe "Register pair expected but register found: B"
    }

    @Test fun `call proc with wrong size arguments (word instead of byte)`() {
        shouldThrow<CompilerException> {
            compileCode("""
            proc main() {
                call	myproc(size: BC)
            }
            
            proc myproc(size: B) {
            }
            """.trimIndent())
        }.message shouldBe "Register expected but pair found: BC"
    }

}