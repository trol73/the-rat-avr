package ru.trolsoft.therat.lang

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import kotlinx.io.Buffer
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.lexer.LexerException

class ScannerTest {

    @Test fun `numbers sequence`() {
        val buf = Buffer().apply { write("123 0 0x12 012 0b1011_0001".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 5

        (tokens[0] as NumberToken).value shouldBe 123
        (tokens[1] as NumberToken).value shouldBe 0
        (tokens[2] as NumberToken).value shouldBe 0x12
        (tokens[2] as NumberToken).radix shouldBe 16
        (tokens[3] as NumberToken).value shouldBe 10
        (tokens[3] as NumberToken).radix shouldBe 8
        (tokens[4] as NumberToken).value shouldBe 0b10110001
        (tokens[4] as NumberToken).radix shouldBe 2
    }

    @Test fun `two lines`() {
        val buf = Buffer().apply { write("12\n34".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 3

        (tokens[0] as NumberToken).value shouldBe 12
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        tokens[1] should beInstanceOf<NewLineToken>()
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 3

        (tokens[2] as NumberToken).value shouldBe 34
        tokens[2].location.line shouldBe 2
        tokens[2].location.column shouldBe 1
    }

    @Test fun `two lines dec hex`() {
        val buf = Buffer().apply { write("12\n0x34".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 3

        (tokens[0] as NumberToken).value shouldBe 12
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        tokens[1] should beInstanceOf<NewLineToken>()
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 3

        (tokens[2] as NumberToken).value shouldBe 0x34
        (tokens[2] as NumberToken).radix shouldBe 16
        tokens[2].location.line shouldBe 2
        tokens[2].location.column shouldBe 1
    }

    @Test fun `line asm-style comment`() {
        val buf = Buffer().apply { write("123 ;comment line\n345".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 4

        (tokens[0] as NumberToken).value shouldBe 123
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as LineCommentToken).text shouldBe "comment line"
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 5

        tokens[2] should beInstanceOf<NewLineToken>()
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 18
    }

    @Test fun `line c-style comment`() {
        val buf = Buffer().apply { write("123 //comment line\n345".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)
        tokens shouldHaveSize 4

        (tokens[0] as NumberToken).value shouldBe 123
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as LineCommentToken).text shouldBe "comment line"
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 5

        tokens[2] should beInstanceOf<NewLineToken>()
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 19
    }

    @Test fun `simple block comment`() {
        val buf = Buffer().apply { write("/*line1\nline2*/".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 1

        (tokens[0] as BlockCommentToken).text shouldBe "line1\nline2"
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1
    }

    @Test fun `unclosed block comment`() {
        val buf = Buffer().apply { write("/*line1\nline2*".toByteArray()) }
        val scanner = Scanner()
        val exception = shouldThrow<LexerException> { scanner.scan("", buf) }

        "Unclosed comment block" shouldBe exception.message
        exception.location.line shouldBe 1
        exception.location.column shouldBe 1
    }

    @Test fun `operators simple`() {
        val buf = Buffer().apply { write("+ - * / & | ! = ^ < > % << >> || && ++ --".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 18

        (tokens[0] as OperatorToken).name shouldBe "+"
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as OperatorToken).name shouldBe "-"
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 3

        (tokens[2] as OperatorToken).name shouldBe "*"
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 5

        (tokens[3] as OperatorToken).name shouldBe "/"
        tokens[3].location.line shouldBe 1
        tokens[3].location.column shouldBe 7

        (tokens[4] as OperatorToken).name shouldBe "&"
        tokens[4].location.line shouldBe 1
        tokens[4].location.column shouldBe 9

        (tokens[5] as OperatorToken).name shouldBe "|"
        tokens[5].location.line shouldBe 1
        tokens[5].location.column shouldBe 11

        (tokens[6] as OperatorToken).name shouldBe "!"
        tokens[6].location.line shouldBe 1
        tokens[6].location.column shouldBe 13

        (tokens[7] as OperatorToken).name shouldBe "="
        tokens[7].location.line shouldBe 1
        tokens[7].location.column shouldBe 15

        (tokens[8] as OperatorToken).name shouldBe "^"
        tokens[8].location.line shouldBe 1
        tokens[8].location.column shouldBe 17

        (tokens[9] as OperatorToken).name shouldBe "<"
        tokens[9].location.line shouldBe 1
        tokens[9].location.column shouldBe 19

        (tokens[10] as OperatorToken).name shouldBe ">"
        tokens[10].location.line shouldBe 1
        tokens[10].location.column shouldBe 21

        (tokens[11] as OperatorToken).name shouldBe "%"
        tokens[11].location.line shouldBe 1
        tokens[11].location.column shouldBe 23

        (tokens[12] as OperatorToken).name shouldBe "<<"
        tokens[12].location.line shouldBe 1
        tokens[12].location.column shouldBe 25

        (tokens[13] as OperatorToken).name shouldBe ">>"
        tokens[13].location.line shouldBe 1
        tokens[13].location.column shouldBe 28

        (tokens[14] as OperatorToken).name shouldBe "||"
        tokens[14].location.line shouldBe 1
        tokens[14].location.column shouldBe 31

        (tokens[15] as OperatorToken).name shouldBe "&&"
        tokens[15].location.line shouldBe 1
        tokens[15].location.column shouldBe 34

        (tokens[16] as OperatorToken).name shouldBe "++"
        tokens[16].location.line shouldBe 1
        tokens[16].location.column shouldBe 37

        (tokens[17] as OperatorToken).name shouldBe "--"
        tokens[17].location.line shouldBe 1
        tokens[17].location.column shouldBe 40
    }

    @Test fun `operators with =`() {
        val buf = Buffer().apply { write("+= -= *= /= &= |= != == ^= <= >= %= <<= >>=".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 14

        (tokens[0] as OperatorToken).name shouldBe "+="
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as OperatorToken).name shouldBe "-="
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 4

        (tokens[2] as OperatorToken).name shouldBe "*="
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 7

        (tokens[3] as OperatorToken).name shouldBe "/="
        tokens[3].location.line shouldBe 1
        tokens[3].location.column shouldBe 10

        (tokens[4] as OperatorToken).name shouldBe "&="
        tokens[4].location.line shouldBe 1
        tokens[4].location.column shouldBe 13

        (tokens[5] as OperatorToken).name shouldBe "|="
        tokens[5].location.line shouldBe 1
        tokens[5].location.column shouldBe 16

        (tokens[6] as OperatorToken).name shouldBe "!="
        tokens[6].location.line shouldBe 1
        tokens[6].location.column shouldBe 19

        (tokens[7] as OperatorToken).name shouldBe "=="
        tokens[7].location.line shouldBe 1
        tokens[7].location.column shouldBe 22

        (tokens[8] as OperatorToken).name shouldBe "^="
        tokens[8].location.line shouldBe 1
        tokens[8].location.column shouldBe 25

        (tokens[9] as OperatorToken).name shouldBe "<="
        tokens[9].location.line shouldBe 1
        tokens[9].location.column shouldBe 28

        (tokens[10] as OperatorToken).name shouldBe ">="
        tokens[10].location.line shouldBe 1
        tokens[10].location.column shouldBe 31

        (tokens[11] as OperatorToken).name shouldBe "%="
        tokens[11].location.line shouldBe 1
        tokens[11].location.column shouldBe 34

        (tokens[12] as OperatorToken).name shouldBe "<<="
        tokens[12].location.line shouldBe 1
        tokens[12].location.column shouldBe 37

        (tokens[13] as OperatorToken).name shouldBe ">>="
        tokens[13].location.line shouldBe 1
        tokens[13].location.column shouldBe 41
    }

    @Test fun `no space operators`() {
        val buf = Buffer().apply { write("+- */ =| =! &|".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 10

        (tokens[0] as OperatorToken).name shouldBe "+"
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as OperatorToken).name shouldBe "-"
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 2

        (tokens[2] as OperatorToken).name shouldBe "*"
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 4

        (tokens[3] as OperatorToken).name shouldBe "/"
        tokens[3].location.line shouldBe 1
        tokens[3].location.column shouldBe 5

        (tokens[4] as OperatorToken).name shouldBe "="
        tokens[4].location.line shouldBe 1
        tokens[4].location.column shouldBe 7

        (tokens[5] as OperatorToken).name shouldBe "|"
        tokens[5].location.line shouldBe 1
        tokens[5].location.column shouldBe 8

        (tokens[6] as OperatorToken).name shouldBe "="
        tokens[6].location.line shouldBe 1
        tokens[6].location.column shouldBe 10

        (tokens[7] as OperatorToken).name shouldBe "!"
        tokens[7].location.line shouldBe 1
        tokens[7].location.column shouldBe 11

        (tokens[8] as OperatorToken).name shouldBe "&"
        tokens[8].location.line shouldBe 1
        tokens[8].location.column shouldBe 13

        (tokens[9] as OperatorToken).name shouldBe "|"
        tokens[9].location.line shouldBe 1
        tokens[9].location.column shouldBe 14
    }

    @Test fun `char literal`() {
        val buf = Buffer().apply { write("'1' 'z' '\\'' '\\n' '\\\\'".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 5

        (tokens[0] as CharLiteralToken).char shouldBe '1'
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as CharLiteralToken).char shouldBe 'z'
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 5

        (tokens[2] as CharLiteralToken).char shouldBe '\''
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 9

        (tokens[3] as CharLiteralToken).char shouldBe '\n'
        tokens[3].location.line shouldBe 1
        tokens[3].location.column shouldBe 14

        (tokens[4] as CharLiteralToken).char shouldBe '\\'
        tokens[4].location.line shouldBe 1
        tokens[4].location.column shouldBe 19
    }

    @Test fun `char literal with error`() {
        val scanner = Scanner()

        val buf1 = Buffer().apply { write("'1".toByteArray()) }
        shouldThrow<LexerException> {
            scanner.scan("", buf1)
        }.message shouldBe "Incorrect character literal"

        val buf2 = Buffer().apply { write("''".toByteArray()) }
        shouldThrow<LexerException> {
            scanner.scan("", buf2)
        }.message shouldBe "Empty character literal"

        val buf3 = Buffer().apply { write("'\\f".toByteArray()) }
        shouldThrow<LexerException> {
            scanner.scan("", buf3)
        }.message shouldBe "Incorrect character literal"

        val buf4 = Buffer().apply { write("'\n'".toByteArray()) }
        shouldThrow<LexerException> {
            scanner.scan("", buf4)
        }.message shouldBe "Incorrect character literal"

        val buf5 = Buffer().apply { write("'f\n".toByteArray()) }
        shouldThrow<LexerException> {
            scanner.scan("", buf5)
        }.message shouldBe "Incorrect character literal"

        val buf6 = Buffer().apply { write("'".toByteArray()) }
        shouldThrow<LexerException> {
            scanner.scan("", buf6)
        }.message shouldBe "Incorrect character literal"

        val buf7 = Buffer().apply { write("' ".toByteArray()) }
        shouldThrow<LexerException> {
            scanner.scan("", buf7)
        }.message shouldBe "Incorrect character literal"
    }

    @Test fun `string literal`() {
        val buf = Buffer().apply { write("\"abc\" \"\"".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 2

        (tokens[0] as StringLiteralToken).str shouldBe "abc"
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as StringLiteralToken).str shouldBe ""
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 7
    }

    @Test fun `escaped string literal`() {
        val buf = Buffer().apply { write(""" "\n" "\\" "\x0a"
            """.toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 4

        (tokens[0] as StringLiteralToken).str shouldBe "\n"
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 2

        (tokens[1] as StringLiteralToken).str shouldBe "\\"
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 7

        (tokens[2] as StringLiteralToken).str shouldBe "\n"
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 12
    }

    @Test fun `wrong string literal`() {
        val scanner = Scanner()

        val buf1 = Buffer().apply { write(""" " """.toByteArray()) }
        val exception1 = shouldThrow<LexerException> { scanner.scan("", buf1) }
        "Incorrect string literal" shouldBe exception1.message

        val buf2 = Buffer().apply { write(""" "
        """.trimMargin().toByteArray()) }
        val exception2 = shouldThrow<LexerException> { scanner.scan("", buf2) }
        "Incorrect string literal" shouldBe exception2.message

        val buf3 = Buffer().apply { write(""" "\" """.trimMargin().toByteArray()) }
        val exception3 = shouldThrow<LexerException> { scanner.scan("", buf3) }
        "Incorrect string literal" shouldBe exception3.message

        val buf4 = Buffer().apply { write(""" "\u" """.trimMargin().toByteArray()) }
        val exception4 = shouldThrow<LexerException> { scanner.scan("", buf4) }
        "Illegal escape" shouldBe exception4.message

        val buf5 = Buffer().apply { write(""" "\xSS" """.trimMargin().toByteArray()) }
        val exception5 = shouldThrow<LexerException> { scanner.scan("", buf5) }
        "Illegal escape" shouldBe exception5.message

        val buf6 = Buffer().apply { write(""" "\x1S" """.trimMargin().toByteArray()) }
        val exception6 = shouldThrow<LexerException> { scanner.scan("", buf6) }
        "Illegal escape" shouldBe exception6.message
    }

    @Test fun brackets() {
        val buf = Buffer().apply { write("() {} []".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 6

        (tokens[0] as BracketToken).bracket shouldBe '('
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as BracketToken).bracket shouldBe ')'
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 2

        (tokens[2] as BracketToken).bracket shouldBe '{'
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 4

        (tokens[3] as BracketToken).bracket shouldBe '}'
        tokens[3].location.line shouldBe 1
        tokens[3].location.column shouldBe 5

        (tokens[4] as BracketToken).bracket shouldBe '['
        tokens[4].location.line shouldBe 1
        tokens[4].location.column shouldBe 7

        (tokens[5] as BracketToken).bracket shouldBe ']'
        tokens[5].location.line shouldBe 1
        tokens[5].location.column shouldBe 8
    }

    @Test fun comma() {
        val buf = Buffer().apply { write("(1, 2)".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 5

        tokens[2] should beInstanceOf<CommaToken>()
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 3
    }

    @Test fun dot() {
        val buf = Buffer().apply { write("r1.r2".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 3

        tokens[1] should beInstanceOf<DotToken>()
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 3
    }

    @Test fun preprocessor() {
        val buf = Buffer().apply { write("""
            #include "file.h"
            #define ABC
        """.trimIndent().toByteArray()) }

        val scanner = object: Scanner() {
            override fun isPreprocessorDirective(s: String): Boolean = s in setOf("include", "define")
        }
        val tokens = scanner.scan("", buf)

        tokens.size should { it > 4 }

        (tokens[0] as PreprocessorToken).name shouldBe "include"
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as StringLiteralToken).str shouldBe "file.h"
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 10

        tokens[2] should beInstanceOf<NewLineToken>()
        tokens[2].location.line shouldBe 1
        tokens[2].location.column shouldBe 18

        (tokens[3] as PreprocessorToken).name shouldBe "define"
        tokens[3].location.line shouldBe 2
        tokens[3].location.column shouldBe 1
    }

    @Test fun `invalid preprocessor directive`() {
        val buf = Buffer().apply { write("#inGlude 'file.h' ".toByteArray()) }
        val scanner = object: Scanner() {
            override fun isPreprocessorDirective(s: String): Boolean = s in setOf("include", "define")
        }
        val exception = shouldThrow<LexerException> { scanner.scan("", buf) }

        "Invalid preprocessor directive: inGlude" shouldBe exception.message
        exception.location.column shouldBe 1
    }

    @Test fun identifier() {
        val buf = Buffer().apply { write("x1 = 2".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 3

        (tokens[0] as IdentifierToken).name shouldBe "x1"
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1
    }

    @Test fun label() {
        val buf = Buffer().apply { write("lbl: nop".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 2

        (tokens[0] as LabelToken).name shouldBe "lbl"
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1

        (tokens[1] as IdentifierToken).name shouldBe "nop"
        tokens[1].location.line shouldBe 1
        tokens[1].location.column shouldBe 6
    }

    @Test fun `not label colon`() {
        val buf = Buffer().apply { write("extern val: byte".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 4

        tokens[2] should beInstanceOf<ColonToken>()
        tokens[2].location.column shouldBe 11
    }

    @Test fun `global label`() {
        val buf = Buffer().apply { write("@label: nop".toByteArray()) }
        val scanner = Scanner()
        val tokens = scanner.scan("", buf)

        tokens shouldHaveSize 2

        (tokens[0] as GlobalLabelToken).name shouldBe "label"
        tokens[0].location.line shouldBe 1
        tokens[0].location.column shouldBe 1
    }

    @Test fun `wrong global label`() {
        val scanner = Scanner()

        val buf1 = Buffer().apply { write("@: nop".toByteArray()) }
        val exception1 = shouldThrow<LexerException>{ scanner.scan("", buf1) }
        "Unexpected char" shouldBe exception1.message

        val buf2 = Buffer().apply { write("@1: nop".toByteArray()) }
        val exception2 = shouldThrow<LexerException>{ scanner.scan("", buf2) }
        "Unexpected char" shouldBe exception2.message

        val buf3 = Buffer().apply { write("@2 nop".toByteArray()) }
        val exception3 = shouldThrow<LexerException>{ scanner.scan("", buf3) }
        "Unexpected char" shouldBe exception3.message
    }

    @Test fun `right arrow operator`() {
        val scanner = Scanner()

        val buf = Buffer().apply { write("led_pin->ddr".toByteArray()) }
        val tokens = scanner.scan("", buf)
        tokens shouldHaveSize 3

        tokens[0] should beInstanceOf<IdentifierToken>()
        tokens[1] should beInstanceOf<OperatorToken>()
        (tokens[1] as OperatorToken).name shouldBe "->"
        tokens[2] should beInstanceOf<IdentifierToken>()
    }

    @Test fun `simple division`() {
        val scanner = Scanner()

        val buf = Buffer().apply { write("10/2".toByteArray()) }
        val tokens = scanner.scan("", buf)
        tokens shouldHaveSize 3

        tokens[0] should beInstanceOf<NumberToken>()
        tokens[1] should beInstanceOf<OperatorToken>()
        (tokens[1] as OperatorToken).name shouldBe "/"
        tokens[2] should beInstanceOf<NumberToken>()
    }


}
