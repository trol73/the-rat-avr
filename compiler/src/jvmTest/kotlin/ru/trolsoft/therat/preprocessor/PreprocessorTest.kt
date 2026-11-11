package ru.trolsoft.therat.preprocessor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.io.Buffer
import kotlinx.io.IOException
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.avr.AvrScanner
import ru.trolsoft.therat.lang.*

class TestFileLoaderStub(private val text: String) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        val buf = Buffer().apply { write(text.toByteArray()) }
        return TokenStream(scanner.scan(path, buf).toMutableList(), path)
    }
}

class TestRealFileLoader : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        val file = java.io.File(path)
        if (!file.exists()) {
            throw IOException("File not found: $path")
        }
        val content = file.readText()
        val buf = Buffer().apply { write(content.toByteArray()) }
        return TokenStream(scanner.scan(path, buf).toMutableList(), path)
    }
}

class TestFileLoader(
    private val sources: Map<String, String>
) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        val content = sources[path] ?: throw IOException("file not found: $path")
        val buf = Buffer().apply { write(content.toByteArray()) }
        return TokenStream(scanner.scan(path, buf).toMutableList(), path)
    }
}


private fun TokenStream.nextMeaningToken(): Token {
    var t = nextToken()
    while (t is NewLineToken) t = nextToken()
    return t
}


class PreprocessorTest {
    private val testConsole = ConsoleReporter({ }, { })

    @Test fun `define and include`() {
        val files = mapOf(
            "/root/main1" to """
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
                """.trimIndent(),

            "/root/file1.h" to """
                str1
                OUT_PIN
                """.trimIndent(),

            "/root/file2.h" to """
                str21
                str22
                OUT_PIN
                """.trimIndent(),

            "/root/dir/dfile.h" to """
                ; ff file
                #include "../file1.h"

                ; macro OUT_PIN
                if (a > OUT_PIN)
                ; end of ff file
                """.trimIndent()
        )
        val loader = TestFileLoader(files)
        val cpp = Preprocessor(loader, testConsole)

        with (cpp.process("/root/main1")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "str1"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 5
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<LineCommentToken>().text shouldBe " 123"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "str21"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "str22"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 5
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<LineCommentToken>().text shouldBe " subdir"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<LineCommentToken>().text shouldBe " ff file"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "str1"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 5
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<LineCommentToken>().text shouldBe " macro OUT_PIN"
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<KeywordToken>().name shouldBe "if"
            nextToken().shouldBeInstanceOf<BracketToken>().bracket shouldBe '('
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "a"
            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe ">"
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 5
            nextToken().shouldBeInstanceOf<BracketToken>().bracket shouldBe ')'
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<LineCommentToken>().text shouldBe " end of ff file"
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<KeywordToken>().name shouldBe "if"
            nextToken().shouldBeInstanceOf<BracketToken>().bracket shouldBe '('
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "x"
            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe "=="
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
            nextToken().shouldBeInstanceOf<BracketToken>().bracket shouldBe ')'
            nextToken().shouldBeInstanceOf<KeywordToken>().name shouldBe "return"
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<KeywordToken>().name shouldBe "if"
            nextToken().shouldBeInstanceOf<BracketToken>().bracket shouldBe '('
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "y"
            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe "=="
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 456
            nextToken().shouldBeInstanceOf<BracketToken>().bracket shouldBe ')'
            nextToken().shouldBeInstanceOf<KeywordToken>().name shouldBe "return"
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<LineCommentToken>().text shouldBe " test multiple includes"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "str21"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "str22"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 6
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "str21"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "str22"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 7
            nextToken().shouldBeInstanceOf<NewLineToken>()
        }
    }

