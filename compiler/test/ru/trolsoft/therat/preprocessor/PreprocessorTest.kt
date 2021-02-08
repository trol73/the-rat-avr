package ru.trolsoft.therat.preprocessor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.trolsoft.therat.avr.AvrScanner
import ru.trolsoft.therat.lang.*
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestFileLoaderStub(private val text: String) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        val stream = ByteArrayInputStream(text.toByteArray())
        return TokenStream(scanner.scan(path, stream).toMutableList(), path)
    }
}

class TestFileLoader1 : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        when (path) {
            "/root/main1" -> {
                val stream = ByteArrayInputStream("""
                #define OUT_PIN 5

                #include "file1.h"
                ; 123
                #include "file2.h"
                ; subdir
                #include "dir/dfile.h"

                #define mf(a1, a2)	if (a1 == a2) return
                mf(x, 123)
                mf(y, 456)

                ; test multiple includes
                #define OUT_PIN		6
                #include "file2.h"
                #define OUT_PIN		7
                #include "file2.h"

                #undef OUT_PIN
                """.trimIndent().toByteArray())
                return TokenStream(scanner.scan(path, stream).toMutableList(), path)
            }
            "/root/file1.h" -> {
                val stream = ByteArrayInputStream("""
                str1
                OUT_PIN
                """.trimIndent().toByteArray())
                return TokenStream(scanner.scan(path, stream).toMutableList(), path)
            }
            "/root/file2.h" -> {
                val stream = ByteArrayInputStream("""
                str21
                str22
                OUT_PIN
                """.trimIndent().toByteArray())
                return TokenStream(scanner.scan(path, stream).toMutableList(), path)
            }
            "/root/dir/dfile.h" -> {
                val stream = ByteArrayInputStream("""
                ; ff file
                #include "../file1.h"

                ; macro OUT_PIN
                if (a > OUT_PIN)
                ; end of ff file
                """.trimIndent().toByteArray())
                return TokenStream(scanner.scan(path, stream).toMutableList(), path)
            }
        }
        throw IOException("file not found: $path")
    }
}


fun nextToken(sream: TokenStream): Token {
    var t = sream.nextToken()
    while (t is NewLineToken) t = sream.nextToken()
    return t
}


