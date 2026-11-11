package ru.trolsoft.therat.avr.parser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.therat.lang.BlockCommentToken
import ru.trolsoft.therat.lang.LineCommentToken
import ru.trolsoft.therat.lang.NewLineToken
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import kotlinx.io.Buffer
import ru.trolsoft.therat.avr.*

class AvrRatParserTest {

    @Test fun `global variables`() {
        val ast = parse("""
            var b: byte
            var w: word
            var dw: dword
            var qw: qword
            var p: ptr
            var pp: prgptr
            var a1, a2, a3: byte
        """.trimIndent())

        ast.nodes shouldHaveSize 9
        with (ast.nodes[0]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "b"
            dataType shouldBe DataType.BYTE
        }
        with (ast.nodes[1]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "w"
            dataType shouldBe DataType.WORD
        }
        with (ast.nodes[2]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "dw"
            dataType shouldBe DataType.DWORD
        }
        with (ast.nodes[3]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "qw"
            dataType shouldBe DataType.QWORD
        }
        with (ast.nodes[4]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "p"
            dataType shouldBe DataType.PTR
        }
        with (ast.nodes[5]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "pp"
            dataType shouldBe DataType.PRGPTR
        }
        with (ast.nodes[6]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "a1"
            dataType shouldBe DataType.BYTE
        }
        with (ast.nodes[7]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "a2"
            dataType shouldBe DataType.BYTE
        }
        with (ast.nodes[8]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "a3"
            dataType shouldBe DataType.BYTE
        }
    }

    @Test fun `simple global array var`() {
        val ast = parse("""
            var buf: byte[64]
            var buf1, buf2: word[32]
        """.trimIndent())
        ast.nodes shouldHaveSize 3

        with (ast.nodes[0]) {
            shouldBeInstanceOf<DataArrayDeclarationNode>()
            name shouldBe "buf"
            dataType shouldBe DataType.BYTE
            size.shouldBeInstanceOf<NumberNode>().value shouldBe 64
        }
        with (ast.nodes[1]) {
            shouldBeInstanceOf<DataArrayDeclarationNode>()
            name shouldBe "buf1"
            dataType shouldBe DataType.WORD
            size.shouldBeInstanceOf<NumberNode>().value shouldBe 32
        }
        with (ast.nodes[2]) {
            shouldBeInstanceOf<DataArrayDeclarationNode>()
            name shouldBe "buf2"
            dataType shouldBe DataType.WORD
            size.shouldBeInstanceOf<NumberNode>().value shouldBe 32
        }
    }

    @Test fun `pin definition`() {
        val ast = parse("""
            pin led = A0
            pin buzzer = D2
        """.trimIndent())
        ast.nodes shouldHaveSize 2
        with (ast.nodes[0].shouldBeInstanceOf<PinDeclarationNode>()) {
            name shouldBe "led"
            devicePin shouldBe "A0"
        }
        with (ast.nodes[1].shouldBeInstanceOf<PinDeclarationNode>()) {
            name shouldBe "buzzer"
            devicePin shouldBe "D2"
        }
    }

    @Test fun `simple bus`() {
        val ast = parse("""
            bus addr = [A2, A3]
        """.trimIndent())
        ast.nodes shouldHaveSize 1
        with (ast.nodes[0].shouldBeInstanceOf<BusDeclarationNode>()) {
            pins shouldHaveSize 2
            pins[0].shouldBeInstanceOf<IdentifierNode>().name shouldBe "A2"
            pins[1].shouldBeInstanceOf<IdentifierNode>().name shouldBe "A3"
            size shouldBe -1
        }
    }

    @Test fun `range bus`() {
        val ast = parse("""
            bus addr = [A0..A7]
        """.trimIndent())
        ast.nodes shouldHaveSize 1
        with (ast.nodes[0].shouldBeInstanceOf<BusDeclarationNode>()) {
            pins shouldHaveSize 1
            with  (pins[0].shouldBeInstanceOf<BusRangeNode>()) {
                from.name shouldBe "A0"
                to.name shouldBe "A7"
                size shouldBe -1
            }
        }
    }

