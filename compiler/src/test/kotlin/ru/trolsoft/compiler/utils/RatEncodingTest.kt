package ru.trolsoft.compiler.utils

import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import kotlin.test.Test

class RatEncodingTest {
    @Test fun `parse encoding`() {
        val src = """
            20-2F: !"#$%&'()*+,-./
            30-3F:0123456789:;<=>?
            60-6F:ЮАБЦДЕФГХИЙКЛМНО
        """.trimIndent()
        val ec = RatEncodingReader().read(Buffer().apply { write(src.toByteArray()) })
        ec.size() shouldBe 48
        ec.contains('!') shouldBe true
        ec.contains('A') shouldBe false
        ec['1'] shouldBe 0x31
        ec[' '] shouldBe 0x20
        ec['X'] shouldBe null
        ec['Б'] shouldBe 0x62
    }
}