package ru.trolsoft.compiler.lexer

import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import org.junit.jupiter.api.Test

class LexerInputTest {


    @Test fun `stream reading`() {
        val buf = Buffer().apply { write("val x=1".toByteArray()) }
        with (LexerInput(buf, "file")) {
            read() shouldBe 'v'
            getText() shouldBe "v"
            read() shouldBe 'a'
            getText() shouldBe "va"
            read() shouldBe 'l'
            getText() shouldBe "val"
            read() shouldBe ' '

            backup(1)
            getText() shouldBe "val"
            read() shouldBe ' '

            backup(2)
            read() shouldBe 'l'
        }
    }

    @Test fun `text extract`() {
        val buf = Buffer().apply { write("val x=1".toByteArray()) }
        with (LexerInput(buf, "file")) {
            read()
            read()
            read()
            extractText() shouldBe "val"
            read()
            extractText() shouldBe " "
            read()
            extractText() shouldBe "x"
            read()
            extractText() shouldBe "="
            read()
            extractText() shouldBe "1"
        }
    }

    @Test fun `backup when start with new line`() {
        val buf = Buffer().apply { write("\nstr\n".toByteArray()) }
        with (LexerInput(buf, "file")) {
            read() shouldBe '\n'
            backup()
            read() shouldBe '\n'
        }
    }

    @Test fun `source position test`() {
        val code = """
            reg = const
            while (true) {
                nop
            }
        """.trimIndent()
        val buf = Buffer().apply { write(code.toByteArray()) }
        with (LexerInput(buf, "file")) {
            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 1
                column shouldBe 1
            }

            read()
            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 1
                column shouldBe 1
            }

            read()
            read()
            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 1
                column shouldBe 1
            }

            extractText() shouldBe "reg"
            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 1
                column shouldBe 4
            }

            read()
            extractText() shouldBe " "
            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 1
                column shouldBe 5
            }

            read()
            read()
            backup(1)
            extractText() shouldBe "="
            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 1
                column shouldBe 6
            }

            read()
            extractText() shouldBe " "
            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 1
                column shouldBe 7
            }

            read()
            read()
            read()
            read()
            read()
            read() shouldBe '\n'
            backup(1)
            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 1
                column shouldBe 7
            }
            extractText() shouldBe "const"

            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 1
                column shouldBe 12
            }

            read()
            extractText()    // EOL

            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 2
                column shouldBe 1
            }
            while (true) {
                val ch = read()
                if (ch == '\n') {
                    break
                }
            }
            backup(1)
            extractText() shouldBe "while (true) {"

            read()
            extractText()    // endline

            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 3
                column shouldBe 1
            }

            while (true) {
                val ch = read()
                if (ch != ' ') {
                    break
                }
            }
            backup()
            extractText() shouldBe "    "
            with(getLocation()) {
                fileName shouldBe "file"
                line shouldBe 3
                column shouldBe 5
            }
        }
    }
}