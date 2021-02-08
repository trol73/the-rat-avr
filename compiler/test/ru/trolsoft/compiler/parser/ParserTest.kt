package ru.trolsoft.compiler.parser

import org.junit.jupiter.api.Test
import ru.trolsoft.therat.avr.AvrScanner
import ru.trolsoft.therat.lang.IdentifierToken
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import java.io.ByteArrayInputStream
import kotlin.test.*


class TestFileLoaderStub(private val text: String) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        val stream = ByteArrayInputStream(text.toByteArray())
        return TokenStream(scanner.scan(path, stream).toMutableList(), path)
    }
}

private fun streamForString(s: String): TokenStream {
    return TestFileLoaderStub(s).loadSource("")
}

open class MyParser(s: String): Parser(streamForString(s)) {
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

    @Test fun testSimpleBinaryOp() {
        val r = MyParser("1 + a").parse()
        assertEquals(1, r.nodes.size)
        val node = r.nodes.first()
        assertTrue(node is BinaryOperNode)
        if (node is BinaryOperNode) {
            assertEquals(Operator.PLUS, node.operator)
            assertEquals(1, (node.left as NumberNode).value)
            assertEquals("a", (node.right as IdentifierNode).name)
        }
    }

    @Test fun testSimpleBinaryOpMinus() {
        val r = MyParser("a-1").parse()
        assertEquals(1, r.nodes.size)
        val node = r.nodes.first()
        assertTrue(node is BinaryOperNode)
        if (node is BinaryOperNode) {
            assertEquals(Operator.MINUS, node.operator)
            assertEquals("a", (node.left as IdentifierNode).name)
            assertEquals(1, (node.right as NumberNode).value)
        }
    }

    @Test fun testParenBinaryOp() {
        val r = MyParser("(1 + a)").parse()
        assertEquals(1, r.nodes.size)
        val node = r.nodes.first()
        assertTrue(node is BinaryOperNode)
        if (node is BinaryOperNode) {
            assertEquals(Operator.PLUS, node.operator)
            assertEquals(1, (node.left as NumberNode).value)
            assertEquals("a", (node.right as IdentifierNode).name)
        }
    }

    @Test fun testCompositeBinaryOp1() {
        val r = MyParser("1 + a * (2-b)").parse()
        assertEquals(1, r.nodes.size)
        val node = r.nodes.first()
        assertTrue(node is BinaryOperNode)
        if (node is BinaryOperNode) {
            assertEquals(Operator.PLUS, node.operator)
            assertEquals(1, (node.left as NumberNode).value)
            val node2 = node.right
            assertTrue(node2 is BinaryOperNode)
            if (node2 is BinaryOperNode) {
                assertEquals(Operator.MUL, node2.operator)
                assertEquals("a", (node2.left as IdentifierNode).name)
                val node3 = node2.right
                assertTrue(node3 is BinaryOperNode)
                if (node3 is BinaryOperNode) {
                    assertEquals(Operator.MINUS, node3.operator)
                    assertEquals(2, (node3.left as NumberNode).value)
                    assertEquals("b", (node3.right as IdentifierNode).name)
                }
            }
        }
    }


    @Test fun testCompositeBinaryOp2() {
        val r = MyParser("((1 + a) * (2<<b))").parse()
        assertEquals(1, r.nodes.size)
        val node = r.nodes.first()
        assertTrue(node is BinaryOperNode)
        if (node is BinaryOperNode) {
            assertEquals(Operator.MUL, node.operator)
            assertTrue(node.left is BinaryOperNode)
            assertTrue(node.right is BinaryOperNode)
            val left = node.left as BinaryOperNode
            val right = node.right as BinaryOperNode
            assertEquals(Operator.PLUS, left.operator)
            assertEquals(1, (left.left as NumberNode).value)
            assertEquals("a", (left.right as IdentifierNode).name)

            assertEquals(Operator.SHL, right.operator)
            assertEquals(2, (right.left as NumberNode).value)
            assertEquals("b", (right.right as IdentifierNode).name)
        }
    }

    @Test fun testMultipleAssign() {
        val r = MyParser("a = b = c").parse()
        assertEquals(1, r.nodes.size)
        val node = r.nodes.first() as BinaryOperNode
        assertEquals(Operator.ASSIGN, node.operator)
        assertEquals("a", (node.left as IdentifierNode).name)
        val node2 = node.right as BinaryOperNode
        assertEquals(Operator.ASSIGN, node2.operator)
        assertEquals("b", (node2.left as IdentifierNode).name)
        assertEquals("c", (node2.right as IdentifierNode).name)
    }

    @Test fun testMultipleRegistersAssign() {
        val r = MyParser("r20 = r21 = r22").parse()
        assertEquals(1, r.nodes.size)
        assertEquals(1, r.nodes.size)
        val node = r.nodes.first() as BinaryOperNode
        assertEquals(Operator.ASSIGN, node.operator)
        assertEquals("r20", (node.left as RegisterNode).reg)
        val node2 = node.right as BinaryOperNode
        assertEquals(Operator.ASSIGN, node2.operator)
        assertEquals("r21", (node2.left as RegisterNode).reg)
        assertEquals("r22", (node2.right as RegisterNode).reg)
    }

    @Test fun testSimplePrefix() {
        val r = MyParser("++x").parse()
        assertEquals(1, r.nodes.size)
        val node = r.nodes.first() as PrefixOperNode
        assertEquals(Operator.INC, node.operator)
        assertEquals("x", (node.expression as IdentifierNode).name)
    }

