package ru.trolsoft.compiler.parser

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.io.Buffer
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.therat.avr.AvrScanner
import ru.trolsoft.therat.lang.IdentifierToken

class TestFileLoaderStub(private val text: String) : ru.trolsoft.compiler.lexer.FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        val buf = Buffer().apply { write(text.toByteArray()) }
        return TokenStream(scanner.scan(path, buf).toMutableList(), path)
    }
}

private fun streamForString(s: String): TokenStream =
    TestFileLoaderStub(s).loadSource("")

open class TestParser(s: String) : Parser(streamForString(s)) {
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

class ParserTestBase {

    // ============ nextIdentifier tests ============

    @Test
    fun `nextIdentifier should return identifier token`() {
        val parser = object : TestParser("myVar") {
            fun testNextIdentifier(): IdentifierToken = nextIdentifier()
        }
        val token = parser.testNextIdentifier()
        token.name shouldBe "myVar"
    }

    // ============ nextNumber tests ============

    @Test
    fun `nextNumber should return number token`() {
        val parser = object : TestParser("42") {
            fun testNextNumber() = nextNumber()
        }
        val token = parser.testNextNumber()
        token.value shouldBe 42
    }

    // ============ checkColon tests ============

    @Test
    fun `checkColon should consume colon token`() {
        val parser = object : TestParser(":") {
            fun testCheckColon() = checkColon()
        }
        // Should not throw
        parser.testCheckColon()
    }

    // ============ checkBracket tests ============

    @Test
    fun `checkBracket should consume matching bracket`() {
        val parser = object : TestParser("(x)") {
            fun testCheckOpen() = checkBracket('(')
        }
        parser.testCheckOpen()
    }

    @Test
    fun `checkBracket should consume closing bracket`() {
        val parser = object : TestParser(")") {
            fun testCheckClose() = checkBracket(')')
        }
        parser.testCheckClose()
    }

    // ============ nextRegister tests ============

    @Test
    fun `nextRegister should return register token`() {
        val parser = object : TestParser("r1") {
            fun testNextRegister() = nextRegister()
        }
        val token = parser.testNextRegister()
        token.name shouldBe "r1"
    }

    // ============ nextBracket tests ============

    @Test
    fun `nextBracket should consume matching bracket`() {
        val parser = object : TestParser("[") {
            fun testNextBracket() = nextBracket('[')
        }
        parser.testNextBracket()
    }

    // ============ parsePrimary tests ============

    @Test
    fun `parsePrimary with identifier should return IdentifierNode`() {
        val r = TestParser("myVar").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<IdentifierNode>().name shouldBe "myVar"
    }

    @Test
    fun `parsePrimary with number should return NumberNode`() {
        val r = TestParser("123").parse()
        r.nodes shouldHaveSize 1
        val num = r.nodes.first().shouldBeTypeOf<NumberNode>()
        num.value shouldBe 123
        num.radix shouldBe 10
    }

    @Test
    fun `parsePrimary with hex number should return NumberNode with radix 16`() {
        val r = TestParser("0xFF").parse()
        r.nodes shouldHaveSize 1
        val num = r.nodes.first().shouldBeTypeOf<NumberNode>()
        num.value shouldBe 0xFF
        num.radix shouldBe 16
    }

    @Test
    fun `parsePrimary with register should return RegisterNode`() {
        val r = TestParser("r20").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<RegisterNode>().reg shouldBe "r20"
    }

    @Test
    fun `parsePrimary with parenthesized expression should return expression`() {
        val r = TestParser("(1 + 2)").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<BinaryOperNode>().operator shouldBe Operator.PLUS
    }

    @Test
    fun `parsePrimary with negative number should return negative NumberNode`() {
        val r = TestParser("-10").parse()
        r.nodes shouldHaveSize 1
        val num = r.nodes.first().shouldBeTypeOf<NumberNode>()
        num.value shouldBe -10
    }

