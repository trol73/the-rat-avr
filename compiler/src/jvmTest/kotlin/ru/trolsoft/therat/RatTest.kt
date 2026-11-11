package ru.trolsoft.therat

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.utils.ConsoleReporter
import java.io.File

class RatTest {
    val resourcesDir: String = File("src/jvmTest/resources").absolutePath
    private val testConsole = ConsoleReporter({ }, { })

    @Test
    fun `avr successful compilation`() {
        compileFiles(
            arch = Arch.AVR,
            files = listOf("$resourcesDir/avr/test1.art"),
            gcc = false,
            devPath = "../devices/avr",
            encodingsPath = "",
            defines = emptyList(),
            includePaths = emptyList(),
            console = testConsole
        ) shouldBe true
        val hex = File("$resourcesDir/avr/test1.hex")
        val map = File("$resourcesDir/avr/test1.map")
        hex.delete() shouldBe true
        map.delete() shouldBe true
    }

    @Test
    fun `avr compilation with error`() {
        val errors = mutableListOf<String>()
        compileFiles(
            arch = Arch.AVR,
            files = listOf("$resourcesDir/avr/error.art"),
            gcc = false,
            devPath = "../devices/avr",
            encodingsPath = "",
            defines = emptyList(),
            includePaths = emptyList(),
            console = ConsoleReporter({ println(it) }, { errors.add(it); println(it) }),
        ) shouldBe false
        errors shouldHaveSize 3
        with(errors[0]) {
            shouldStartWith("ERROR: ")
            shouldEndWith("/error.art:4:2: Unexpected expression")
        }
        errors[2] shouldBe "Compilation error"
    }
}