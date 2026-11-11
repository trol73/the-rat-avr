package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException


class CompilerErrorHintsTest {
    @Test
    fun `move const directly to port`() {
        shouldThrow<CompilerException> {
            compileM8("""
                PORTD = 123
            """)
        }.message shouldStartWith "Can't write to port directly. You can do this using a temporary register, like so"
    }

    @Test
    fun `move const directly to mem`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                
                var mem: byte
                
                proc main() {
                    mem = 123
                }
                """)
        }.message shouldStartWith "Can't write to memory directly. You can do this using a temporary register, like so:"
    }

    @Test
    fun `add reg to pair`() {
        val msg = shouldThrow<CompilerException> {
            compileM8("r24.r25 += r1")
        }.message
        msg shouldStartWith "You can't just add a register to a group. The easiest way to do this is to use a register that contains 0"
        msg shouldContain "r24.r25 += r0.r1"
    }

    @Test
    fun `add reg to group`() {
        val msg = shouldThrow<CompilerException> {
            compileM8("r24.r25.r26 += r10")
        }.message

        msg shouldStartWith "You can't just add a register to a group. The easiest way to do this is to use a register that contains 0"
        msg shouldContain "r24.r25.r26 += r1.r1.r10"
    }

}