    @Test fun `mixed bus 1`() {
        val ast = parse(
            """
            bus addr = [B1, B2, A0..A7]
        """.trimIndent())
        ast.nodes shouldHaveSize 1
        with (ast.nodes[0].shouldBeInstanceOf<BusDeclarationNode>()) {
            pins shouldHaveSize 3
            pins[0].shouldBeInstanceOf<IdentifierNode>().name shouldBe "B1"
            pins[1].shouldBeInstanceOf<IdentifierNode>().name shouldBe "B2"
            with (pins[2].shouldBeInstanceOf<BusRangeNode>()) {
                from.name shouldBe "A0"
                to.name shouldBe "A7"
                size shouldBe -1
            }
        }
    }

    @Test fun `mixed bus 2`() {
        val ast = parse(
            """
            bus addr = [A0..A7, B1]
        """.trimIndent())
        ast.nodes shouldHaveSize 1
        with (ast.nodes[0].shouldBeInstanceOf<BusDeclarationNode>()) {
            pins shouldHaveSize 2
            with (pins[0].shouldBeInstanceOf<BusRangeNode>()) {
                from.name shouldBe "A0"
                to.name shouldBe "A7"
            }
            pins[1].shouldBeInstanceOf<IdentifierNode>().name shouldBe "B1"
            size shouldBe -1
        }
    }

    @Test fun `sized bus`() {
        val ast = parse("""
            bus addr[2] = [A2, A3]
        """.trimIndent())
        ast.nodes shouldHaveSize 1
        with (ast.nodes[0].shouldBeInstanceOf<BusDeclarationNode>()) {
            pins shouldHaveSize 2
            pins[0].shouldBeInstanceOf<IdentifierNode>().name shouldBe "A2"
            pins[1].shouldBeInstanceOf<IdentifierNode>().name shouldBe "A3"
            size shouldBe 2
        }
    }

    @Test fun `simple proc`() {
        val ast = parse("""
            proc myProc(param: r1) {
            }
        """.trimIndent())
        ast.nodes shouldHaveSize 1
        with (ast.nodes[0].shouldBeInstanceOf<ProcNode>()) {
            name shouldBe "myProc"
            args shouldHaveSize 1
            with (args.first()) {
                name shouldBe "param"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r1"
            }
        }
    }

    @Test fun `code without proc error`() {
        shouldThrow<ParserException> {
            parse("""
            r21 = 1
        """.trimIndent())}
    }

    @Test fun `proc with single result`() {
        val ast = parse("""
            proc myProc(param: r24) -> (res1: r24) {
            }
        """.trimIndent())
        with (ast.nodes[0].shouldBeInstanceOf<ProcNode>()) {
            name shouldBe "myProc"
            args shouldHaveSize 1
            with (args.first()) {
                name shouldBe "param"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r24"
            }
            results shouldHaveSize 1
            with (results.first()) {
                name shouldBe "res1"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r24"
            }
        }
    }

    @Test fun `void proc with single result `() {
        val ast = parse("""
            proc myProc() -> (res1: r24) {
            }
        """.trimIndent())
        with (ast.nodes[0].shouldBeInstanceOf<ProcNode>()) {
            name shouldBe "myProc"
            args shouldHaveSize 0
            results shouldHaveSize 1
            with (results.first()) {
                name shouldBe "res1"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r24"
            }
        }
    }

    @Test fun `proc with two results`() {
        val ast = parse("""
            proc myProc(param: r24) -> (res1: r24, res2: r25) {
            }
        """.trimIndent())
        with (ast.nodes[0].shouldBeInstanceOf<ProcNode>()) {
            name shouldBe "myProc"
            args shouldHaveSize 1
            with (args.first()) {
                name shouldBe "param"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r24"
            }
            results shouldHaveSize 2
            with (results.first()) {
                name shouldBe "res1"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r24"
            }
            with (results.last()) {
                name shouldBe "res2"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r25"
            }
        }
    }

