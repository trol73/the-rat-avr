package ru.trolsoft.compiler.parser

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.therat.avr.compiler.TestFileLoaderStub
import ru.trolsoft.therat.lang.IdentifierToken


private fun streamForString(s: String): TokenStream =
    TestFileLoaderStub(s).loadSource("")

open class MyParser(s: String) : Parser(streamForString(s)) {
    override fun parseFuncCallArgument(): Node {
        val t = nextToken()
        if (t is IdentifierToken) {
            return IdentifierNode(t.location, t.name)
        }
        errorUnexpectedToken(t)
    }

    override fun parse(): AstNode {
        val node = parseExpression()
        return AstNode(listOf(node))
    }

}

class ParserTest {

    @Test
    fun `simple binary operation plus`() {
        val r = MyParser("1 + a").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeInstanceOf<BinaryOperNode>()) {
            operator shouldBe Operator.PLUS
            left.shouldBeInstanceOf<NumberNode>().value shouldBe 1
            right.shouldBeInstanceOf<IdentifierNode>().name shouldBe "a"
        }
    }

    @Test
    fun `simple binary operation minus`() {
        val r = MyParser("a-1").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.MINUS
            left.shouldBeInstanceOf<IdentifierNode>().name shouldBe "a"
            right.shouldBeInstanceOf<NumberNode>().value shouldBe 1
        }
    }

    @Test
    fun `simple binary operation in brackets`() {
        val r = MyParser("(1 + a)").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.PLUS
            left.shouldBeInstanceOf<NumberNode>().value shouldBe 1
            right.shouldBeInstanceOf<IdentifierNode>().name shouldBe "a"
        }
    }

    @Test
    fun `composite binary operation with one brackets`() {
        val r = MyParser("1 + a * (2-b)").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.PLUS
            left.shouldBeInstanceOf<NumberNode>().value shouldBe 1
            with (right.shouldBeTypeOf<BinaryOperNode>()) {
                operator shouldBe Operator.MUL
                left.shouldBeInstanceOf<IdentifierNode>().name shouldBe "a"
                with (right.shouldBeTypeOf<BinaryOperNode>()) {
                    operator shouldBe Operator.MINUS
                    left.shouldBeInstanceOf<NumberNode>().value shouldBe 2
                    right.shouldBeInstanceOf<IdentifierNode>().name shouldBe "b"
                }
            }
        }
    }


    @Test
    fun `composite binary operation with two brackets`() {
        val r = MyParser("((1 + a) * (2<<b))").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.MUL
            with (left.shouldBeTypeOf<BinaryOperNode>()) {
                operator shouldBe Operator.PLUS
                left.shouldBeInstanceOf<NumberNode>().value shouldBe 1
                right.shouldBeInstanceOf<IdentifierNode>().name shouldBe "a"
            }
            with (right.shouldBeTypeOf<BinaryOperNode>()) {
                operator shouldBe Operator.SHL
                left.shouldBeInstanceOf<NumberNode>().value shouldBe 2
                right.shouldBeInstanceOf<IdentifierNode>().name shouldBe "b"
            }
        }
    }

    @Test
    fun `multiple assigns operation`() {
        val r = MyParser("a = b = c").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.ASSIGN
            left.shouldBeInstanceOf<IdentifierNode>().name shouldBe "a"
            with (right.shouldBeTypeOf<BinaryOperNode>()) {
                operator shouldBe Operator.ASSIGN
                left.shouldBeInstanceOf<IdentifierNode>().name shouldBe "b"
                right.shouldBeInstanceOf<IdentifierNode>().name shouldBe "c"
            }
        }
    }

    @Test
    fun `multiple registers assign`() {
        val r = MyParser("r20 = r21 = r22").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.ASSIGN
            left.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r20"
            with (right.shouldBeTypeOf<BinaryOperNode>()) {
                operator shouldBe Operator.ASSIGN
                left.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r21"
                right.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r22"
            }
        }
    }

    @Test
    fun `simple prefix operation`() {
        val r = MyParser("++x").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<PrefixOperNode>()) {
            operator shouldBe Operator.INC
            expression.shouldBeInstanceOf<IdentifierNode>().name shouldBe "x"
        }
    }

    @Test
    fun `simple suffix operation`() {
        val r = MyParser("y--").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<SuffixOperNode>()) {
            operator shouldBe Operator.DEC
            expression.shouldBeInstanceOf<IdentifierNode>().name shouldBe "y"
        }
    }

    @Test
    fun `simple if condition`() {
        val r = MyParser("if (r1 == 1) r1 = 0").parse()
        r.nodes shouldHaveSize 1

        with (r.nodes.first().shouldBeTypeOf<IfNode>()) {
            with (cond.shouldBeTypeOf<BinaryOperNode>()) {
                operator shouldBe Operator.EQ
                left.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r1"
                right.shouldBeInstanceOf<NumberNode>().value shouldBe 1
            }
            with (thenBlock.shouldBeInstanceOf<BinaryOperNode>()) {
                operator shouldBe Operator.ASSIGN
                left.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r1"
                right.shouldBeInstanceOf<NumberNode>().value shouldBe 0
            }
            elseBlock.shouldBeNull()
        }
    }

    @Test
    fun `simple function call`() {
        val r = MyParser("func(x, y, z)").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<FunctionCallNode>()) {
            name shouldBe "func"
            args shouldHaveSize 3
            args[0].shouldBeTypeOf<IdentifierNode>().name shouldBe "x"
            args[1].shouldBeTypeOf<IdentifierNode>().name shouldBe "y"
            args[2].shouldBeTypeOf<IdentifierNode>().name shouldBe "z"
        }
    }

    @Test
    fun `assembler instruction with two args`() {
        val r = MyParser(
            """
            ldi r1, 1
        """.trimIndent()).parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<AssemblerInstrNode>()) {
            instr shouldBe "ldi"
            args.shouldNotBeNull()
            args shouldHaveSize 2
            args[0].shouldBeTypeOf<RegisterNode>().reg shouldBe "r1"
            args[1].shouldBeTypeOf<NumberNode>().value shouldBe 1
        }
    }

    @Test
    fun `assembler push with two args without comma`() {
        val parser = object : MyParser("""
            push r1 r2
        """.trimIndent()) {
            override fun asmRequiresComma(cmd: String, args: List<Node>): Boolean {
                val low = cmd.lowercase()
                return low != "push" && low != "pop"
            }
        }

        val r = parser.parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<AssemblerInstrNode>()) {
            instr shouldBe "push"
            args.shouldNotBeNull()
            args shouldHaveSize 2
            args[0].shouldBeTypeOf<RegisterNode>().reg shouldBe "r1"
            args[1].shouldBeTypeOf<RegisterNode>().reg shouldBe "r2"
        }
    }

    @Test
    fun `assembler instruction with one arg`() {
        val r = MyParser(
            """
            push r2
        """.trimIndent()).parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<AssemblerInstrNode>()) {
            instr shouldBe "push"
            args.shouldNotBeNull()
            args shouldHaveSize 1
            args.first().shouldBeTypeOf<RegisterNode>().reg shouldBe "r2"
        }
    }

    @Test
    fun `assembler instruction without args`() {
        val r = MyParser(
            """
            cli
        """.trimIndent()
        ).parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<AssemblerInstrNode>()) {
            instr shouldBe "cli"
            args.shouldBeNull()
        }
    }

    @Test
    fun `right arrow`() {
        val r = MyParser(
            """
            led_pin->PORT = 1
        """.trimIndent()
        ).parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.ASSIGN
            with (left.shouldBeInstanceOf<BinaryOperNode>()) {
                operator shouldBe Operator.ARROW_RIGHT
                left.shouldBeTypeOf<IdentifierNode>().name shouldBe "led_pin"
                right.shouldBeTypeOf<IdentifierNode>().name shouldBe "PORT"
            }
            right.shouldBeInstanceOf<NumberNode>().value shouldBe 1
        }
    }

    @Test
    fun `left arrow`() {
        val r = MyParser(
            """
            r24[1] <- r25[4]
        """.trimIndent()
        ).parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.ARROW_LEFT
            with (left.shouldBeInstanceOf<IndexedRegNode>()) {
                reg.reg shouldBe "r24"
                index.shouldBeInstanceOf<NumberNode>().value shouldBe 1
            }
            with (right.shouldBeInstanceOf<IndexedRegNode>()) {
                reg.reg shouldBe "r25"
                index.shouldBeInstanceOf<NumberNode>().value shouldBe 4
            }
        }
    }

    @Test
    fun `multiple assigns with function call`() {
        val r = MyParser(
            """
                SPH = r16 = high(RAMEND)
        """.trimIndent()
        ).parse()

        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeInstanceOf<BinaryOperNode>()) {
            operator shouldBe Operator.ASSIGN
            left.shouldBeInstanceOf<IdentifierNode>().name shouldBe "SPH"
            with (right.shouldBeInstanceOf<BinaryOperNode>()) {
                operator shouldBe Operator.ASSIGN
                with (right.shouldBeInstanceOf<FunctionCallNode>()) {
                    name shouldBe "high"
                    args.shouldNotBeNull()
                    args shouldHaveSize 1
                    args.first().shouldBeTypeOf<IdentifierNode>().name shouldBe "RAMEND"
                }
            }
        }
    }

    @Test
    fun `multiple assigns with register groups`() {
        val r = MyParser("""SPH = r16.r15 = 0x1234""".trimIndent()).parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeInstanceOf<BinaryOperNode>()) {
            operator shouldBe Operator.ASSIGN
            left.shouldBeInstanceOf<IdentifierNode>().name shouldBe "SPH"
            with (right.shouldBeInstanceOf<BinaryOperNode>()) {
                operator shouldBe Operator.ASSIGN
                with (left.shouldBeInstanceOf<RegisterGroupNode>()) {
                    regs shouldHaveSize 2
                    regs[0] shouldBe "r16"
                    regs[1] shouldBe "r15"
                }
                right.shouldBeInstanceOf<NumberNode>().value shouldBe 0x1234
            }
        }
    }


    @Test
    fun `arrow operation with inversion`() {
        val r = MyParser(
            """
                !port->pin_name
        """.trimIndent()).parse()
        r.nodes shouldHaveSize 1
        with  (r.nodes.first().shouldBeInstanceOf<PrefixOperNode>()) {
            operator shouldBe Operator.LOGICAL_NOT
            with (expression.shouldBeInstanceOf<BinaryOperNode>()) {
                operator shouldBe Operator.ARROW_RIGHT
                left.shouldBeInstanceOf<IdentifierNode>().name shouldBe "port"
                right.shouldBeInstanceOf<IdentifierNode>().name shouldBe "pin_name"
            }
        }
    }

    @Test
    fun `not pin and reg bit`() {
        val r = MyParser("!port->pin_name && r20[1]").parse()
        r.nodes shouldHaveSize 1
        with (r.nodes.first().shouldBeInstanceOf<BinaryOperNode>()) {
            operator shouldBe Operator.LOGICAL_AND
            with(left.shouldBeInstanceOf<PrefixOperNode>()) {
                operator.shouldBe(Operator.LOGICAL_NOT)
                with(expression.shouldBeInstanceOf<BinaryOperNode>()) {
                    left.shouldBeInstanceOf<IdentifierNode>().name shouldBe "port"
                    right.shouldBeInstanceOf<IdentifierNode>().name shouldBe "pin_name"
                }
            }
            with(right.shouldBeInstanceOf<IndexedRegNode>()) {
                reg.reg shouldBe "r20"
                index.shouldBeInstanceOf<NumberNode>().value shouldBe 1
            }
        }
    }
}