    @Test
    fun `parsePrimary with array index should return IndexedNumNode`() {
        val r = TestParser("arr[0]").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<IndexedNode>()
    }

    @Test
    fun `parsePrimary with register array index should return IndexedRegNode`() {
        val r = TestParser("r20[1]").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<IndexedRegNode>()
    }

    // ============ parseExpression tests ============

    @Test
    fun `parseExpression with simple identifier should return identifier`() {
        val r = TestParser("x").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<IdentifierNode>().name shouldBe "x"
    }

    @Test
    fun `parseExpression with comparison should return BinaryOperNode`() {
        val r = TestParser("a == b").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.EQ
    }

    @Test
    fun `parseExpression with logical and should return BinaryOperNode`() {
        val r = TestParser("a && b").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.LOGICAL_AND
    }

    @Test
    fun `parseExpression with logical or should return BinaryOperNode`() {
        val r = TestParser("a || b").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.LOGICAL_OR
    }

    @Test
    fun `parseExpression with shift should return BinaryOperNode`() {
        val r = TestParser("x << 1").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.SHL
    }

    @Test
    fun `parseExpression with bitwise and should return BinaryOperNode`() {
        val r = TestParser("x & 0xFF").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.AND
    }

    @Test
    fun `parseExpression with bitwise or should return BinaryOperNode`() {
        val r = TestParser("x | 0x01").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.OR
    }

    @Test
    fun `parseExpression with bitwise xor should return BinaryOperNode`() {
        val r = TestParser("x ^ 0xFF").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.XOR
    }

    @Test
    fun `parseExpression with comparison less than should return BinaryOperNode`() {
        val r = TestParser("a < b").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.LESS
    }

    @Test
    fun `parseExpression with comparison greater than should return BinaryOperNode`() {
        val r = TestParser("a > b").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.GREAT
    }

    @Test
    fun `parseExpression with comparison less equal should return BinaryOperNode`() {
        val r = TestParser("a <= b").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.LESS_EQ
    }

    @Test
    fun `parseExpression with comparison greater equal should return BinaryOperNode`() {
        val r = TestParser("a >= b").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.GREAT_EQ
    }

    @Test
    fun `parseExpression with not equal should return BinaryOperNode`() {
        val r = TestParser("a != b").parse()
        r.nodes shouldHaveSize 1
        val bin = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        bin.operator shouldBe Operator.NOT_EQ
    }

    // ============ Operator precedence tests ============

    @Test
    fun `parseExpression should respect operator precedence multiplication over addition`() {
        val r = TestParser("a + b * c").parse()
        r.nodes shouldHaveSize 1
        val plus = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        plus.operator shouldBe Operator.PLUS
        with (plus.right.shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.MUL
        }
    }

    @Test
    fun `parseExpression should handle parentheses`() {
        val r = TestParser("(a + b) * c").parse()
        r.nodes shouldHaveSize 1
        val mul = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        mul.operator shouldBe Operator.MUL
        with (mul.left.shouldBeTypeOf<BinaryOperNode>()) {
            operator shouldBe Operator.PLUS
        }
    }

    // ============ parseFunctionCall tests ============

    @Test
    fun `parsePrimary with function call should return FunctionCallNode`() {
        val r = TestParser("func(x)").parse()
        r.nodes shouldHaveSize 1
        val call = r.nodes.first().shouldBeTypeOf<FunctionCallNode>()
        call.name shouldBe "func"
        call.args shouldHaveSize 1
    }

    @Test
    fun `parsePrimary with function call multiple args should return FunctionCallNode`() {
        val r = TestParser("func(a, b, c)").parse()
        r.nodes shouldHaveSize 1
        val call = r.nodes.first().shouldBeTypeOf<FunctionCallNode>()
        call.name shouldBe "func"
        call.args shouldHaveSize 3
    }

    @Test
    fun `parsePrimary with function call no args should return FunctionCallNode`() {
        val r = TestParser("empty()").parse()
        r.nodes shouldHaveSize 1
        val call = r.nodes.first().shouldBeTypeOf<FunctionCallNode>()
        call.name shouldBe "empty"
        call.args shouldHaveSize 0
    }