    @Test fun `proc with two results - reg and Z-pair`() {
        val ast = parse("""
            proc myProc(param: r24) -> (res1: r24, res2: Z) {
            }
        """.trimIndent())
        with (ast.nodes[0].shouldBeInstanceOf<ProcNode>()) {
            name shouldBe "myProc"
            args shouldHaveSize 1
            with (args.first()) {
                name shouldBe "param"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r24"
            }
            results shouldHaveSize 2
            with (results.first()) {
                name shouldBe "res1"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r24"
            }
            with (results.last()) {
                name shouldBe "res2"
                val g = expr.shouldBeInstanceOf<RegisterGroupNode>()
                g.size() shouldBe 2
                g.regs[0] shouldBe "r31"
                g.regs[1] shouldBe "r30"
            }
        }
    }

    @Test fun `proc with two results - group`() {
        val ast = parse("""
            proc myProc(param: r24) -> (res: r3.r2.r1) {
            }
        """.trimIndent())
        with (ast.nodes[0].shouldBeInstanceOf<ProcNode>()) {
            name shouldBe "myProc"
            args shouldHaveSize 1
            with (args.first()) {
                name shouldBe "param"
                expr.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r24"
            }
            results shouldHaveSize 1
            with (results.first()) {
                name shouldBe "res"
                val g = expr.shouldBeInstanceOf<RegisterGroupNode>()
                g.size() shouldBe 3
                g.regs[0] shouldBe "r3"
                g.regs[1] shouldBe "r2"
                g.regs[2] shouldBe "r1"
            }
        }
    }

    @Test fun `vectors table`() {
        val s = """
            vectors {
                TIMER2_COMP:
                    rjmp	timer0_comp
                default:
                    nop
            }
             """.trimIndent()
        val r = parse(s, false)
        // vectors are stored in the 'vectors' field, not in 'nodes'
        r.vectors shouldNotBe null
        with (r.vectors!!) {
            table.size shouldBe 2
            with (getVectorNode("TIMER2_COMP").shouldBeInstanceOf<AssemblerInstrNode>()) {
                instr shouldBe "rjmp"
            }
            with (getVectorNode("default").shouldBeInstanceOf<AssemblerInstrNode>()) {
                instr shouldBe "nop"
            }
        }
    }


    @Test fun `hello world`() {
        val s = """
            pin led = D0

            proc main() {
                led->ddr = 1
                loop {
                    led->port = 1
                    rcall	delay(100)
                    led->port = 0
                    rcall	delay(200)
                }
            }

            proc delay(time: r21) {
                loop (time) {
                    loop (r22 = 0xff)
                        nop
                }
                ret
            }

             """.trimIndent()
        val r = parse(s, false)
        r.size() shouldBe 3
        // pin declaration
        r[0].shouldBeInstanceOf<PinDeclarationNode>().name shouldBe "led"
        // main proc
        val mainProc = r[1].shouldBeInstanceOf<ProcNode>()
        mainProc.name shouldBe "main"
        mainProc.args shouldHaveSize 0
        // delay proc
        val delayProc = r[2].shouldBeInstanceOf<ProcNode>()
        delayProc.name shouldBe "delay"
        delayProc.args shouldHaveSize 1
        delayProc.args[0].name shouldBe "time"
        // main proc has empty args
        mainProc.args shouldHaveSize 0
    }

    @Test fun `global strings in prg section`() {
        val s = """
            byte[] "some string"
            byte[] {"string1", "string2"}
            """
        val r = parse(s, false)
        r.size() shouldBe 2
        r[0].shouldBeInstanceOf<DataBlockNode>().type shouldBe DataType.BYTE
        r[1].shouldBeInstanceOf<DataBlockNode>().type shouldBe DataType.BYTE
    }

    @Test fun `local strings in prg section`() {
        val s = """
            proc main() {
                byte[] "some string"
                byte[] {"string1", "string2"}
            }
            """
        val r = parse(s, false)
        r.size() shouldBe 1
        val mainProc = r[0].shouldBeInstanceOf<ProcNode>()
        mainProc.name shouldBe "main"
        mainProc.body.size() shouldBe 2
        mainProc.body[0].shouldBeInstanceOf<DataBlockNode>().type shouldBe DataType.BYTE
        mainProc.body[1].shouldBeInstanceOf<DataBlockNode>().type shouldBe DataType.BYTE
    }


