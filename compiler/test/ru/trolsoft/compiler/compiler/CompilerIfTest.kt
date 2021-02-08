package ru.trolsoft.compiler.compiler

import org.junit.jupiter.api.Test
import ru.trolsoft.therat.lang.Scanner
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import java.io.ByteArrayInputStream
import java.lang.RuntimeException
import kotlin.test.assertEquals

class CompileIfTest {
    private fun node2str(node: Node): String {
        if (node is LabelNode) {
            return "${node.name}:"
        }
        if (node !is IfNode || node.thenBlock !is GotoNode) {
            throw RuntimeException("Unexpected node: $node")
        }
        val cond = node.cond
        val label = (node.thenBlock as GotoNode).label

        if (cond is PrefixOperNode && cond.operator == Operator.LOGICAL_NOT) {
            return "if (!${cond.expression}) goto $label"
        }
        return "if ($cond) goto $label"

    }

    private fun processIfGoto(expr: String): List<String> {
        val stream = ByteArrayInputStream(expr.toByteArray())
        val scanner = object : Scanner() {
            private val keywords = setOf("if", "else", "goto")
            override fun isKeyword(s: String): Boolean = keywords.contains(s)
        }
        val tokenStream = TokenStream(scanner.scan("", stream).toMutableList(), "")
        val parser = object : Parser(tokenStream) {
            override fun parse(): Node {
                return parsePrimary()
            }

            override fun parseFuncCallArgument(): Node = CommentNode(listOf())

        }
        val out = mutableListOf<Node>()
        val ifNode = parser.parse() as IfNode
        decomposeIf(ifNode, "", out)
        val result = mutableListOf<String>()
        for (a in out) {
            result.add(node2str(a))
        }
        return result
    }

    @Test fun testTrivial() {
        val r = processIfGoto("if (a) goto lbl")
        assertEquals(listOf("if (a) goto lbl"), r)
    }

    @Test fun testTrivialNot() {
        val r = processIfGoto("if (!a) goto lbl")
        assertEquals(listOf("if (!a) goto lbl"), r)
    }

    @Test fun testSimpleAdd() {
        val r = processIfGoto("if (a && b) goto lbl")
        assertEquals(listOf(
                "if (!a) goto @end",
                "if (b) goto lbl",
                "@end:"
        ), r)
    }

    @Test fun testSimpleOr() {
        val r = processIfGoto("if (a || b) goto lbl")
        assertEquals(listOf(
                "if (a) goto lbl",
                "if (b) goto lbl"
        ), r)
    }

    @Test fun testAAndNotB() {
        val r = processIfGoto("if (a || !b) goto lbl")
        assertEquals(listOf(
                "if (a) goto lbl",
                "if (!b) goto lbl"
        ), r)
    }

    @Test fun testAAndBOrC() {
        val r = processIfGoto("if (a && (b || c)) goto lbl")
        assertEquals(listOf(
                "if (!a) goto @end",
                "if (b) goto lbl",
                "if (c) goto lbl",
                "@end:"
        ), r)
    }

    @Test fun testAAndBOrCAndD() {
        val r = processIfGoto("if ((a && b) || (c && d)) goto lbl")
        assertEquals(listOf(
                "if (!a) goto @or1@end",
                "if (b) goto lbl",
                "@or1@end:",
                "if (!c) goto @or2@end",
                "if (d) goto lbl",
                "@or2@end:"
        ), r)
    }

    @Test fun testAAndBAndC() {
        val r = processIfGoto("if (a && b && c) goto lbl")
        assertEquals(listOf(
            "if (!a) goto @end",
            "if (!b) goto @end",
            "if (c) goto lbl",
            "@end:"
        ), r)
    }

    @Test fun testSimpleBrackets() {
        val r = processIfGoto("if (((a))) goto lbl")
        assertEquals(listOf("if (a) goto lbl"), r)
    }

    @Test fun testSimpleNotNot() {
        val r = processIfGoto("if (!(!a)) goto lbl")
        assertEquals(listOf("if (a) goto lbl"), r)
    }

}