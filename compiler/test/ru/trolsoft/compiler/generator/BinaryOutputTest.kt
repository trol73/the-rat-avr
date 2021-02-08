package ru.trolsoft.compiler.generator

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.test.*

class BinaryOutputTest {

    @Test fun testSimple() {
        val bo = BinaryOutput(Endian.BIG)
        bo.gotoAddress(0x0000)
        bo.reserve(2)
        bo.reserve(2)
        bo.reserve(2)
        bo.reserve(2)
        bo.gotoAddress(0x0100)
        bo.reserve(2)
        bo.reserve(2)
        bo.reserve(16)

        assertEquals(2, bo.pages.size)
        assertEquals(8, bo.pages[0].size)
        assertEquals(0, bo.pages[0].startAddress)
        assertEquals(20, bo.pages[1].size)
        assertEquals(0x100, bo.pages[1].startAddress)

        bo.allocatePages(0)
        bo.gotoAddress(0x0000)
        bo.addByte(0x12)
        bo.addByte(0x34)
        bo.addWord(0xABCD)
        bo.addDword(0x56789ABC)

        bo.gotoAddress(0x0100)
        for (i in 0..19) {
            bo.addByte(i)
        }

        assertEquals(2, bo.pages.size)

        val p1 = bo.pages[0]
        assertEquals(8, p1.size)
        assertEquals(8, p1.data!!.size)
        assertEquals(0, p1.startAddress)
        assertEquals(0x12, p1[0])
        assertEquals(0x34, p1[1])
        assertEquals(0xAB, p1[2])
        assertEquals(0xCD, p1[3])


        val p2 = bo.pages[1]
        assertEquals(20, p2.size)
        assertEquals(20, p2.data!!.size)
        assertEquals(0x100, p2.startAddress)
        assertEquals(0x00, p2[0])
        assertEquals(0x01, p2[1])
        assertEquals(0x02, p2[2])
        assertEquals(0x03, p2[3])
        assertEquals(19, p2[19])

        val stream32 = ByteArrayOutputStream()
        bo.saveToIntelHex(stream32, 32)
        assertEquals(":080000001234ABCD56789ABC16\n" +
                ":14010000000102030405060708090A0B0C0D0E0F101112132D\n" +
                ":00000001FF\n", stream32.toString())

        val stream16 = ByteArrayOutputStream()
        bo.saveToIntelHex(stream16, 16)
        assertEquals(":080000001234ABCD56789ABC16\n" +
                ":10010000000102030405060708090A0B0C0D0E0F77\n" +
                ":0401100010111213A5\n" +
                ":00000001FF\n", stream16.toString())
    }

    @Test fun testPagesOrder() {
        val bo = BinaryOutput(Endian.LITTLE)
        bo.gotoAddress(100)
        bo.reserve(2)
        bo.gotoAddress(50)
        bo.reserve(4)

        bo.allocatePages()

        bo.gotoAddress(100)
        bo.addWord(0x1234)
        bo.gotoAddress(50)
        bo.addDword(0x12345678)

        assertEquals(2, bo.pages.size)
        val p1 = bo.pages[0]
        val p2 = bo.pages[1]
        assertEquals(50, p1.startAddress)
        assertEquals(100, p2.startAddress)

        assertEquals(0x78, p1[0])
        assertEquals(0x56, p1[1])
        assertEquals(0x34, p1[2])
        assertEquals(0x12, p1[3])

        assertEquals(0x34, p2[0])
        assertEquals(0x12, p2[1])

        val stream = ByteArrayOutputStream()
        bo.saveToIntelHex(stream, 16)
        assertEquals(":0400320078563412B6\n" +
                ":02006400341254\n" +
                ":00000001FF\n", stream.toString())
    }
}