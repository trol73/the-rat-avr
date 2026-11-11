package ru.trolsoft.therat.i8085.parser

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.VarDeclarationNode
import ru.trolsoft.therat.i8085.I8085AstNode
import ru.trolsoft.therat.i8085.I8085RatParser
import ru.trolsoft.therat.i8085.I8085Scanner
import kotlinx.io.Buffer
import ru.trolsoft.therat.lang.BlockCommentToken
import ru.trolsoft.therat.lang.LineCommentToken
import ru.trolsoft.therat.lang.NewLineToken

class I8085RatParserTest {
    @Test
    fun `global variables`() {
        val ast = parse(
            """
            var b1: byte
            var w1: word
            var dw1: dword
            var qw1: qword
            var a1, a2, a3: byte
        """.trimIndent())
        ast.nodes shouldHaveSize 7
        with (ast.nodes[0]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "b1"
            dataType shouldBe DataType.BYTE
        }
        with (ast.nodes[1]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "w1"
            dataType shouldBe DataType.WORD
        }
        with (ast.nodes[2]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "dw1"
            dataType shouldBe DataType.DWORD
        }
        with (ast.nodes[3]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "qw1"
            dataType shouldBe DataType.QWORD
        }
        with (ast.nodes[4]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "a1"
            dataType shouldBe DataType.BYTE
        }
        with (ast.nodes[5]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "a2"
            dataType shouldBe DataType.BYTE
        }
        with (ast.nodes[6]) {
            shouldBeInstanceOf<VarDeclarationNode>()
            name shouldBe "a3"
            dataType shouldBe DataType.BYTE
        }
    }
}

class TestFileLoaderStub(private val text: String) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = I8085Scanner()
        val buf = Buffer().apply { write(text.toByteArray()) }
        return TokenStream(scanner.scan(path, buf).toMutableList(), path)
    }
}
fun parse(s: String, removeWhitespace: Boolean = false): I8085AstNode {
    val stream = TestFileLoaderStub(s).loadSource("")
    if (removeWhitespace) {
        stream.remove { it is NewLineToken || it is LineCommentToken || it is BlockCommentToken }
    }
    return I8085RatParser(stream).parse()
}