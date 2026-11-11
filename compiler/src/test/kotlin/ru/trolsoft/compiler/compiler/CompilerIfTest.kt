package ru.trolsoft.compiler.compiler

import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import org.junit.jupiter.api.Test
import ru.trolsoft.therat.lang.Scanner
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*

class CompileIfTest {
    private fun node2str(node: Node): String {
        if (node is LabelNode) {
            return "${node.name}:"
        }
        if (node !is IfNode || node.thenBlock !is GotoNode) {
            throw RuntimeException("Unexpected node: $node")
        }
        val cond = node.cond
        val label = node.thenBlock.label

        if (cond is PrefixOperNode && cond.operator == Operator.LOGICAL_NOT) {
            return "if (!${cond.expression}) goto $label"
        }
        return "if ($cond) goto $label"

    }

    private fun processIfGoto(expr: String): List<String> {
        val buf = Buffer().apply { write(expr.toByteArray()) }
        val scanner = object : Scanner() {
            private val keywords = setOf("if", "else", "goto")
            override fun isKeyword(s: String): Boolean = keywords.contains(s)
        }
        val tokenStream = TokenStream(scanner.scan("", buf).toMutableList(), "")
        val parser = object : Parser(tokenStream) {
            override fun parse(): Node {
                return parsePrimary()
            }

            override fun parseFuncCallArgument(): Node = CommentNode(listOf())
        }
        buf.close()
        val out = mutableListOf<Node>()
        val ifNode = parser.parse() as IfNode
        decomposeIf(ifNode, "", out)
        val result = mutableListOf<String>()
        for (a in out) {
            result.add(node2str(a))
        }
        return result
    }

    @Test fun `trivia ifl`() {
        val r = processIfGoto("if (a) goto lbl")
        r shouldBe listOf("if (a) goto lbl")
    }

    @Test fun `trivial not`() {
        val r = processIfGoto("if (!a) goto lbl")
        r shouldBe listOf("if (!a) goto lbl")
    }

    @Test fun `simple add`() {
        val r = processIfGoto("if (a && b) goto lbl")
        r shouldBe listOf(
                "if (!a) goto @end",
                "if (b) goto lbl",
                "@end:"
        )
    }

    @Test fun `simple or`() {
        val r = processIfGoto("if (a || b) goto lbl")
        r shouldBe listOf(
                "if (a) goto lbl",
                "if (b) goto lbl"
        )
    }

    @Test fun `A and !B`() {
        val r = processIfGoto("if (a || !b) goto lbl")
        r shouldBe listOf(
                "if (a) goto lbl",
                "if (!b) goto lbl"
        )
    }

    @Test fun `A and (B or C)`() {
        val r = processIfGoto("if (a && (b || c)) goto lbl")
        r shouldBe listOf(
                "if (!a) goto @end",
                "if (b) goto lbl",
                "if (c) goto lbl",
                "@end:"
        )
    }

    @Test fun `(A and B) or (C and D)`() {
        val r = processIfGoto("if ((a && b) || (c && d)) goto lbl")
        r shouldBe listOf(
                "if (!a) goto @or1@end",
                "if (b) goto lbl",
                "@or1@end:",
                "if (!c) goto @or2@end",
                "if (d) goto lbl",
                "@or2@end:"
        )
    }

    @Test fun `A and B and C`() {
        val r = processIfGoto("if (a && b && c) goto lbl")
        r shouldBe listOf(
            "if (!a) goto @end",
            "if (!b) goto @end",
            "if (c) goto lbl",
            "@end:"
        )
    }

    @Test fun `simple brackets`() {
        val r = processIfGoto("if (((a))) goto lbl")
        r shouldBe listOf("if (a) goto lbl")
    }

    @Test fun `simple not not`() {
        val r = processIfGoto("if (!(!a)) goto lbl")
        r shouldBe listOf("if (a) goto lbl")
    }

}