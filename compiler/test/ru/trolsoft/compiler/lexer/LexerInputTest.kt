package ru.trolsoft.compiler.lexer

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.*

class LexerInputTest {


    @Test fun testRead() {
        val stream = ByteArrayInputStream("val x=1".toByteArray())
        val li = LexerInput(stream, "file")
        assertEquals('v', li.read())
        assertEquals("v", li.getText())
        assertEquals('a', li.read())
        assertEquals("va", li.getText())
        assertEquals('l', li.read())
        assertEquals("val", li.getText())
        assertEquals(' ', li.read())
        li.backup(1)
        assertEquals("val", li.getText())
        assertEquals(' ', li.read())
        li.backup(2)
        assertEquals('l', li.read())
    }

    @Test fun testExtract() {
        val stream = ByteArrayInputStream("val x=1".toByteArray())
        val li = LexerInput(stream, "file")
        li.read()
        li.read()
        li.read()
        assertEquals("val", li.extractText())
        li.read()
        assertEquals(" ", li.extractText())
        li.read()
        assertEquals("x", li.extractText())
        li.read()
        assertEquals("=", li.extractText())
        li.read()
        assertEquals("1", li.extractText())
    }

    @Test fun testStartWithNewlineBackup() {
        val stream = ByteArrayInputStream("\nstr\n".toByteArray())
        val li = LexerInput(stream, "file")
        assertEquals('\n', li.read())
        li.backup()
        assertEquals('\n', li.read())
    }

    @Test fun testSourcePosition() {
        val code = """
            reg = const
            while (true) {
                nop
            }
        """.trimIndent().toByteArray()
        val stream = ByteArrayInputStream(code)
        val li = LexerInput(stream, "file")

        assertEquals("file", li.getLocation().fileName)
        assertEquals(1, li.getLocation().line)
        assertEquals(1, li.getLocation().column)

        li.read()
        assertEquals("file", li.getLocation().fileName)
        assertEquals(1, li.getLocation().line)
        assertEquals(1, li.getLocation().column)

        li.read()
        li.read()
        assertEquals(1, li.getLocation().column)

        assertEquals("reg", li.extractText())
        assertEquals(1, li.getLocation().line)
        assertEquals(4, li.getLocation().column)

        li.read()
        assertEquals(" ", li.extractText())
        assertEquals(1, li.getLocation().line)
        assertEquals(5, li.getLocation().column)

        li.read()
        li.read()
        li.backup(1)
        assertEquals("=", li.extractText())
        assertEquals(1, li.getLocation().line)
        assertEquals(6, li.getLocation().column)

        li.read()
        assertEquals(" ", li.extractText())
        assertEquals(1, li.getLocation().line)
        assertEquals(7, li.getLocation().column)

        li.read()
        li.read()
        li.read()
        li.read()
        li.read()
        assertEquals('\n', li.read())
        li.backup(1)
        assertEquals(1, li.getLocation().line)
        assertEquals(7, li.getLocation().column)
        assertEquals("const", li.extractText())

        assertEquals(1, li.getLocation().line)
        assertEquals(12, li.getLocation().column)


        li.read()
        li.extractText()    // endline

        assertEquals(2, li.getLocation().line)
        assertEquals(1, li.getLocation().column)

        while (true) {
            val ch = li.read()
            if (ch == '\n') {
                break
            }
        }
        li.backup(1)
        assertEquals("while (true) {", li.extractText())

        li.read()
        li.extractText()    // endline

        assertEquals(3, li.getLocation().line)
        assertEquals(1, li.getLocation().column)

        while (true) {
            val ch = li.read()
            if (ch != ' ') {
                break
            }
        }
        li.backup()
        assertEquals("    ", li.extractText())
        assertEquals(3, li.getLocation().line)
        assertEquals(5, li.getLocation().column)
    }
}