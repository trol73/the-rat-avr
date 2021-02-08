package ru.trolsoft.compiler.compiler

import org.junit.jupiter.api.Test
import ru.trolsoft.therat.lang.Scanner
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals


class CompileExpressionTest {
    private fun calcExpr(expr: String): Int {
        val stream = ByteArrayInputStream(expr.toByteArray())
        val scanner = Scanner()
        val tokenStream = TokenStream(scanner.scan("", stream).toMutableList(), "")
        val parser = object : Parser(tokenStream) {
            override fun parse(): Node {
                return parseExpression()
            }

            override fun parseFuncCallArgument(): Node = CommentNode(listOf())

        }
        val compiler = object : Compiler(scanner) {
            override fun parse(stream: TokenStream): AstNode = AstNode(listOf(parser.parse()))
            override fun compile(ast: AstNode) {}
        }
        return compiler.calculateConst(parser.parse())
    }

    @Test fun testCalcSimpleConst() {
        assertEquals(123, calcExpr("123"))
    }

    @Test fun testCalcSimpleUnaryConst() {
        assertEquals(0, calcExpr("!1"))
    }

    @Test fun testCalcSimpleBinary() {
        assertEquals(3, calcExpr("1+2"))
        assertEquals(-1, calcExpr("1-2"))
        assertEquals(6, calcExpr("3*2"))
        assertEquals(5, calcExpr("10/2"))
        assertEquals(8, calcExpr("1 << 3"))
        assertEquals(4, calcExpr("8>>1"))
        assertEquals(4, calcExpr("8>>1"))
        assertEquals(0b11110000, calcExpr("0b10100000 | 0b01010000"))
        assertEquals(0b1100, calcExpr("0b1111 ^ 0b0011"))
        assertEquals(0b11110000, calcExpr("0b11111111 & 0b11110000"))

        assertEquals(1, calcExpr("1 || 0"))
        assertEquals(1, calcExpr("1 || 1"))
        assertEquals(0, calcExpr("0 || 0"))
        assertEquals(0, calcExpr("1 && 0"))
        assertEquals(1, calcExpr("1 && 1"))
        assertEquals(1, calcExpr("1 == 1"))
        assertEquals(1, calcExpr("1 != 0"))
        assertEquals(1, calcExpr("10 > 5"))
        assertEquals(0, calcExpr("10 > 15"))
        assertEquals(1, calcExpr("10 >= 10"))
        assertEquals(0, calcExpr("10 < 1"))
        assertEquals(0, calcExpr("10 <= 9"))
    }

    @Test fun testCalc() {
        assertEquals(9, calcExpr("1+2*4"))
        assertEquals((1 shl 3) or (1 shl 7), calcExpr("(1<<3)|(1<<7)"))
        assertEquals(8, calcExpr("(1+2)*3-1"))
    }

}