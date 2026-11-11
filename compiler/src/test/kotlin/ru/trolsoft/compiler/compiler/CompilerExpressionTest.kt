package ru.trolsoft.compiler.compiler

import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.lang.Scanner


class CompileExpressionTest {
    private fun calcExpr(expr: String): Int {
        val buf = Buffer().apply { write(expr.toByteArray()) }
        val scanner = Scanner()
        val tokenStream = TokenStream(scanner.scan("", buf).toMutableList(), "")
        buf.close()
        val parser = object : Parser(tokenStream) {
            override fun parse(): Node {
                return parseExpression()
            }

            override fun parseFuncCallArgument(): Node = CommentNode(listOf())

        }
        val compiler = object : Compiler(scanner, "", ConsoleReporter({ }, { })) {
            override fun parse(stream: TokenStream): AstNode = AstNode(listOf(parser.parse()))
            override fun compile(ast: AstNode) {}
            override fun compileAssembler(node: AssemblerInstrNode, comment: String?) {}
            override fun resolveArg(arg: Node): Node = arg
        }
        return compiler.calculateConst(parser.parse())
    }

    @Test fun testCalcSimpleConst() {
        calcExpr("123") shouldBe 123
    }

    @Test fun testCalcSimpleUnaryConst() {
        calcExpr("!1") shouldBe 0
    }

    @Test fun testCalcSimpleBinary() {
        calcExpr("1+2") shouldBe 3
        calcExpr("1-2") shouldBe -1
        calcExpr("3*2") shouldBe 6
        calcExpr("10/2") shouldBe 5
        calcExpr("1 << 3") shouldBe 8
        calcExpr("8>>1") shouldBe 4
        calcExpr("8>>1") shouldBe 4
        calcExpr("0b10100000 | 0b01010000") shouldBe 0b11110000
        calcExpr("0b1111 ^ 0b0011") shouldBe 0b1100
        calcExpr("0b11111111 & 0b11110000") shouldBe 0b11110000

        calcExpr("1 || 0") shouldBe 1
        calcExpr("1 || 1") shouldBe 1
        calcExpr("0 || 0") shouldBe 0
        calcExpr("1 && 0") shouldBe 0
        calcExpr("1 && 1") shouldBe 1
        calcExpr("1 == 1") shouldBe 1
        calcExpr("1 != 0") shouldBe 1
        calcExpr("10 > 5") shouldBe 1
        calcExpr("10 > 15") shouldBe 0
        calcExpr("10 >= 10") shouldBe 1
        calcExpr("10 < 1") shouldBe 0
        calcExpr("10 <= 9") shouldBe 0
    }

    @Test fun testCalc() {
        calcExpr("1+2*4") shouldBe 9
        calcExpr("(1<<3)|(1<<7)") shouldBe ((1 shl 3) or (1 shl 7))
        calcExpr("(1+2)*3-1") shouldBe 8
    }

}