class PreprocessorTest {
    @Test fun testDefineAndInclude() {
        val loader = TestFileLoader1()
        val cpp = Preprocessor(loader)
        val res = cpp.process("/root/main1")
        assertEquals("str1", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals(5, (res.nextToken() as NumberToken).value)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals(" 123", (res.nextToken() as LineCommentToken).text)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("str21", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("str22", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals(5, (res.nextToken() as NumberToken).value)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals(" subdir", (res.nextToken() as LineCommentToken).text)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals(" ff file", (res.nextToken() as LineCommentToken).text)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("str1", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals(5, (res.nextToken() as NumberToken).value)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals(" macro OUT_PIN", (res.nextToken() as LineCommentToken).text)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("if", (res.nextToken() as KeywordToken).name)
        assertEquals('(', (res.nextToken() as BracketToken).bracket)
        assertEquals("a", (res.nextToken() as IdentifierToken).name)
        assertEquals(">", (res.nextToken() as OperatorToken).name)
        assertEquals(5, (res.nextToken() as NumberToken).value)
        assertEquals(')', (res.nextToken() as BracketToken).bracket)
        assertTrue(res.nextToken() is NewLineToken)

        assertEquals(" end of ff file", (res.nextToken() as LineCommentToken).text)
        assertTrue(res.nextToken() is NewLineToken)

        assertEquals("if", (res.nextToken() as KeywordToken).name)
        assertEquals('(', (res.nextToken() as BracketToken).bracket)
        assertEquals("x", (res.nextToken() as IdentifierToken).name)
        assertEquals("==", (res.nextToken() as OperatorToken).name)
        assertEquals(123, (res.nextToken() as NumberToken).value)
        assertEquals(')', (res.nextToken() as BracketToken).bracket)
        assertEquals("return", (res.nextToken() as KeywordToken).name)
        assertTrue(res.nextToken() is NewLineToken)

        assertEquals("if", (res.nextToken() as KeywordToken).name)
        assertEquals('(', (res.nextToken() as BracketToken).bracket)
        assertEquals("y", (res.nextToken() as IdentifierToken).name)
        assertEquals("==", (res.nextToken() as OperatorToken).name)
        assertEquals(456, (res.nextToken() as NumberToken).value)
        assertEquals(')', (res.nextToken() as BracketToken).bracket)
        assertEquals("return", (res.nextToken() as KeywordToken).name)
        assertTrue(res.nextToken() is NewLineToken)

        assertEquals(" test multiple includes", (res.nextToken() as LineCommentToken).text)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("str21", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("str22", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals(6, (res.nextToken() as NumberToken).value)
        assertTrue(res.nextToken() is NewLineToken)

        assertEquals("str21", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("str22", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals(7, (res.nextToken() as NumberToken).value)
        assertTrue(res.nextToken() is NewLineToken)
    }

    @Test fun testIncludeBracket() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #include <stdio.h>
            123
        """.trimIndent()))

        val res = cpp.process("test.asm")

        assertEquals("include", (res.nextToken() as PreprocessorToken).name)
    }

    @Test fun testErrorIncludeEOF() {
        val cpp = Preprocessor(TestFileLoaderStub("#include"))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Unexpected EOF", exception1.message)
    }

    @Test fun testErrorIncludeNL() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #include
            abc
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Wrong include syntax", exception1.message)
    }

    @Test fun testErrorIncludeNumber() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #include 123
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Wrong include syntax", exception1.message)
    }

    @Test fun testErrorDefineEOF() {
        val cpp = Preprocessor(TestFileLoaderStub("#define"))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Unexpected EOF", exception1.message)
    }

    @Test fun testErrorDefineNL() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define
            abc
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Expected identifier", exception1.message)
    }

    @Test fun testErrorDefineNumber() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define 123
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Expected identifier", exception1.message)
    }

    @Test fun testUndef() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define ABC 123
            ABC
            #undef ABC
            ABC
        """.trimIndent()))

        val res = cpp.process("test.asm")

        assertEquals(123, (nextToken(res) as NumberToken).value)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("ABC", (res.nextToken() as IdentifierToken).name)
    }

    @Test fun testErrorUndefEOF() {
        val cpp = Preprocessor(TestFileLoaderStub("#undef"))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Unexpected EOF", exception1.message)
    }

    @Test fun testErrorUndefNL() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #undef
            abc
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Expected identifier", exception1.message)
    }

    @Test fun testErrorUndefNumber() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #undef 123
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Expected identifier", exception1.message)
    }

    @Test fun testSimpleIfdefTrue() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define AAA
            #ifdef AAA
            123
            abc
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertEquals(123, (nextToken(res) as NumberToken).value)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("abc", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }

    @Test fun testSimpleIfdefFalse() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifdef AAA
            123
            abc
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }

    @Test fun testSimpleIfndefTrue() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifndef AAA
            123
            abc
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertEquals(123, (nextToken(res) as NumberToken).value)
        assertTrue(res.nextToken() is NewLineToken)
        assertEquals("abc", (res.nextToken() as IdentifierToken).name)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }

    @Test fun testSimpleIfndefFalse() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define AAA
            #ifndef AAA
            123
            abc
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }

