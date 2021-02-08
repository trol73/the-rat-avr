package ru.trolsoft.therat.lang

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.trolsoft.compiler.lexer.LexerException
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScannerTest {

    @Test fun testNumbers() {
        val stream = ByteArrayInputStream("123 0 0x12 012 0b1011_0001".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(5, tokens.size)

        assertTrue(tokens[0] is NumberToken)
        assertEquals(123, (tokens[0] as NumberToken).value)

        assertTrue(tokens[1] is NumberToken)
        assertEquals(0, (tokens[1] as NumberToken).value)

        assertTrue(tokens[2] is NumberToken)
        assertEquals(0x12, (tokens[2] as NumberToken).value)
        assertEquals(16, (tokens[2] as NumberToken).radix)

        assertTrue(tokens[3] is NumberToken)
        assertEquals(10, (tokens[3] as NumberToken).value)
        assertEquals(8, (tokens[3] as NumberToken).radix)

        assertTrue(tokens[4] is NumberToken)
        assertEquals(0b10110001, (tokens[4] as NumberToken).value)
        assertEquals(2, (tokens[4] as NumberToken).radix)
    }

    @Test fun testTwoLines() {
        val stream = ByteArrayInputStream("12\n34".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(3, tokens.size)

        assertTrue(tokens[0] is NumberToken)
        assertEquals(12, (tokens[0] as NumberToken).value)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is NewLineToken)
        assertEquals(1, tokens[1].location.line)
        assertEquals(3, tokens[1].location.column)

        assertTrue(tokens[2] is NumberToken)
        assertEquals(34, (tokens[2] as NumberToken).value)
        assertEquals(2, tokens[2].location.line)
        assertEquals(1, tokens[2].location.column)
    }

    @Test fun testTwoLinesDecHex() {
        val stream = ByteArrayInputStream("12\n0x34".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(3, tokens.size)

        assertTrue(tokens[0] is NumberToken)
        assertEquals(12, (tokens[0] as NumberToken).value)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is NewLineToken)
        assertEquals(1, tokens[1].location.line)
        assertEquals(3, tokens[1].location.column)

        assertTrue(tokens[2] is NumberToken)
        assertEquals(0x34, (tokens[2] as NumberToken).value)
        assertEquals(16, (tokens[2] as NumberToken).radix)
        assertEquals(2, tokens[2].location.line)
        assertEquals(1, tokens[2].location.column)
    }

    @Test fun testLineComment() {
        val stream = ByteArrayInputStream("123 ;comment line\n345".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(4, tokens.size)

        assertTrue(tokens[0] is NumberToken)
        assertEquals(123, (tokens[0] as NumberToken).value)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is LineCommentToken)
        assertEquals("comment line", (tokens[1] as LineCommentToken).text)
        assertEquals(1, tokens[1].location.line)
        assertEquals(5, tokens[1].location.column)

        assertTrue(tokens[2] is NewLineToken)
        assertEquals(1, tokens[2].location.line)
        assertEquals(18, tokens[2].location.column)
    }

    @Test fun testSimpleBlockComment() {
        val stream = ByteArrayInputStream("/*line1\nline2*/".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(1, tokens.size)

        assertTrue(tokens[0] is BlockCommentToken)
        assertEquals("line1\nline2", (tokens[0] as BlockCommentToken).text)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)
    }

    @Test fun testUnclosedBlockComment() {
        val stream = ByteArrayInputStream("/*line1\nline2*".toByteArray())
        val scanner = Scanner()
        val exception = assertThrows<LexerException> { scanner.scan("", stream) }

        assertEquals("Unclosed comment block", exception.message)
        assertEquals(1, exception.location.line)
        assertEquals(1, exception.location.column)
    }

    @Test fun testOperatorsSimple() {
        val stream = ByteArrayInputStream("+ - * / & | ! = ^ < > % << >> || && ++ --".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(18, tokens.size)

        assertTrue(tokens[0] is OperatorToken)
        assertEquals("+", (tokens[0] as OperatorToken).name)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is OperatorToken)
        assertEquals("-", (tokens[1] as OperatorToken).name)
        assertEquals(1, tokens[1].location.line)
        assertEquals(3, tokens[1].location.column)

        assertTrue(tokens[2] is OperatorToken)
        assertEquals("*", (tokens[2] as OperatorToken).name)
        assertEquals(1, tokens[2].location.line)
        assertEquals(5, tokens[2].location.column)

        assertTrue(tokens[3] is OperatorToken)
        assertEquals("/", (tokens[3] as OperatorToken).name)
        assertEquals(1, tokens[3].location.line)
        assertEquals(7, tokens[3].location.column)

        assertTrue(tokens[4] is OperatorToken)
        assertEquals("&", (tokens[4] as OperatorToken).name)
        assertEquals(1, tokens[4].location.line)
        assertEquals(9, tokens[4].location.column)

        assertTrue(tokens[5] is OperatorToken)
        assertEquals("|", (tokens[5] as OperatorToken).name)
        assertEquals(1, tokens[5].location.line)
        assertEquals(11, tokens[5].location.column)

        assertTrue(tokens[6] is OperatorToken)
        assertEquals("!", (tokens[6] as OperatorToken).name)
        assertEquals(1, tokens[6].location.line)
        assertEquals(13, tokens[6].location.column)

        assertTrue(tokens[7] is OperatorToken)
        assertEquals("=", (tokens[7] as OperatorToken).name)
        assertEquals(1, tokens[7].location.line)
        assertEquals(15, tokens[7].location.column)

        assertTrue(tokens[8] is OperatorToken)
        assertEquals("^", (tokens[8] as OperatorToken).name)
        assertEquals(1, tokens[8].location.line)
        assertEquals(17, tokens[8].location.column)

        assertTrue(tokens[9] is OperatorToken)
        assertEquals("<", (tokens[9] as OperatorToken).name)
        assertEquals(1, tokens[9].location.line)
        assertEquals(19, tokens[9].location.column)

        assertTrue(tokens[10] is OperatorToken)
        assertEquals(">", (tokens[10] as OperatorToken).name)
        assertEquals(1, tokens[10].location.line)
        assertEquals(21, tokens[10].location.column)

        assertTrue(tokens[11] is OperatorToken)
        assertEquals("%", (tokens[11] as OperatorToken).name)
        assertEquals(1, tokens[11].location.line)
        assertEquals(23, tokens[11].location.column)

        assertTrue(tokens[12] is OperatorToken)
        assertEquals("<<", (tokens[12] as OperatorToken).name)
        assertEquals(1, tokens[12].location.line)
        assertEquals(25, tokens[12].location.column)

        assertTrue(tokens[13] is OperatorToken)
        assertEquals(">>", (tokens[13] as OperatorToken).name)
        assertEquals(1, tokens[13].location.line)
        assertEquals(28, tokens[13].location.column)

        assertTrue(tokens[14] is OperatorToken)
        assertEquals("||", (tokens[14] as OperatorToken).name)
        assertEquals(1, tokens[14].location.line)
        assertEquals(31, tokens[14].location.column)

        assertTrue(tokens[15] is OperatorToken)
        assertEquals("&&", (tokens[15] as OperatorToken).name)
        assertEquals(1, tokens[15].location.line)
        assertEquals(34, tokens[15].location.column)

        assertTrue(tokens[16] is OperatorToken)
        assertEquals("++", (tokens[16] as OperatorToken).name)
        assertEquals(1, tokens[16].location.line)
        assertEquals(37, tokens[16].location.column)

        assertTrue(tokens[17] is OperatorToken)
        assertEquals("--", (tokens[17] as OperatorToken).name)
        assertEquals(1, tokens[17].location.line)
        assertEquals(40, tokens[17].location.column)
    }

    @Test fun testOperatorsWithEq() {
        val stream = ByteArrayInputStream("+= -= *= /= &= |= != == ^= <= >= %= <<= >>=".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(14, tokens.size)

        assertTrue(tokens[0] is OperatorToken)
        assertEquals("+=", (tokens[0] as OperatorToken).name)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is OperatorToken)
        assertEquals("-=", (tokens[1] as OperatorToken).name)
        assertEquals(1, tokens[1].location.line)
        assertEquals(4, tokens[1].location.column)

        assertTrue(tokens[2] is OperatorToken)
        assertEquals("*=", (tokens[2] as OperatorToken).name)
        assertEquals(1, tokens[2].location.line)
        assertEquals(7, tokens[2].location.column)

        assertTrue(tokens[3] is OperatorToken)
        assertEquals("/=", (tokens[3] as OperatorToken).name)
        assertEquals(1, tokens[3].location.line)
        assertEquals(10, tokens[3].location.column)

        assertTrue(tokens[4] is OperatorToken)
        assertEquals("&=", (tokens[4] as OperatorToken).name)
        assertEquals(1, tokens[4].location.line)
        assertEquals(13, tokens[4].location.column)

        assertTrue(tokens[5] is OperatorToken)
        assertEquals("|=", (tokens[5] as OperatorToken).name)
        assertEquals(1, tokens[5].location.line)
        assertEquals(16, tokens[5].location.column)

        assertTrue(tokens[6] is OperatorToken)
        assertEquals("!=", (tokens[6] as OperatorToken).name)
        assertEquals(1, tokens[6].location.line)
        assertEquals(19, tokens[6].location.column)

        assertTrue(tokens[7] is OperatorToken)
        assertEquals("==", (tokens[7] as OperatorToken).name)
        assertEquals(1, tokens[7].location.line)
        assertEquals(22, tokens[7].location.column)

        assertTrue(tokens[8] is OperatorToken)
        assertEquals("^=", (tokens[8] as OperatorToken).name)
        assertEquals(1, tokens[8].location.line)
        assertEquals(25, tokens[8].location.column)

        assertTrue(tokens[9] is OperatorToken)
        assertEquals("<=", (tokens[9] as OperatorToken).name)
        assertEquals(1, tokens[9].location.line)
        assertEquals(28, tokens[9].location.column)

        assertTrue(tokens[10] is OperatorToken)
        assertEquals(">=", (tokens[10] as OperatorToken).name)
        assertEquals(1, tokens[10].location.line)
        assertEquals(31, tokens[10].location.column)

        assertTrue(tokens[11] is OperatorToken)
        assertEquals("%=", (tokens[11] as OperatorToken).name)
        assertEquals(1, tokens[11].location.line)
        assertEquals(34, tokens[11].location.column)

        assertTrue(tokens[12] is OperatorToken)
        assertEquals("<<=", (tokens[12] as OperatorToken).name)
        assertEquals(1, tokens[12].location.line)
        assertEquals(37, tokens[12].location.column)

        assertTrue(tokens[13] is OperatorToken)
        assertEquals(">>=", (tokens[13] as OperatorToken).name)
        assertEquals(1, tokens[13].location.line)
        assertEquals(41, tokens[13].location.column)
    }

    @Test fun testNoSpaceOperators() {
        val stream = ByteArrayInputStream("+- */ =| =! &|".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(10, tokens.size)

        assertTrue(tokens[0] is OperatorToken)
        assertEquals("+", (tokens[0] as OperatorToken).name)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is OperatorToken)
        assertEquals("-", (tokens[1] as OperatorToken).name)
        assertEquals(1, tokens[1].location.line)
        assertEquals(2, tokens[1].location.column)

        assertTrue(tokens[2] is OperatorToken)
        assertEquals("*", (tokens[2] as OperatorToken).name)
        assertEquals(1, tokens[2].location.line)
        assertEquals(4, tokens[2].location.column)

        assertTrue(tokens[3] is OperatorToken)
        assertEquals("/", (tokens[3] as OperatorToken).name)
        assertEquals(1, tokens[3].location.line)
        assertEquals(5, tokens[3].location.column)

        assertTrue(tokens[4] is OperatorToken)
        assertEquals("=", (tokens[4] as OperatorToken).name)
        assertEquals(1, tokens[4].location.line)
        assertEquals(7, tokens[4].location.column)

        assertTrue(tokens[5] is OperatorToken)
        assertEquals("|", (tokens[5] as OperatorToken).name)
        assertEquals(1, tokens[5].location.line)
        assertEquals(8, tokens[5].location.column)

        assertTrue(tokens[6] is OperatorToken)
        assertEquals("=", (tokens[6] as OperatorToken).name)
        assertEquals(1, tokens[6].location.line)
        assertEquals(10, tokens[6].location.column)

        assertTrue(tokens[7] is OperatorToken)
        assertEquals("!", (tokens[7] as OperatorToken).name)
        assertEquals(1, tokens[7].location.line)
        assertEquals(11, tokens[7].location.column)

        assertTrue(tokens[8] is OperatorToken)
        assertEquals("&", (tokens[8] as OperatorToken).name)
        assertEquals(1, tokens[8].location.line)
        assertEquals(13, tokens[8].location.column)

        assertTrue(tokens[9] is OperatorToken)
        assertEquals("|", (tokens[9] as OperatorToken).name)
        assertEquals(1, tokens[9].location.line)
        assertEquals(14, tokens[9].location.column)
    }

    @Test fun testCharLiteral() {
        val stream = ByteArrayInputStream("'1' 'z' '\\'' '\\n' '\\\\'".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(5, tokens.size)

        assertTrue(tokens[0] is CharLiteralToken)
        assertEquals('1', (tokens[0] as CharLiteralToken).char)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is CharLiteralToken)
        assertEquals('z', (tokens[1] as CharLiteralToken).char)
        assertEquals(1, tokens[1].location.line)
        assertEquals(5, tokens[1].location.column)

        assertTrue(tokens[2] is CharLiteralToken)
        assertEquals('\'', (tokens[2] as CharLiteralToken).char)
        assertEquals(1, tokens[2].location.line)
        assertEquals(9, tokens[2].location.column)

        assertTrue(tokens[3] is CharLiteralToken)
        assertEquals('\n', (tokens[3] as CharLiteralToken).char)
        assertEquals(1, tokens[3].location.line)
        assertEquals(14, tokens[3].location.column)

        assertTrue(tokens[4] is CharLiteralToken)
        assertEquals('\\', (tokens[4] as CharLiteralToken).char)
        assertEquals(1, tokens[4].location.line)
        assertEquals(19, tokens[4].location.column)
    }

    @Test fun testCharLiteralError() {
        val scanner = Scanner()

        val stream1 = ByteArrayInputStream("'1".toByteArray())
        val exception1 = assertThrows<LexerException> { scanner.scan("", stream1) }
        assertEquals("Incorrect character literal", exception1.message)

        val stream2 = ByteArrayInputStream("''".toByteArray())
        val exception2 = assertThrows<LexerException> { scanner.scan("", stream2) }
        assertEquals("Empty character literal", exception2.message)

        val stream3 = ByteArrayInputStream("'\\f".toByteArray())
        val exception3 = assertThrows<LexerException> { scanner.scan("", stream3) }
        assertEquals("Incorrect character literal", exception3.message)

        val stream4 = ByteArrayInputStream("'\n'".toByteArray())
        val exception4 = assertThrows<LexerException> { scanner.scan("", stream4) }
        assertEquals("Incorrect character literal", exception4.message)

        val stream5 = ByteArrayInputStream("'f\n".toByteArray())
        val exception5 = assertThrows<LexerException> { scanner.scan("", stream5) }
        assertEquals("Incorrect character literal", exception5.message)

        val stream6 = ByteArrayInputStream("'".toByteArray())
        val exception6 = assertThrows<LexerException> { scanner.scan("", stream6) }
        assertEquals("Incorrect character literal", exception6.message)

        val stream7 = ByteArrayInputStream("' ".toByteArray())
        val exception7 = assertThrows<LexerException> { scanner.scan("", stream7) }
        assertEquals("Incorrect character literal", exception7.message)
    }

    @Test fun testStringLiteral() {
        val stream = ByteArrayInputStream("\"abc\" \"\"".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(2, tokens.size)

        assertTrue(tokens[0] is StringLiteralToken)
        assertEquals("abc", (tokens[0] as StringLiteralToken).str)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is StringLiteralToken)
        assertEquals("", (tokens[1] as StringLiteralToken).str)
        assertEquals(1, tokens[1].location.line)
        assertEquals(7, tokens[1].location.column)
    }

    @Test fun testEscapedStringLiteral() {
        val stream = ByteArrayInputStream(""" "\n" "\\" "\x0a"
            """.toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(4, tokens.size)

        assertTrue(tokens[0] is StringLiteralToken)
        assertEquals("\n", (tokens[0] as StringLiteralToken).str)
        assertEquals(1, tokens[0].location.line)
        assertEquals(2, tokens[0].location.column)

        assertTrue(tokens[1] is StringLiteralToken)
        assertEquals("\\", (tokens[1] as StringLiteralToken).str)
        assertEquals(1, tokens[1].location.line)
        assertEquals(7, tokens[1].location.column)

        assertTrue(tokens[2] is StringLiteralToken)
        assertEquals("\n", (tokens[2] as StringLiteralToken).str)
        assertEquals(1, tokens[2].location.line)
        assertEquals(12, tokens[2].location.column)
    }

    @Test fun testWrongStringLiteral() {
        val scanner = Scanner()

        val stream1 = ByteArrayInputStream(""" " """.toByteArray())
        val exception1 = assertThrows<LexerException> { scanner.scan("", stream1) }
        assertEquals("Incorrect string literal", exception1.message)

        val stream2 = ByteArrayInputStream(""" "
        " """.trimMargin().toByteArray())
        val exception2 = assertThrows<LexerException> { scanner.scan("", stream2) }
        assertEquals("Incorrect string literal", exception2.message)

        val stream3 = ByteArrayInputStream(""" "\" """.trimMargin().toByteArray())
        val exception3 = assertThrows<LexerException> { scanner.scan("", stream3) }
        assertEquals("Incorrect string literal", exception3.message)

        val stream4 = ByteArrayInputStream(""" "\u" """.trimMargin().toByteArray())
        val exception4 = assertThrows<LexerException> { scanner.scan("", stream4) }
        assertEquals("Illegal escape", exception4.message)

        val stream5 = ByteArrayInputStream(""" "\xSS" """.trimMargin().toByteArray())
        val exception5 = assertThrows<LexerException> { scanner.scan("", stream5) }
        assertEquals("Illegal escape", exception5.message)

        val stream6 = ByteArrayInputStream(""" "\x1S" """.trimMargin().toByteArray())
        val exception6 = assertThrows<LexerException> { scanner.scan("", stream6) }
        assertEquals("Illegal escape", exception6.message)
    }

    @Test fun testBrackets() {
        val stream = ByteArrayInputStream("() {} []".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(6, tokens.size)

        assertTrue(tokens[0] is BracketToken)
        assertEquals('(', (tokens[0] as BracketToken).bracket)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is BracketToken)
        assertEquals(')', (tokens[1] as BracketToken).bracket)
        assertEquals(1, tokens[1].location.line)
        assertEquals(2, tokens[1].location.column)

        assertTrue(tokens[2] is BracketToken)
        assertEquals('{', (tokens[2] as BracketToken).bracket)
        assertEquals(1, tokens[2].location.line)
        assertEquals(4, tokens[2].location.column)

        assertTrue(tokens[3] is BracketToken)
        assertEquals('}', (tokens[3] as BracketToken).bracket)
        assertEquals(1, tokens[3].location.line)
        assertEquals(5, tokens[3].location.column)

        assertTrue(tokens[4] is BracketToken)
        assertEquals('[', (tokens[4] as BracketToken).bracket)
        assertEquals(1, tokens[4].location.line)
        assertEquals(7, tokens[4].location.column)

        assertTrue(tokens[5] is BracketToken)
        assertEquals(']', (tokens[5] as BracketToken).bracket)
        assertEquals(1, tokens[5].location.line)
        assertEquals(8, tokens[5].location.column)
    }

    @Test fun testComma() {
        val stream = ByteArrayInputStream("(1, 2)".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(5, tokens.size)

        assertTrue(tokens[2] is CommaToken)
        assertEquals(1, tokens[2].location.line)
        assertEquals(3, tokens[2].location.column)
    }

    @Test fun testDot() {
        val stream = ByteArrayInputStream("r1.r2".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(3, tokens.size)

        assertTrue(tokens[1] is DotToken)
        assertEquals(1, tokens[1].location.line)
        assertEquals(3, tokens[1].location.column)
    }

    @Test fun testPreprocessor() {
        val stream = ByteArrayInputStream("""
            #include "file.h"
            #define ABC
        """.trimIndent().toByteArray())

        val scanner = object: Scanner() {
            override fun isPreprocessorDirective(s: String): Boolean = s in setOf("include", "define")
        }
        val tokens = scanner.scan("", stream)

        assertTrue(tokens.size > 4)

        assertTrue(tokens[0] is PreprocessorToken)
        assertEquals("include", (tokens[0] as PreprocessorToken).name)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is StringLiteralToken)
        assertEquals("file.h", (tokens[1] as StringLiteralToken).str)
        assertEquals(1, tokens[1].location.line)
        assertEquals(10, tokens[1].location.column)

        assertTrue(tokens[2] is NewLineToken)
        assertEquals(1, tokens[2].location.line)
        assertEquals(18, tokens[2].location.column)

        assertTrue(tokens[3] is PreprocessorToken)
        assertEquals("define", (tokens[3] as PreprocessorToken).name)
        assertEquals(2, tokens[3].location.line)
        assertEquals(1, tokens[3].location.column)
    }

    @Test fun testInvalidPreprocessorDirective() {
        val stream = ByteArrayInputStream("#inGlude 'file.h' ".toByteArray())
        val scanner = object: Scanner() {
            override fun isPreprocessorDirective(s: String): Boolean = s in setOf("include", "define")
        }
        val exception = assertThrows<LexerException> { scanner.scan("", stream) }

        assertEquals("Invalid preprocessor directive: inGlude", exception.message)
        assertEquals(1, exception.location.column)
    }

    @Test fun testIdentifier() {
        val stream = ByteArrayInputStream("x1 = 2".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(3, tokens.size)

        assertTrue(tokens[0] is IdentifierToken)
        assertEquals("x1", (tokens[0] as IdentifierToken).name)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)
    }

    @Test fun testLabel() {
        val stream = ByteArrayInputStream("lbl: nop".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(2, tokens.size)

        assertTrue(tokens[0] is LabelToken)
        assertEquals("lbl", (tokens[0] as LabelToken).name)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)

        assertTrue(tokens[1] is IdentifierToken)
        assertEquals("nop", (tokens[1] as IdentifierToken).name)
        assertEquals(1, tokens[1].location.line)
        assertEquals(6, tokens[1].location.column)
    }

    @Test fun testNotLabelColon() {
        val stream = ByteArrayInputStream("extern val: byte".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(4, tokens.size)

        assertTrue(tokens[2] is ColonToken)
        assertEquals(11, tokens[2].location.column)
    }

    @Test fun testLocalLabel() {
        val stream = ByteArrayInputStream("@label: nop".toByteArray())
        val scanner = Scanner()
        val tokens = scanner.scan("", stream)

        assertEquals(2, tokens.size)

        assertTrue(tokens[0] is GlobalLabelToken)
        assertEquals("label", (tokens[0] as GlobalLabelToken).name)
        assertEquals(1, tokens[0].location.line)
        assertEquals(1, tokens[0].location.column)
    }

    @Test fun testWrongLocalLabel() {
        val scanner = Scanner()

        val stream1 = ByteArrayInputStream("@: nop".toByteArray())
        val exception1 = assertThrows<LexerException>{ scanner.scan("", stream1) }
        assertEquals("Unexpected char", exception1.message)

        val stream2 = ByteArrayInputStream("@1: nop".toByteArray())
        val exception2 = assertThrows<LexerException>{ scanner.scan("", stream2) }
        assertEquals("Unexpected char", exception2.message)

        val stream3 = ByteArrayInputStream("@2 nop".toByteArray())
        val exception3 = assertThrows<LexerException>{ scanner.scan("", stream3) }
        assertEquals("Unexpected char", exception3.message)
    }

    @Test fun testArrowOperator() {
        val scanner = Scanner()

        val stream = ByteArrayInputStream("led_pin->ddr".toByteArray())
        val tokens = scanner.scan("", stream)
        assertEquals(3, tokens.size)

        assertTrue(tokens[0] is IdentifierToken)
        assertTrue(tokens[1] is OperatorToken)
        assertEquals("->", (tokens[1] as OperatorToken).name)
        assertTrue(tokens[2] is IdentifierToken)
    }

    @Test fun testSimpleDivisionExpr() {
        val scanner = Scanner()

        val stream = ByteArrayInputStream("10/2".toByteArray())
        val tokens = scanner.scan("", stream)
        assertEquals(3, tokens.size)

        assertTrue(tokens[0] is NumberToken)
        assertTrue(tokens[1] is OperatorToken)
        assertEquals("/", (tokens[1] as OperatorToken).name)
        assertTrue(tokens[2] is NumberToken)
    }


}