    // ============ parseLoop tests ============

    @Test
    fun `parsePrimary with loop should return LoopNode`() {
        val r = TestParser("loop {}").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<LoopNode>()
    }

    @Test
    fun `parsePrimary with loop condition should return LoopNode`() {
        val r = TestParser("loop (x) { }").parse()
        r.nodes shouldHaveSize 1
        val loopNode = r.nodes.first().shouldBeTypeOf<LoopNode>()
        loopNode.cond.shouldNotBeNull()
    }

    // ============ parseIf tests ============

    @Test
    fun `parsePrimary with if should return IfNode`() {
        val r = TestParser("if (x) a").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<IfNode>()
    }

    @Test
    fun `parsePrimary with if else should return IfNode with else block`() {
        val r = TestParser("if (x) a else b").parse()
        r.nodes shouldHaveSize 1
        val ifNode = r.nodes.first().shouldBeTypeOf<IfNode>()
        ifNode.elseBlock.shouldNotBeNull()
    }

    // ============ parseGoto tests ============

    @Test
    fun `parsePrimary with goto should return GotoNode`() {
        val r = TestParser("goto myLabel").parse()
        r.nodes shouldHaveSize 1
        val goto = r.nodes.first().shouldBeTypeOf<GotoNode>()
        goto.label shouldBe "myLabel"
    }

    // ============ parseDoWhile tests ============

    @Test
    fun `parsePrimary with do while should return DoWhileNode`() {
        val r = TestParser("do { } while (x)").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<DoWhileNode>()
    }

    // ============ parseSaveregs tests ============

    @Test
    fun `parsePrimary with saveregs should return SaveregsNode`() {
        val r = TestParser("saveregs (r16) { }").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<SaveregsNode>()
    }

    // ============ Break and Continue tests ============

    @Test
    fun `parsePrimary with break should return BreakNode`() {
        val r = TestParser("break").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<BreakNode>()
    }

    @Test
    fun `parsePrimary with continue should return ContinueNode`() {
        val r = TestParser("continue").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<ContinueNode>()
    }

    // ============ Char and String literal tests ============

    @Test
    fun `parsePrimary with char literal should return CharLiteralNode`() {
        val r = TestParser("'A'").parse()
        r.nodes shouldHaveSize 1
        val lit = r.nodes.first().shouldBeTypeOf<CharLiteralNode>()
        lit.char shouldBe 'A'
    }

    @Test
    fun `parsePrimary with string literal should return StringLiteralNode`() {
        val r = TestParser("\"hello\"").parse()
        r.nodes shouldHaveSize 1
        val lit = r.nodes.first().shouldBeTypeOf<StringLiteralNode>()
        lit.str shouldBe "hello"
    }

    // ============ Label tests ============

    @Test
    fun `parsePrimary with label should return LabelNode`() {
        val r = TestParser("myLabel:").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<LabelNode>()
    }

    // ============ Signed cast tests ============

    @Test
    fun `parseExpression with signed cast should return SignedCastNode`() {
        val r = TestParser("signed x").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<SignedCastNode>()
    }

    // ============ Prefix operator tests ============

    @Test
    fun `parseExpression with prefix inc should return PrefixOperNode`() {
        val r = TestParser("++x").parse()
        r.nodes shouldHaveSize 1
        val prefix = r.nodes.first().shouldBeTypeOf<PrefixOperNode>()
        prefix.operator shouldBe Operator.INC
    }

    @Test
    fun `parseExpression with prefix dec should return PrefixOperNode`() {
        val r = TestParser("--x").parse()
        r.nodes shouldHaveSize 1
        val prefix = r.nodes.first().shouldBeTypeOf<PrefixOperNode>()
        prefix.operator shouldBe Operator.DEC
    }