    @Test fun testSimpleSuffix() {
        val r = MyParser("y--").parse()
        assertEquals(1, r.nodes.size)
        val node = r.nodes.first() as SuffixOperNode
        assertEquals(Operator.DEC, node.operator)
        assertEquals("y", (node.expression as IdentifierNode).name)
    }

    @Test fun testSimpleIf() {
        val r = MyParser("if (r1 == 1) r1 = 0").parse()
        assertEquals(1, r.nodes.size)
        assertTrue(r.nodes.first() is IfNode)
        val node = r.nodes.first() as IfNode
        assertTrue(node.cond is BinaryOperNode)
        assertTrue(node.thenBlock is BinaryOperNode)
        assertEquals(null, node.elseBlock)

        val cond = node.cond as BinaryOperNode
        val then = node.thenBlock as BinaryOperNode
        assertEquals(Operator.EQ, cond.operator)
        assertEquals(Operator.ASSIGN, then.operator)
    }

    @Test fun testSimpleCall() {
        val r = MyParser("func(x, y, z)").parse()
        assertTrue(r.nodes.first() is FunctionCallNode)
        val node = r.nodes.first() as FunctionCallNode
        assertEquals("func", node.name)
        assertEquals(3, node.args.size)

        assertEquals("x", (node.args[0] as IdentifierNode).name)
        assertEquals("y", (node.args[1] as IdentifierNode).name)
        assertEquals("z", (node.args[2] as IdentifierNode).name)
    }

    @Test fun testAssembler2() {
        val r = MyParser("""
            ldi r1, 1
        """.trimIndent()).parse()
        assertTrue(r.nodes.first() is AssemblerInstrNode)
        val n = r.nodes.first() as AssemblerInstrNode
        assertEquals("ldi", n.instr)
        assertEquals(2, n.args!!.size)
        assertEquals("r1", (n.args!![0] as RegisterNode).reg)
        assertEquals(1, (n.args!![1] as NumberNode).value)
    }

    @Test fun testAssembler2noComma() {
        val parser = object : MyParser("""
            push r1 r2
        """.trimIndent()) {
            override fun asmRequiresComma(cmd: String, args: List<Node>): Boolean {
                val low = cmd.toLowerCase()
                return low != "push" && low != "pop"
            }
        }

        val r = parser.parse()
        assertTrue(r.nodes.first() is AssemblerInstrNode)
        val n = r.nodes.first() as AssemblerInstrNode
        assertEquals("push", n.instr)
        assertEquals(2, n.args!!.size)
        assertEquals("r1", (n.args!![0] as RegisterNode).reg)
        assertEquals("r2", (n.args!![1] as RegisterNode).reg)
    }

    @Test fun testAssembler1() {
        val r = MyParser("""
            push r2
        """.trimIndent()).parse()
        assertTrue(r.nodes.first() is AssemblerInstrNode)
        val n = r.nodes.first() as AssemblerInstrNode
        assertEquals("push", n.instr)
        assertEquals(1, n.args!!.size)
        assertEquals("r2", (n.args!![0] as RegisterNode).reg)

    }

    @Test fun testAssembler0() {
        val r = MyParser("""
            cli
        """.trimIndent()).parse()
        val n = r.nodes.first() as AssemblerInstrNode
        assertEquals("cli", n.instr)
        assertNull(n.args)
    }

    @Test fun testArrow() {
        val r = MyParser("""
            led_pin->PORT = 1
        """.trimIndent()).parse()
        val n = r.nodes.first() as BinaryOperNode
        assertEquals(Operator.ASSIGN, n.operator)
        assertTrue(n.left is BinaryOperNode)
        assertTrue(n.right is NumberNode)
        val left = n.left as BinaryOperNode
        assertEquals(Operator.ARROW_RIGHT, left.operator)
        assertEquals("PORT", (left.right as IdentifierNode).name)
        assertEquals("led_pin", (left.left as IdentifierNode).name)
        assertEquals(1, (n.right as NumberNode).value)
    }

    @Test fun testMultiAssigns() {
        val r = MyParser("""
                SPH = r16 = high(RAMEND)
        """.trimIndent()).parse()

        val n = r.nodes.first() as BinaryOperNode
        assertEquals(Operator.ASSIGN, n.operator)
        assertTrue(n.left is IdentifierNode)
        assertTrue(n.right is BinaryOperNode)
        val right = n.right as BinaryOperNode
        assertEquals(Operator.ASSIGN, right.operator)
        assertEquals("SPH", (n.left as IdentifierNode).name)
        assertTrue(right.right is FunctionCallNode)
    }

    @Test fun testMultiGroupAssigns() {
        val r = MyParser("""
                SPH = r16.r15 = 0x1234
        """.trimIndent()).parse()
        val n = r.nodes.first() as BinaryOperNode
        assertEquals(Operator.ASSIGN, n.operator)
        assertEquals("SPH", (n.left as IdentifierNode).name)
        val right = n.right as BinaryOperNode
        assertEquals(Operator.ASSIGN, right.operator)
        assertTrue(right.left is RegisterGroupNode)
        assertEquals(0x1234, (right.right as NumberNode).value)
    }


    @Test fun testInverseArrow() {
        val r = MyParser("""
                !port->pin_name
        """.trimIndent()).parse()
        val n = r.nodes.first() as PrefixOperNode
        assertEquals(Operator.LOGICAL_NOT, n.operator)
        val e = n.expression as BinaryOperNode
        assertEquals(Operator.ARROW_RIGHT, e.operator)
        assertEquals("port", (e.left as IdentifierNode).name)
        assertEquals("pin_name", (e.right as IdentifierNode).name)
    }



}