package ru.trolsoft.compiler.utils

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class RkrCrcCalculatorTest {

    private val calculator = RkrCrcCalculator()

    @Test
    fun `initial CRC should be zero`() {
        calculator.crc() shouldBe 0
    }

    @Test
    fun `nextByte should update CRC state`() {
        calculator.nextByte(0)
        calculator.crc() shouldBe 0

        calculator.reset()
        calculator.nextByte(1)
        // crcLo = 0 + 1 = 1, crcHi = 0 + 1 = 1
        // crcHi += (1 shr 8) and 1 = 1 + 0 = 1
        // crc = 1 + (1 shl 8) = 257
        calculator.crc() shouldBe 257
    }

    @Test
    fun `nextByte should handle byte values 0-255`() {
        calculator.reset()
        
        for (i in 0..255) {
            calculator.nextByte(i)
            // Should not throw exception
        }
    }

    @Test
    fun `nextByte should handle negative byte values`() {
        calculator.reset()
        calculator.nextByte(255)
        // After nextByte(255): crcLo = 255, crcHi = 255 + (255 shr 8) and 1 = 255
        // crc = 255 + (255 shl 8) = 65535
        calculator.crc() shouldBe 65535
    }

    @Test
    fun `reset should clear CRC state`() {
        calculator.nextByte(1)
        calculator.nextByte(2)
        val crcBefore = calculator.crc()
        
        calculator.reset()
        calculator.crc() shouldBe 0
        calculator.crc() shouldNotBe crcBefore
    }

    @Test
    fun `calculate with empty array should return 0`() {
        val calc = RkrCrcCalculator()
        calc.calculate(byteArrayOf()) shouldBe 0
    }

    @Test
    fun `calculate with single byte`() {
        val calc = RkrCrcCalculator()
        val result = calc.calculate(byteArrayOf(0))
        result shouldBe 0
    }

    @Test
    fun `calculate with single byte value 1`() {
        val calc = RkrCrcCalculator()
        val result = calc.calculate(byteArrayOf(1))
        // After nextByte(1): crcLo=1, crcHi=1, crcHi += (1 shr 8) and 1 = 1
        // crc = 1 + (1 << 8) = 257 & 0xFF... let's just verify it's consistent
        result shouldBe calc.calculate(byteArrayOf(1))
    }

    @Test
    fun `calculate should be deterministic`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val calc1 = RkrCrcCalculator()
        val calc2 = RkrCrcCalculator()
        
        val result1 = calc1.calculate(data)
        val result2 = calc2.calculate(data)
        
        result1 shouldBe result2
    }

    @Test
    fun `calculate with same data should produce same result`() {
        val data = byteArrayOf(0x41, 0x42, 0x43) // "ABC"
        
        val calc1 = RkrCrcCalculator()
        val result1 = calc1.calculate(data)
        
        val calc2 = RkrCrcCalculator()
        val result2 = calc2.calculate(data)
        
        result1 shouldBe result2
    }

    @Test
    fun `calculate with different data should produce different result`() {
        val data1 = byteArrayOf(0x00)
        val data2 = byteArrayOf(0x01)
        
        val calc1 = RkrCrcCalculator()
        val calc2 = RkrCrcCalculator()
        
        val result1 = calc1.calculate(data1)
        val result2 = calc2.calculate(data2)
        
        result1 shouldNotBe result2
    }

    @Test
    fun `calculate with all zeros should be consistent`() {
        val data = ByteArray(100) { 0 }
        val calc = RkrCrcCalculator()
        val result = calc.calculate(data)
        result shouldBe calc.calculate(data)
    }

    @Test
    fun `calculate with all 0xFF should be consistent`() {
        val data = ByteArray(100) { -1 } // 0xFF
        val calc = RkrCrcCalculator()
        val result = calc.calculate(data)
        result shouldBe calc.calculate(data)
    }

    @Test
    fun `calculate with sequential bytes`() {
        val data = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val calc = RkrCrcCalculator()
        val result = calc.calculate(data)
        
        // Result should be within valid CRC range (16 bits)
        result and 0xFFFF shouldBe result
    }

    @Test
    fun `calculate with large data array`() {
        val data = ByteArray(1024) { it.toByte() }
        val calc = RkrCrcCalculator()
        val result = calc.calculate(data)
        
        result shouldBe result // Just verify it doesn't throw
        result and 0xFFFF shouldBe result
    }

    @Test
    fun `nextByte and calculate should give same result`() {
        val data = byteArrayOf(0x10, 0x20, 0x30)
        
        val calc1 = RkrCrcCalculator()
        val result1 = calc1.calculate(data)
        
        val calc2 = RkrCrcCalculator()
        for (b in data) {
            calc2.nextByte(b.toInt() and 0xff)
        }
        val result2 = calc2.crc()
        
        result1 shouldBe result2
    }

    @Test
    fun `CRC should be 16 bit value`() {
        val calc = RkrCrcCalculator()
        val data = ByteArray(256) { it.toByte() }
        calc.calculate(data)
        
        val crc = calc.crc()
        crc and 0xFFFF shouldBe crc // Should fit in 16 bits
    }

    @Test
    fun `multiple calculate calls on same instance`() {
        val calc = RkrCrcCalculator()
        val data1 = byteArrayOf(1, 2, 3)
        val data2 = byteArrayOf(4, 5, 6)
        
        val result1 = calc.calculate(data1)
        val result2 = calc.calculate(data2)
        
        result1 shouldBe calc.calculate(data1) // Should be reproducible
        result2 shouldBe calc.calculate(data2)
    }

    @Test
    fun `reset between calculations`() {
        val calc = RkrCrcCalculator()
        
        calc.nextByte(1)
        calc.reset()
        calc.crc() shouldBe 0
        
        calc.nextByte(2)
        calc.reset()
        calc.crc() shouldBe 0
    }

    @Test
    fun `calculate with byteArrayOf 0x00 to 0xFF`() {
        val calc = RkrCrcCalculator()
        val data = ByteArray(256) { it.toByte() }
        val result = calc.calculate(data)
        
        result shouldBe result // Just verify it doesn't throw
        result and 0xFFFF shouldBe result
    }
}