    @Test
    fun `parseExpression with prefix not should return PrefixOperNode`() {
        val r = TestParser("!x").parse()
        r.nodes shouldHaveSize 1
        val prefix = r.nodes.first().shouldBeTypeOf<PrefixOperNode>()
        prefix.operator shouldBe Operator.LOGICAL_NOT
    }

    @Test
    fun `parseExpression with prefix binary not should return PrefixOperNode`() {
        val r = TestParser("~x").parse()
        r.nodes shouldHaveSize 1
        val prefix = r.nodes.first().shouldBeTypeOf<PrefixOperNode>()
        prefix.operator shouldBe Operator.BINARY_NOT
    }

    // ============ Suffix operator tests ============

    @Test
    fun `parseExpression with suffix inc should return SuffixOperNode`() {
        val r = TestParser("x++").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<SuffixOperNode>()
    }

    @Test
    fun `parseExpression with suffix dec should return SuffixOperNode`() {
        val r = TestParser("x--").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<SuffixOperNode>()
    }

    // ============ CompoundStmt tests ============

    @Test
    fun `parseCodeBlock should return CompoundStmt`() {
        val parser = object : TestParser("a\nb()") {
            fun testParseCodeBlock() = parseCodeBlock()
        }
        val stmt = parser.testParseCodeBlock()
        stmt.shouldBeTypeOf<CompoundStmt>()
        stmt.size() shouldBe 2
    }

    // ============ parseArraySize tests ============

    @Test
    fun `parseArraySize with brackets should return expression`() {
        val parser = object : TestParser("[10]") {
            fun testParseArraySize() = parseArraySize()
        }
        val size = parser.testParseArraySize()
        size.shouldNotBeNull()
    }

    @Test
    fun `parseArraySize without brackets should return null`() {
        val parser = object : TestParser("x") {
            fun testParseArraySize() = parseArraySize()
        }
        val size = parser.testParseArraySize()
        size shouldBe null
    }

    // ============ Identifier with group tests ============

    @Test
    fun `parsePrimary with dotted identifier should return DotGroupNode`() {
        val r = TestParser("port.pin").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<DotGroupNode>()
    }

    @Test
    fun `parsePrimary with indexed identifier should return IndexedNode`() {
        val r = TestParser("arr[idx]").parse()
        r.nodes shouldHaveSize 1
        r.nodes.first().shouldBeTypeOf<IndexedNode>()
    }

    // ============ Complex expression tests ============

    @Test
    fun `parseExpression with chained assignments should be right associative`() {
        val r = TestParser("a = b = c").parse()
        r.nodes shouldHaveSize 1
        val outer = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        outer.operator shouldBe Operator.ASSIGN
        // Right side should also be ASSIGN (right associative)
        outer.right.shouldBeTypeOf<BinaryOperNode>().operator shouldBe Operator.ASSIGN
    }

    @Test
    fun `parseExpression with mixed operators should respect precedence`() {
        val r = TestParser("a + b * c - d").parse()
        r.nodes shouldHaveSize 1
        // Top level should be MINUS (lowest precedence in chain)
        val top = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        top.operator shouldBe Operator.MINUS
    }

    @Test
    fun `parseExpression with comparison chain`() {
        val r = TestParser("a == b != c").parse()
        r.nodes shouldHaveSize 1
        // Should parse as chained comparisons
        r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
    }

    @Test
    fun `parseExpression with logical operators chain`() {
        val r = TestParser("a && b || c").parse()
        r.nodes shouldHaveSize 1
        // TOP should be LOGICAL_OR (lower precedence)
        val top = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        top.operator shouldBe Operator.LOGICAL_OR
    }

    @Test
    fun `parseExpression with bitwise and logical mix`() {
        val r = TestParser("a & b && c").parse()
        r.nodes shouldHaveSize 1
        // TOP should be LOGICAL_AND (lower precedence than AND)
        val top = r.nodes.first().shouldBeTypeOf<BinaryOperNode>()
        top.operator shouldBe Operator.LOGICAL_AND
    }
}
