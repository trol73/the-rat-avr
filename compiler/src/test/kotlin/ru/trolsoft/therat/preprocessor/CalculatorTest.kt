package ru.trolsoft.therat.preprocessor

import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.lang.Scanner
import kotlin.test.Test

class CalculatorTest {
    private val testConsole = ConsoleReporter({ }, { })

    private fun createPreprocessor() =
        Preprocessor(TestFileLoaderStub(), testConsole)

    private fun TestFileLoaderStub(text: String = "") = FileLoader { path ->
        val scanner = Scanner()
        val buf = Buffer().apply { write(text.toByteArray()) }
        TokenStream(scanner.scan(path, buf).toMutableList(), path)
    }

    @Test
    fun `checkExpr should calculate simple number`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("123".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 123
    }

    @Test
    fun `checkExpr should calculate addition`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("1+2".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 3
    }

    @Test
    fun `checkExpr should calculate subtraction`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("10-5".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 5
    }

    @Test
    fun `checkExpr should calculate multiplication`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("3*4".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 12
    }

    @Test
    fun `checkExpr should calculate division`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("20/4".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 5
    }

    @Test
    fun `checkExpr should respect operator precedence`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("1+2*3".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 7 // 1 + (2*3) = 7
    }

    @Test
    fun `checkExpr should handle parentheses`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("(1+2)*3".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 9 // (1+2)*3 = 9
    }

    @Test
    fun `checkExpr should handle bitwise OR`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("0b11110000|0b00001111".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 0b11111111
    }

    @Test
    fun `checkExpr should handle bitwise AND`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("0b11111111&0b11110000".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 0b11110000
    }

    @Test
    fun `checkExpr should handle bitwise XOR`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("0b1111^0b0011".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 0b1100
    }

    @Test
    fun `checkExpr should handle left shift`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("1<<4".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 16
    }

    @Test
    fun `checkExpr should handle right shift`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("32>>2".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 8
    }

    @Test
    fun `checkExpr should handle logical OR`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("1||0".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 1
    }

    @Test
    fun `checkExpr should handle logical AND`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("1&&0".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 0
    }

    @Test
    fun `checkExpr should handle equality comparison true`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("5==5".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 1
    }

    @Test
    fun `checkExpr should handle inequality comparison true`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("5!=3".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 1
    }

    @Test
    fun `checkExpr should handle greater than true`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("10>5".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 1
    }

    @Test
    fun `checkExpr should handle less than true`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("5<10".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 1
    }

    @Test
    fun `checkExpr should handle greater or equal true`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("10>=10".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 1
    }

    @Test
    fun `checkExpr should handle less or equal true`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("5<=5".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 1
    }

    @Test
    fun `checkExpr should handle unary NOT false`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("!1".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 0
    }

    @Test
    fun `checkExpr should handle hex numbers`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("0xFF".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 255
    }

    @Test
    fun `checkExpr should handle binary numbers`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("0b1010".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 10
    }

    @Test
    fun `checkExpr should handle octal numbers`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("077".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 63
    }

    @Test
    fun `checkExpr should handle complex expression`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("((1+2)*3-1)".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 8
    }

    @Test
    fun `checkExpr should handle negative result`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("5-10".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe -5
    }

    @Test
    fun `checkExpr should handle zero result`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("5-5".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 0
    }

    @Test
    fun `checkExpr should handle multiple parentheses`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("((10))".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 10
    }

    @Test
    fun `checkExpr should handle bitwise shifts in complex expression`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("(1<<3)|(1<<7)".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe ((1 shl 3) or (1 shl 7))
    }

    @Test
    fun `checkExpr should handle logical OR with zeros`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("0||0".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 0
    }

    @Test
    fun `checkExpr should handle multiple operations with precedence`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("10/2+3*2".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        // 10/2 = 5, 3*2 = 6, 5+6 = 11
        result shouldBe 11
    }

    @Test
    fun `checkExpr should handle complex nested expression`() {
        val scanner = Scanner()
        val buf = Buffer().apply { write("((1+2)*(3+4))-5".toByteArray()) }
        val tokens = scanner.scan("test", buf).toMutableList()
        buf.close()
        
        val ppc = createPreprocessor()
        val result = checkExpr(ppc, tokens, "test")
        result shouldBe 16
    }
}
