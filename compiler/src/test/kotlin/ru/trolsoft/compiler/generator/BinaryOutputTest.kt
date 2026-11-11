package ru.trolsoft.compiler.generator

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Test

class BinaryOutputTest {

    @Test fun `simple test`() {
        val bo = BinaryOutput(Endian.BIG).apply {
            gotoAddress(0x0000)
            reserve(2)
            reserve(2)
            reserve(2)
            reserve(2)
            gotoAddress(0x0100)
            reserve(2)
            reserve(2)
            reserve(16)
        }
        with(bo) {
            pages shouldHaveSize 2
            with(pages[0]) {
                size shouldBe 8
                startAddress shouldBe 0
            }
            with(pages[1]) {
                size shouldBe 20
                startAddress shouldBe 0x100
            }
        }

        bo.apply {
            allocatePages(0)
            gotoAddress(0x0000)
            addByte(0x12)
            addByte(0x34)
            addWord(0xABCD)
            addDword(0x56789ABC)

            gotoAddress(0x0100)
            for (i in 0..19) {
                addByte(i)
            }
        }

        with (bo) {
            pages shouldHaveSize 2
            with(pages[0]) {
                size shouldBe 8
                data!!.size shouldBe 8
                startAddress shouldBe 0
                this[0] shouldBe 0x12
                this[1] shouldBe 0x34
                this[2] shouldBe 0xAB
                this[3] shouldBe 0xCD
            }
            with(pages[1]) {
                size shouldBe 20
                data!!.size shouldBe 20
                startAddress shouldBe 0x100
                this[0] shouldBe 0x00
                this[1] shouldBe 0x01
                this[2] shouldBe 0x02
                this[3] shouldBe 0x03
                this[19] shouldBe 19
            }
        }

        val stream32 = Buffer()
        bo.saveToIntelHex(stream32, 32)
        stream32.readByteArray().decodeToString() shouldBe ":080000001234ABCD56789ABC16\n" +
                ":14010000000102030405060708090A0B0C0D0E0F101112132D\n" +
                ":00000001FF\n"

        val stream16 = Buffer()
        bo.saveToIntelHex(stream16, 16)
        stream16.readByteArray().decodeToString() shouldBe ":080000001234ABCD56789ABC16\n" +
                ":10010000000102030405060708090A0B0C0D0E0F77\n" +
                ":0401100010111213A5\n" +
                ":00000001FF\n"
    }

    @Test fun `pages order`() {
        val bo = BinaryOutput(Endian.LITTLE).apply {
            gotoAddress(100)
            reserve(2)
            gotoAddress(50)
            reserve(4)

            allocatePages()

            gotoAddress(100)
            addWord(0x1234)
            gotoAddress(50)
            addDword(0x12345678)
        }

        bo.pages shouldHaveSize 2
        val p1 = bo.pages[0]
        val p2 = bo.pages[1]

        p1.startAddress shouldBe 50
        p2.startAddress shouldBe 100

        p1[0] shouldBe 0x78
        p1[1] shouldBe 0x56
        p1[2] shouldBe 0x34
        p1[3] shouldBe 0x12

        p2[0] shouldBe 0x34
        p2[1] shouldBe 0x12

        val stream = Buffer()
        bo.saveToIntelHex(stream, 16)
        stream.readByteArray().decodeToString() shouldBe ":0400320078563412B6\n" +
                ":02006400341254\n" +
                ":00000001FF\n"
    }
}