    @Test fun testIfdefElseTrue() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifdef AAA
            first
            #else
            second
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertEquals("second", (nextToken(res) as IdentifierToken).name)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }

    @Test fun testIfdefElseFalse() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define AAA=0
            #ifdef AAA
            first
            #else
            second
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertEquals("first", (nextToken(res) as IdentifierToken).name)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }

    @Test fun testNestedIfdef11() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define A
            #define B

            #ifdef A
                #ifdef B
                  AB
                #else
                  AnB
                #endif
            #else
                #ifdef B
                  nAB
                #else
                  nAnB
                #endif
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertEquals("AB", (nextToken(res) as IdentifierToken).name)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }

    @Test fun testNestedIfdef10() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define A

            #ifdef A
                #ifdef B
                  AB
                #else
                  AnB
                #endif
            #else
                #ifdef B
                  nAB
                #else
                  nAnB
                #endif
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertEquals("AnB", (nextToken(res) as IdentifierToken).name)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }

    @Test fun testNestedIfdef01() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define B

            #ifdef A
                #ifdef B
                  AB
                #else
                  AnB
                #endif
            #else
                #ifdef B
                  nAB
                #else
                  nAnB
                #endif
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertEquals("nAB", (nextToken(res) as IdentifierToken).name)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }

    @Test fun testNestedIfdef00() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifdef A
                #ifdef B
                  AB
                #else
                  AnB
                #endif
            #else
                #ifdef B
                  nAB
                #else
                  nAnB
                #endif
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertEquals("nAnB", (nextToken(res) as IdentifierToken).name)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
        assertTrue(res.eof() || res.nextToken() is NewLineToken)
    }


    @Test fun testErrorIfdefEOF() {
        val cpp = Preprocessor(TestFileLoaderStub("#ifdef"))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Unexpected EOF", exception1.message)
    }

    @Test fun testErrorIfdefNumber() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifdef 123
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("Expected identifier", exception1.message)
    }

    @Test fun testErrorElseWithoutIf() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #else
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("#if/#ifdef directive not found", exception1.message)
    }

    @Test fun testErrorEndifWithoutIf() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #endif
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("#if/#ifdef directive not found", exception1.message)
    }

    @Test fun testErrorElseAfterElse() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifdef AAA
            #else
            #else
            #endif
        """.trimIndent()))

        val exception1 = assertThrows<PreprocessorException> { cpp.process("test.asm") }
        assertEquals("#else after #else", exception1.message)
    }

    @Test fun testDefineToDefine() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define F_CPU       16000000
            #define F_CPU_256   F_CPU/256

            F_CPU_256
            #define F_CPU       8000000
            F_CPU_256
        """.trimIndent()))

        val res = cpp.process("test.asm")
        val f16 = res.nextToken()
        val div = res.nextToken()
        val n256 = res.nextToken()

        assertTrue(res.nextToken() is NewLineToken)
        val f8 = res.nextToken()
        val div2 = res.nextToken()
        val n2562 = res.nextToken()


        assertTrue(f16 is NumberToken && f16.value == 16000000)
        assertTrue(div.isOperator("/"))
        assertTrue(n256 is NumberToken && n256.value == 256)

        assertTrue(f8 is NumberToken && f8.value == 8000000)
        assertTrue(div2.isOperator("/"))
        assertTrue(n2562 is NumberToken && n2562.value == 256)
    }

    @Test fun testDefineCalc() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define F_CPU   16000000
            #define cMulti  (65536000/(F_CPU/1000))
            cMulti
        """.trimIndent()))
        val res = cpp.process("test.asm")

        assertTrue(res.nextToken().isBracket('('))
        val t1 = res.nextToken()
        assertTrue(t1 is NumberToken && t1.value == 65536000)
        val t2 = res.nextToken()
        assertTrue(t2 is OperatorToken && t2.name == "/")
        assertTrue(res.nextToken().isBracket('('))
        val t3 = res.nextToken()
        assertTrue(t3 is NumberToken && t3.value == 16000000)
        val t4 = res.nextToken()
        assertTrue(t4 is OperatorToken && t4.name == "/")
        val t5 = res.nextToken()
        assertTrue(t5 is NumberToken && t5.value == 1000)
        assertTrue(res.nextToken().isBracket(')'))
        assertTrue(res.nextToken().isBracket(')'))
    }

    @Test fun testStringCmpIf() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define CPU   "atmega8"
            #if CPU == "atmega8"
                m8
            #elif CPU == "atmega16"
                m16
            #else
                unknown
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")
        val t1 = res.nextToken()
        assertTrue(t1 is IdentifierToken && t1.name == "m8")
        assertTrue(res.nextToken() is NewLineToken)
        assertTrue(res.eof())
    }

    @Test fun testStringCmpElif() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define CPU   "atmega8"
            #if CPU == "atmega16"
                m16
            #elif CPU == "atmega8"
                m8
            #else
                unknown
            #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")
        val t1 = res.nextToken()
        assertTrue(t1 is IdentifierToken && t1.name == "m8")
        assertTrue(res.nextToken() is NewLineToken)
        assertTrue(res.eof())
    }

    @Test fun testMultipleIfElse() {
        val cpp = Preprocessor(TestFileLoaderStub("""
        #define CPU   "atmega8"
        #if CPU == "atmega8"
            #define UART_DATA 		 UDR
        #elif CPU == "atmega328"
            #define UART_DATA		 UDR0
        #elif CPU == "atmega128"
            #if UART_NUMBER == 0
                #define UART_DATA 	 	UDR0
            #elif UART_NUMBER == 1
                #define UART_DATA		 UDR1
            #else
                #error "wrong UART_NUMBER"
            #endif
        #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")
        assertTrue(res.eof())
        assertEquals("UDR", cpp.getStringMacro("UART_DATA"))
    }

    @Test fun testMultipleIfElse2() {
        val cpp = Preprocessor(TestFileLoaderStub("""
        #define CPU   "atmega128"
        #define UART_NUMBER     1
        #if CPU == "atmega8"
            #define UART_DATA 		 UDR
        #elif CPU == "atmega328"
            #define UART_DATA		 UDR0
        #elif CPU == "atmega128"
            #if UART_NUMBER == 0
                #define UART_DATA 	 	UDR0
            #elif UART_NUMBER == 1
                #define UART_DATA		 UDR1
            #else
                #error "wrong UART_NUMBER"
            #endif
        #endif
        """.trimIndent()))
        val res = cpp.process("test.asm")
        assertTrue(res.eof())
        assertEquals("UDR1", cpp.getStringMacro("UART_DATA"))
    }

}