    @Test fun `multi if`() {
        val s = """
            proc main() {
                if (ZL == 1 || ZL == 7 || ZL == 8 || ZL == 14) goto clockwise
            }
        """.trimIndent()
        val r = parse(s, false)
        r.size() shouldBe 1
        val mainProc = r[0].shouldBeInstanceOf<ProcNode>()
        mainProc.name shouldBe "main"
        mainProc.body.size() shouldBe 1
        val ifNode = mainProc.body[0].shouldBeInstanceOf<IfNode>()
        // Check the condition is a complex OR expression
        val cond = ifNode.cond.shouldBeTypeOf<BinaryOperNode>()
        cond.operator shouldBe Operator.LOGICAL_OR
        // Then block is goto clockwise
        val gotoNode = ifNode.thenBlock.shouldBeTypeOf<GotoNode>()
        gotoNode.label shouldBe "clockwise"
    }


    @Test fun `some expression`() {
        val s = """
            proc main() {
                r1 = (r2 << 3) + r3 - r4 - 5 + 2*2
            }
        """.trimIndent()
        val r = parse(s, false)
        r.size() shouldBe 1
        val mainProc = r[0].shouldBeInstanceOf<ProcNode>()
        mainProc.name shouldBe "main"
        mainProc.body.size() shouldBe 1
        val assign = mainProc.body[0].shouldBeTypeOf<BinaryOperNode>()
        assign.operator shouldBe Operator.ASSIGN
        // Left side is r1
        assign.left.shouldBeTypeOf<RegisterNode>().reg shouldBe "r1"
        // Right side is complex expression: ((r2 << 3) + r3) - r4 - 5 + (2*2)
        val right = assign.right.shouldBeTypeOf<BinaryOperNode>()
        right.operator shouldBe Operator.PLUS
        // The rightmost is 2*2
        val mul = right.right.shouldBeTypeOf<BinaryOperNode>()
        mul.operator shouldBe Operator.MUL
        mul.left.shouldBeTypeOf<NumberNode>().value shouldBe 2
        mul.right.shouldBeTypeOf<NumberNode>().value shouldBe 2
    }

    @Test fun `if (++x == y)`() {
        val r = parse(
            """
                proc main() {
                    if (++r21 < 100) nop
                }
            """.trimIndent()
        )
        val body = r.nodes.first().shouldBeTypeOf<ProcNode>().body
        val ifNode = body.nodes.first().shouldBeTypeOf<IfNode>()
        val cond = ifNode.cond.shouldBeTypeOf<BinaryOperNode>()
        with (cond.left.shouldBeTypeOf<PrefixOperNode>()) {
            operator shouldBe Operator.INC
            expression.shouldBeTypeOf<RegisterNode>().reg shouldBe "r21"
        }
        cond.operator shouldBe Operator.LESS
        cond.right.shouldBeTypeOf<NumberNode>().value shouldBe 100
    }

    @Test fun `r31 = ~r10 + 1 - var1`() {
        val res = parse(
            """
                proc main() {
                    r31 = ~r10 + 1 - var1
                }
            """.trimIndent()
        )
        val r = ((res.nodes[0] as ProcNode).body.nodes[0] as BinaryOperNode).right
        with (r.shouldBeInstanceOf<BinaryOperNode>()) {
            operator shouldBe Operator.MINUS
            right.shouldBeInstanceOf<IdentifierNode>().name shouldBe "var1"
            with (left.shouldBeInstanceOf<BinaryOperNode>()) {
                operator shouldBe Operator.PLUS
                right.shouldBeInstanceOf<NumberNode>().value shouldBe 1
                with (left.shouldBeInstanceOf<PrefixOperNode>()) {
                    operator shouldBe  Operator.BINARY_NOT
                    expression.shouldBeInstanceOf<RegisterNode>().reg shouldBe "r10"
                }
            }
        }
    }
}

class TestFileLoaderStub(private val text: String) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        val buf = Buffer().apply { write(text.toByteArray()) }
        return TokenStream(scanner.scan(path, buf).toMutableList(), path)
    }
}

fun parse(s: String, removeWhitespace: Boolean = false): AvrAstNode {
    val stream = TestFileLoaderStub(s).loadSource("")
    if (removeWhitespace) {
        stream.remove { it is NewLineToken || it is LineCommentToken || it is BlockCommentToken }
    }
    return AvrRatParser(stream).parse()
}