    @Test fun `include with bracket`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #include <stdio.h>
            123
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<PreprocessorToken>().name shouldBe "include"
        }
    }

    @Test fun `error include EOF`() {
        val cpp = Preprocessor(TestFileLoaderStub("#include"), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Unexpected EOF"
    }

    @Test fun `error include EOL`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #include
            abc
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Wrong include syntax"
    }

    @Test fun `error include number`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #include 123
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Wrong include syntax"
    }

    @Test fun `error define EOF`() {
        val cpp = Preprocessor(TestFileLoaderStub("#define"), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Unexpected EOF"
    }

    @Test fun `error define EOL`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define
            abc
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Identifier expected"
    }

    @Test fun `error define number`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define 123
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Identifier expected"
    }

    @Test fun undef() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define ABC 123
            ABC
            #undef ABC
            ABC
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"
        }
    }

    @Test fun `error undef EOF`() {
        val cpp = Preprocessor(TestFileLoaderStub("#undef"), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Unexpected EOF"
    }

    @Test fun `error undef EOL`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #undef
            abc
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Identifier expected"
    }

    @Test fun `error undef number`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #undef 123
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Identifier expected"
    }

    @Test fun `simple ifdef true`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define AAA
            #ifdef AAA
            123
            abc
            #endif
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextMeaningToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextMeaningToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "abc"
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true

        }
    }

    @Test fun `simple ifdef false`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifdef AAA
            123
            abc
            #endif
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
        }
    }

    @Test fun `simple ifndef true`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifndef AAA
            123
            abc
            #endif
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextMeaningToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextMeaningToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "abc"
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
        }
    }

    @Test fun `simple ifndef false`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define AAA
            #ifndef AAA
            123
            abc
            #endif
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
        }
    }

    @Test fun `ifdef else true`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifdef AAA
            first
            #else
            second
            #endif
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextMeaningToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "second"
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
        }
    }

    @Test fun `ifdef else false`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define AAA=0
            #ifdef AAA
            first
            #else
            second
            #endif
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextMeaningToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "first"
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
        }
    }

    @Test fun `nested ifdef 11`() {
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
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextMeaningToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "AB"
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
        }
    }

    @Test fun `nested ifdef 10`() {
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
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextMeaningToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "AnB"
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
        }
    }

    @Test fun `nested ifdef 01`() {
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
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextMeaningToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "nAB"
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
        }
    }

    @Test fun `nested ifdef 00`() {
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
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextMeaningToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "nAnB"
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
            (eof() || nextToken() is NewLineToken) shouldBe true
        }
    }


    @Test fun `error ifdef EOF`() {
        val cpp = Preprocessor(TestFileLoaderStub("#ifdef"), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Unexpected EOF"
    }

    @Test fun `error ifdef number`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifdef 123
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "Identifier expected"
    }

    @Test fun `error else without if`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #else
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "#if/#ifdef directive not found"
    }
    @Test fun `error endif without if`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #endif
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "#if/#ifdef directive not found"
    }

    @Test fun `error else after else`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifdef AAA
            #else
            #else
            #endif
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldBe "#else after #else"
    }

    @Test fun `define to define`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define F_CPU       16000000
            #define F_CPU_256   F_CPU/256

            F_CPU_256
            #define F_CPU       8000000
            F_CPU_256
        """.trimIndent()), testConsole)

        with(cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 16000000
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("/") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 256

            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 8000000
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("/") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 256
        }
    }

    @Test fun `define calc`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define F_CPU   16000000
            #define cMulti  (65536000/(F_CPU/1000))
            cMulti
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextToken().isBracket('(') shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 65536000

            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe "/"
            nextToken().isBracket('(') shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 16000000
            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe "/"
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 1000
            nextToken().isBracket(')') shouldBe true
            nextToken().isBracket(')') shouldBe true
        }
    }

    @Test fun `if with string compare`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define CPU   "atmega8"
            #if CPU == "atmega8"
                m8
            #elif CPU == "atmega16"
                m16
            #else
                unknown
            #endif
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "m8"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            eof() shouldBe true
        }
    }

    @Test fun `elif with string compare`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define CPU   "atmega8"
            #if CPU == "atmega16"
                m16
            #elif CPU == "atmega8"
                m8
            #else
                unknown
            #endif
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "m8"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            eof() shouldBe true
        }
    }

    @Test fun `multiple if else`() {
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
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            eof() shouldBe true
        }
        cpp.getStringMacro("UART_DATA") shouldBe "UDR"
    }

    @Test fun `multiple if else 2`() {
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
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            eof() shouldBe true
        }
        cpp.getStringMacro("UART_DATA") shouldBe "UDR1"
    }

    @Test fun `cross defines`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define UART_LCD_RX_BUFFER_SIZE	16
            #define UART_LCD_RX_BUFFER_MASK (UART_LCD_RX_BUFFER_SIZE - 1)
        """.trimIndent()), testConsole)
        cpp.process("test.asm")

        cpp.getStringMacro("UART_LCD_RX_BUFFER_SIZE") shouldBe "16"
        cpp.getStringMacro("UART_LCD_RX_BUFFER_MASK") shouldBe "(UART_LCD_RX_BUFFER_SIZE-1)"
    }

    @Test fun `if with cross defines`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define UART_LCD_RX_BUFFER_SIZE	16
            #define UART_LCD_RX_BUFFER_MASK (UART_LCD_RX_BUFFER_SIZE - 1)
            #if UART_LCD_RX_BUFFER_MASK == 15
            included
            #endif
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "included"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            eof() shouldBe true
        }
    }

    @Test fun `C-header ifdef`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifndef _FILE_H_
            #define _FILE_H_
            body
            #endif // _FILE_H_
        """.trimIndent()), testConsole)
        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "body"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            eof() shouldBe true
        }
    }

    @Test fun `if else with C-comment`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #ifndef _FILE_H_
              body
            #else // !_FILE_H_
              body-never
            #endif
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "body"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            eof() shouldBe true
        }
    }

    @Test fun `circular defines`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define KEYBOARD_RX_BUFFER_SIZE  (KEYBOARD_RX_BUFFER_SIZE - 1)
            
            a = KEYBOARD_RX_BUFFER_SIZE
        """.trimIndent()), testConsole)
        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldContain "circular defines"
    }

    @Test fun `nested circular defines`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define MACRO_A  (MACRO_B + 1)
            #define MACRO_B  (MACRO_A + 1)
            
            a = MACRO_A
            b = MACRO_B
        """.trimIndent()), testConsole)
        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldContain "circular defines"
    }

    @Test fun `define without EOL at the end`() {
        val cpp = Preprocessor(TestFileLoaderStub("#define DN    DV"), testConsole)
        cpp.process("test.asm")
        cpp.getStringMacro("DN") shouldBe "DV"
    }

    @Test fun `define without body and EOL at the end`() {
        val cpp = Preprocessor(TestFileLoaderStub("#define DN"), testConsole)
        cpp.process("test.asm")
        cpp.getStringMacro("DN") shouldBe ""
    }

    @Test fun `empty stream`() {
        val files = mapOf(
            "/root/main.c" to ""
        )
        val loader = TestFileLoader(files)
        val cpp = Preprocessor(loader, testConsole)

        with (cpp.process("/root/main.c")) {
            eof() shouldBe true
        }
    }

    @Test fun `counter basic`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            a = __COUNTER__
            b = __COUNTER__
            c = __COUNTER__
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "a"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 0
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "b"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 1
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "c"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 2
        }
    }

    @Test fun `counter multiple per line`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            x = __COUNTER__ + __COUNTER__
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "x"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 0
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("+") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 1
        }
    }

    @Test fun `counter with define`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define REG1 __COUNTER__
            #define REG2 __COUNTER__

            a = REG1
            b = REG2
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "a"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 0
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "b"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 1
        }
    }

    @Test fun `file basic`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            fname = __FILE__
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "fname"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<StringLiteralToken>().str shouldBe "test.asm"
        }
    }

    @Test fun `line basic`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            l1 = __LINE__
            l2 = __LINE__
            l3 = __LINE__
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "l1"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 1
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "l2"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 2
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "l3"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 3
        }
    }

    @Test fun `file in define`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define MY_FILE __FILE__

            fname = MY_FILE
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "fname"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<StringLiteralToken>().str shouldBe "test.asm"
        }
    }

    @Test fun `line in define`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define MY_LINE __LINE__

            l1 = MY_LINE
            l2 = MY_LINE
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "l1"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 3
            nextToken().shouldBeInstanceOf<NewLineToken>()

            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "l2"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 4
        }
    }

    @Test fun `date basic`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            d = __DATE__
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "d"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            val dateToken = nextToken().shouldBeInstanceOf<StringLiteralToken>()
            dateToken.str shouldNotBe ""
            dateToken.str.length shouldBe 11
            dateToken.str[3] shouldBe ' '
            dateToken.str[6] shouldBe ' '
        }
    }

    @Test fun `time basic`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            t = __TIME__
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "t"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            val timeToken = nextToken().shouldBeInstanceOf<StringLiteralToken>()
            timeToken.str shouldNotBe ""
            timeToken.str.length shouldBe 8
            timeToken.str[2] shouldBe ':'
            timeToken.str[5] shouldBe ':'
        }
    }

    @Test fun `date in define`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define BUILD_DATE __DATE__

            d = BUILD_DATE
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "d"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            val dateToken = nextToken().shouldBeInstanceOf<StringLiteralToken>()
            dateToken.str shouldNotBe ""
            dateToken.str.length shouldBe 11
            dateToken.str[3] shouldBe ' '
            dateToken.str[6] shouldBe ' '
        }
    }

    @Test fun `time in define`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define BUILD_TIME __TIME__

            t = BUILD_TIME
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "t"
            nextToken().shouldBeInstanceOf<OperatorToken>().isOperator("=") shouldBe true
            val timeToken = nextToken().shouldBeInstanceOf<StringLiteralToken>()
            timeToken.str shouldNotBe ""
            timeToken.str.length shouldBe 8
            timeToken.str[2] shouldBe ':'
            timeToken.str[5] shouldBe ':'
        }
    }



//    @Test fun `call define with curly brackets`() {
//        val cpp = Preprocessor(TestFileLoaderStub("""
//            #define SET_STACK_POINTER() SPL = tr1 = 0x60
//
//            SET_STACK_POINTER()
//        """.trimIndent()), testConsole)
//        val res = cpp.process("test.asm")
//
//        val t1 = res.nextToken()
//        assertTrue(t1 is IdentifierToken)
//        assertEquals("SPL", t1.name)
//
//        val t2 = res.nextToken()
//        assertTrue(t2 is OperatorToken)
//        assertEquals("=", t2.name)
//
//        val t3 = res.nextToken()
//        assertTrue(t3 is IdentifierToken)
//        assertEquals("tr1", t3.name)
//
//        val t4 = res.nextToken()
//        assertTrue(t4 is OperatorToken)
//        assertEquals("=", t4.name)
//
//        val t5 = res.nextToken()
//        assertTrue(t5 is NumberToken)
//        assertEquals(96, t5.value)
//
//        assertTrue(res.eof())
//    }

    private fun createTempFiles(pairs: List<Pair<String, String>>): String {
        val tempDir = java.nio.file.Files.createTempDirectory("rat-test-").toFile()
        tempDir.deleteOnExit()
        for ((path, content) in pairs) {
            val file = java.io.File(tempDir, path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            file.deleteOnExit()
        }
        return tempDir.absolutePath
    }

    @Test fun `include path basic`() {
        val baseDir = createTempFiles(listOf(
            "main.asm" to """
                #include "common.h"
                main_code
                """.trimIndent(),
            "libs/common.h" to "lib_common"
        ))
        val loader = TestRealFileLoader()
        val cpp = Preprocessor(loader, testConsole).apply {
            addIncludePath("$baseDir/libs")
        }

        with (cpp.process("$baseDir/main.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "lib_common"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "main_code"
        }
    }

    @Test fun `include path multiple directories`() {
        val baseDir = createTempFiles(listOf(
            "main.asm" to """
                #include "defs.h"
                #include "utils.h"
                """.trimIndent(),
            "include/defs.h" to "DEF_VAL",
            "lib/utils.h" to "UTIL_VAL"
        ))
        val loader = TestRealFileLoader()
        val cpp = Preprocessor(loader, testConsole).apply {
            addIncludePath("$baseDir/include")
            addIncludePath("$baseDir/lib")
        }

        with (cpp.process("$baseDir/main.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "DEF_VAL"
            nextToken().shouldBeInstanceOf<NewLineToken>()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "UTIL_VAL"
        }
    }

    @Test fun `include path takes precedence over relative`() {
        val baseDir = createTempFiles(listOf(
            "main.asm" to """
                #include "common.h"
                """.trimIndent(),
            "common.h" to "RELATIVE_COMMON",
            "extra/common.h" to "INCLUDE_PATH_COMMON"
        ))
        val loader = TestRealFileLoader()
        val cpp = Preprocessor(loader, testConsole).apply {
            addIncludePath("$baseDir/extra")
        }

        with (cpp.process("$baseDir/main.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "RELATIVE_COMMON"
        }
    }

    @Test fun `include path file not found error`() {
        val baseDir = createTempFiles(listOf(
            "main.asm" to "#include \"missing.h\""
        ))
        val loader = TestRealFileLoader()
        val cpp = Preprocessor(loader, testConsole).apply {
            addIncludePath("/nonexistent/path")
        }

        shouldThrow<PreprocessorException> {
            cpp.process("$baseDir/main.asm")
        }.message shouldContain "missing.h"
    }

    @Test fun `include path with relative file content`() {
        val baseDir = createTempFiles(listOf(
            "main.asm" to """
                #include "common.h"
                """.trimIndent(),
            "libs/common.h" to """
                #include "shared.h"
                """.trimIndent(),
            "libs/shared.h" to "SHARED_CONTENT"
        ))
        val loader = TestRealFileLoader()
        val cpp = Preprocessor(loader, testConsole).apply {
            addIncludePath("$baseDir/libs")
        }

        with (cpp.process("$baseDir/main.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "SHARED_CONTENT"
        }
    }

    @Test fun `error directive basic`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #error This is an error message
            123
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldContain "#error This is an error message"
    }

    @Test fun `error directive simple`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #error
            123
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldContain "#error"
    }

    @Test fun `error directive in conditional`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #define DEBUG 1
            #ifdef DEBUG
            #error Debug mode not allowed
            #endif
            code
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldContain "#error Debug mode not allowed"
    }

    @Test fun `error directive not in conditional`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #if 0
            #error Debug mode not allowed
            #endif
            code
        """.trimIndent()), testConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "code"
        }
    }

    @Test fun `warning directive basic`() {
        val warnings = mutableListOf<String>()
        val warningConsole = ConsoleReporter({ }, { warnings.add(it) })
        val cpp = Preprocessor(TestFileLoaderStub("""
            #warning This is a warning
            123
        """.trimIndent()), warningConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
        }
        warnings shouldNotBe emptyList<String>()
        warnings[0] shouldContain "#warning This is a warning"
    }

    @Test fun `warning directive simple`() {
        val warnings = mutableListOf<String>()
        val warningConsole = ConsoleReporter({ }, { warnings.add(it) })
        val cpp = Preprocessor(TestFileLoaderStub("""
            #warning
            123
        """.trimIndent()), warningConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
        }
        warnings shouldNotBe emptyList<String>()
        warnings[0] shouldContain "#warning"
    }

    @Test fun `warning directive in conditional false`() {
        val warnings = mutableListOf<String>()
        val warningConsole = ConsoleReporter({ }, { warnings.add(it) })
        val cpp = Preprocessor(TestFileLoaderStub("""
            #if 0
            #warning Debug mode
            #endif
            123
        """.trimIndent()), warningConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
        }
        warnings shouldBe emptyList()
    }

    @Test fun `warning and error together`() {
        val warnings = mutableListOf<String>()
        val warningConsole = ConsoleReporter({ }, { warnings.add(it) })
        val cpp = Preprocessor(TestFileLoaderStub("""
            #warning This is a warning
            #error This is an error
            123
        """.trimIndent()), warningConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldContain "#error This is an error"
        warnings shouldNotBe emptyList<String>()
        warnings[0] shouldContain "#warning This is a warning"
    }

    @Test fun `error with string literal`() {
        val cpp = Preprocessor(TestFileLoaderStub("""
            #error "Missing required configuration"
            123
        """.trimIndent()), testConsole)

        shouldThrow<PreprocessorException> {
            cpp.process("test.asm")
        }.message shouldContain "#error \"Missing required configuration\""
    }

    @Test fun `warning with multiple tokens`() {
        val warnings = mutableListOf<String>()
        val warningConsole = ConsoleReporter({ }, { warnings.add(it) })
        val cpp = Preprocessor(TestFileLoaderStub("""
            #warning Deprecated: use new_func instead
            new_func
        """.trimIndent()), warningConsole)

        with (cpp.process("test.asm")) {
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "new_func"
        }
        warnings shouldNotBe emptyList<String>()
        warnings[0] shouldContain "#warning"
        warnings[0] shouldContain "Deprecated"
        warnings[0] shouldContain "new_func"
    }
}
