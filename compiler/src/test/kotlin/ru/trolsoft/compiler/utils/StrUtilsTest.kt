package ru.trolsoft.compiler.utils

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class StrUtilsTest {

    @Test
    fun `isLineEnd should return true for newline`() {
        isLineEnd('\n') shouldBe true
    }

    @Test
    fun `isLineEnd should return true for carriage return`() {
        isLineEnd('\r') shouldBe true
    }

    @Test
    fun `isLineEnd should return false for regular character`() {
        isLineEnd('a') shouldBe false
        isLineEnd('1') shouldBe false
        isLineEnd(' ') shouldBe false
    }

    @Test
    fun `isWhitespace should return true for space`() {
        isWhitespace(' ') shouldBe true
    }

    @Test
    fun `isWhitespace should return true for tab`() {
        isWhitespace('\t') shouldBe true
    }

    @Test
    fun `isWhitespace should return false for other characters`() {
        isWhitespace('a') shouldBe false
        isWhitespace('\n') shouldBe false
    }

    @Test
    fun `isDigit should return true for digits 0-9`() {
        for (digit in '0'..'9') {
            isDigit(digit) shouldBe true
        }
    }

    @Test
    fun `isDigit should return false for non-digits`() {
        isDigit('a') shouldBe false
        isDigit(' ') shouldBe false
        isDigit('-') shouldBe false
    }

    @Test
    fun `isHexDigit should return true for hex digits 0-9`() {
        for (digit in '0'..'9') {
            isHexDigit(digit) shouldBe true
        }
    }

    @Test
    fun `isHexDigit should return true for hex digits a-f`() {
        for (digit in 'a'..'f') {
            isHexDigit(digit) shouldBe true
        }
    }

    @Test
    fun `isHexDigit should return true for hex digits A-F`() {
        for (digit in 'A'..'F') {
            isHexDigit(digit) shouldBe true
        }
    }

    @Test
    fun `isHexDigit should return false for non-hex characters`() {
        isHexDigit('g') shouldBe false
        isHexDigit('G') shouldBe false
        isHexDigit(' ') shouldBe false
    }

    @Test
    fun `isOctalDigit should return true for digits 0-7`() {
        for (digit in '0'..'7') {
            isOctalDigit(digit) shouldBe true
        }
    }

    @Test
    fun `isOctalDigit should return false for 8 and 9`() {
        isOctalDigit('8') shouldBe false
        isOctalDigit('9') shouldBe false
    }

    @Test
    fun `isLetter should return true for lowercase letters`() {
        for (letter in 'a'..'z') {
            isLetter(letter) shouldBe true
        }
    }

    @Test
    fun `isLetter should return true for uppercase letters`() {
        for (letter in 'A'..'Z') {
            isLetter(letter) shouldBe true
        }
    }

    @Test
    fun `isLetter should return true for underscore`() {
        isLetter('_') shouldBe true
    }

    @Test
    fun `isLetter should return false for digits`() {
        isLetter('0') shouldBe false
        isLetter('5') shouldBe false
    }

    @Test
    fun `isNumberChar should return true for binary digits with base 2`() {
        isNumberChar('0', 2) shouldBe true
        isNumberChar('1', 2) shouldBe true
        isNumberChar('2', 2) shouldBe false
    }

    @Test
    fun `isNumberChar should return true for octal digits with base 8`() {
        isNumberChar('0', 8) shouldBe true
        isNumberChar('7', 8) shouldBe true
        isNumberChar('8', 8) shouldBe false
        isNumberChar('9', 8) shouldBe false
    }

    @Test
    fun `isNumberChar should return true for hex digits with base 16`() {
        isNumberChar('0', 16) shouldBe true
        isNumberChar('9', 16) shouldBe true
        isNumberChar('a', 16) shouldBe true
        isNumberChar('f', 16) shouldBe true
        isNumberChar('A', 16) shouldBe true
        isNumberChar('F', 16) shouldBe true
        isNumberChar('g', 16) shouldBe false
    }

    @Test
    fun `parseNumber should parse hex number with 0x prefix`() {
        parseNumber("0x10") shouldBe 16
        parseNumber("0xFF") shouldBe 255
        parseNumber("0x1a") shouldBe 26
    }

    @Test
    fun `parseNumber should parse hex number with 0X prefix`() {
        parseNumber("0X10") shouldBe 16
        parseNumber("0XFF") shouldBe 255
    }

    @Test
    fun `parseNumber should parse binary number with 0b prefix`() {
        parseNumber("0b10") shouldBe 2
        parseNumber("0b11111111") shouldBe 255
        parseNumber("0b1010") shouldBe 10
    }

    @Test
    fun `parseNumber should parse binary number with 0B prefix`() {
        parseNumber("0B10") shouldBe 2
        parseNumber("0B1111") shouldBe 15
    }

    @Test
    fun `parseNumber should parse octal number starting with 0`() {
        parseNumber("0") shouldBe 0
        parseNumber("010") shouldBe 8
        parseNumber("077") shouldBe 63
    }

    @Test
    fun `parseNumber should parse decimal number`() {
        parseNumber("123") shouldBe 123
        parseNumber("0") shouldBe 0
        parseNumber("999") shouldBe 999
    }

    @Test
    fun `parseNumber should return null for invalid numbers`() {
        parseNumber("xyz") shouldBe null
        parseNumber("0xG") shouldBe null
        parseNumber("0b2") shouldBe null
        parseNumber("") shouldBe null
    }

    @Test
    fun `isNumber should return true for valid numbers`() {
        isNumber("123") shouldBe true
        isNumber("0x1F") shouldBe true
        isNumber("0b1010") shouldBe true
        isNumber("077") shouldBe true
    }

    @Test
    fun `isNumber should return false for invalid numbers`() {
        isNumber("abc") shouldBe false
        isNumber("") shouldBe false
        isNumber("12.34") shouldBe false
    }

    @Test
    fun `hexByte should convert byte to hex string`() {
        hexByte(0) shouldBe "00"
        hexByte(15) shouldBe "0F"
        hexByte(16) shouldBe "10"
        hexByte(255) shouldBe "FF"
        hexByte(170) shouldBe "AA"
    }

    @Test
    fun `hexByte should handle unsigned values correctly`() {
        // Testing with negative bytes (they should be converted to unsigned)
        hexByte(-1) shouldBe "FF"
        hexByte(-2) shouldBe "FE"
    }

    @Test
    fun `hexWord should convert word to hex string`() {
        hexWord(0) shouldBe "0000"
        hexWord(256) shouldBe "0100" // 0x0100
        hexWord(65535) shouldBe "FFFF"
        hexWord(0x1234) shouldBe "1234"
    }

    @Test
    fun `hexDword should convert dword to hex string`() {
        hexDword(0) shouldBe "00000000"
        hexDword(0x12345678) shouldBe "12345678"
        hexDword(-1) shouldBe "FFFFFFFF"
    }

    @Test
    fun `hexByte caching should work`() {
        // Verify that cached values are returned correctly
        val result1 = hexByte(255)
        val result2 = hexByte(255)
        result1 shouldBe result2
    }

    @Test
    fun `parseNumber should handle large numbers`() {
        parseNumber("2147483647") shouldBe Int.MAX_VALUE
        // 2147483648 causes overflow and wraps to -1 intoIntOrNull
        parseNumber("2147483648") shouldBe null
    }

    @Test
    fun `parseNumber with leading zeros`() {
        parseNumber("007") shouldBe 7
        parseNumber("010") shouldBe 8
    }
}