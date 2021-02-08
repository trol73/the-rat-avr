package ru.trolsoft.therat.avr

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.trolsoft.therat.lang.BlockCommentToken
import ru.trolsoft.therat.lang.LineCommentToken
import ru.trolsoft.therat.lang.NewLineToken
import ru.trolsoft.compiler.parser.AstNode
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.NumberNode
import ru.trolsoft.compiler.parser.ParserException
import ru.trolsoft.compiler.parser.RegisterNode
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvrParserTest {

    @Test fun testGlobalVariables() {
        val ast = parse("""
            var b: byte
            var w: word
            var dw: dword
            var p: ptr
            var pp: prgptr
            var a1, a2, a3: byte
        """.trimIndent())

        assertEquals(8, ast.nodes.size)
        var d = ast.nodes[0]
        assertTrue(d is VarDeclarationNode && d.name == "b" && d.dataType == DataType.BYTE)
        d = ast.nodes[1]
        assertTrue(d is VarDeclarationNode && d.name == "w" && d.dataType == DataType.WORD)
        d = ast.nodes[2]
        assertTrue(d is VarDeclarationNode && d.name == "dw" && d.dataType == DataType.DWORD)
        d = ast.nodes[3]
        assertTrue(d is VarDeclarationNode && d.name == "p" && d.dataType == DataType.PTR)
        d = ast.nodes[4]
        assertTrue(d is VarDeclarationNode && d.name == "pp" && d.dataType == DataType.PRGPTR)
        d = ast.nodes[5]
        assertTrue(d is VarDeclarationNode && d.name == "a1" && d.dataType == DataType.BYTE)
        d = ast.nodes[6]
        assertTrue(d is VarDeclarationNode && d.name == "a2" && d.dataType == DataType.BYTE)
        d = ast.nodes[7]
        assertTrue(d is VarDeclarationNode && d.name == "a3" && d.dataType == DataType.BYTE)
    }

    @Test fun testSimpleGlobalArrayVar() {
        val ast = parse("""
            var buf: byte[64]
            var buf1, buf2: word[32]
        """.trimIndent())
        assertEquals(3, ast.nodes.size)
        val d1 = ast.nodes[0] as DataArrayDeclarationNode
        assertTrue(d1.name == "buf" && d1.dataType == DataType.BYTE)
        assertEquals(64, (d1.size as NumberNode).value)

        val d2 = ast.nodes[1] as DataArrayDeclarationNode
        assertTrue(d2.name == "buf1" && d2.dataType == DataType.WORD)
        assertEquals(32, (d2.size as NumberNode).value)

        val d3 = ast.nodes[2] as DataArrayDeclarationNode
        assertTrue(d3.name == "buf2" && d3.dataType == DataType.WORD)
        assertEquals(32, (d3.size as NumberNode).value)
    }

    @Test fun testPinDefinition() {
        val ast = parse("""
            pin led = A0
            pin buzzer = D2
        """.trimIndent())
        assertEquals(2, ast.nodes.size)
        val d1 = ast.nodes[0] as PinDeclarationNode
        assertTrue(d1.name == "led" && d1.devicePin == "A0")
        val d2 = ast.nodes[1] as PinDeclarationNode
        assertTrue(d2.name == "buzzer" && d2.devicePin == "D2")

    }

    @Test fun testSimpleProc() {
        val ast = parse("""
            proc myProc(param: r1) {
            }
        """.trimIndent())
        assertEquals(1, ast.nodes.size)
        assertTrue(ast.nodes[0] is ProcNode)
        val proc = ast.nodes[0] as ProcNode
        assertEquals("myProc", proc.name)
        assertEquals(1, proc.args.size)
        val arg = proc.args.first()
        assertEquals("param", arg.name)
        assertTrue(arg.expr is RegisterNode)
        assertEquals("r1", (arg.expr as RegisterNode).reg)
    }

    @Test fun testCodeWithoutProcError() {
        assertThrows<ParserException> {
            parse("""
            r21 = 1
        """.trimIndent())
        }
    }

    @Test fun testVectors() {
        val s = """
            vectors {
                TIMER2_COMP:
                    rjmp	timer0_comp
                default:
                    nop
            }
             """.trimIndent()
        val r = parse(s, false)
    }


    @Test fun testHelloWorld() {
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
    }

    @Test fun testGlobalDataStrings() {
        val s = """
            byte[] "some string"
            byte[] {"string1", "string2"}
            """
        val r = parse(s, false)
    }

    @Test fun testLocalDataStrings() {
        val s = """
            proc main() {
                byte[] "some string"
                byte[] {"string1", "string2"}
            }
            """
        val r = parse(s, false)
    }


    @Test fun testMultiIf() {
        val s = """
            proc main() {
                if (ZL == 1 || ZL == 7 || ZL == 8 || ZL == 14) goto clockwise
            }
        """.trimIndent()
        val r = parse(s, false)
    }


    @Test fun test0() {
        val s = """
            proc main() {
                r1 = (r2 << 3) + r3 - r4 - 5 + 2*2
            }
        """.trimIndent()
        val r = parse(s, false)
        /**
         * (ASSIGN, r1, (PLUS, (MINUS, (MINUS, (PLUS, (SHL, r2, 3), r3), r4), 5), (MUL, 2, 2)))
         * (ASSIGN, r1, (PLUS, (MINUS, (MINUS, (PLUS, (SHL, r2, 3), r3), r4), 5), 4))
         * (ASSIGN, r1, (MINUS, (MINUS, (PLUS, (SHL, r2, 3), r3), r4), 1))
         * r1 = ()
         *
         * binop(const, const) -> const
         * binop(+, binop(+, X, const1), const2) -> binop(+, X, const1+const2)
         */
    }

}

class TestFileLoaderStub(private val text: String) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        val stream = ByteArrayInputStream(text.toByteArray())
        return TokenStream(scanner.scan(path, stream).toMutableList(), path)
    }
}

fun parse(s: String, removeWhitespace: Boolean = false): AstNode {
    val stream = TestFileLoaderStub(s).loadSource("")
    if (removeWhitespace) {
        stream.remove { it is NewLineToken || it is LineCommentToken || it is BlockCommentToken }
    }
    return AvrParser(stream).parse()
}