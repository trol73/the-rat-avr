package ru.trolsoft.compiler.parser

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import ru.trolsoft.compiler.lexer.SourceLocation
import kotlin.test.Test

class NodeTest {

    private val loc = SourceLocation("test.s", 1, 1)

    // ============ AstNode ============

    @Test
    fun `AstNode should return location of first node`() {
        val child = IdentifierNode(loc, "x")
        val parent = AstNode(listOf(child))
        parent.getLocation() shouldBe loc
    }

    @Test
    fun `AstNode should return empty location when no children`() {
        val parent = AstNode(emptyList())
        parent.getLocation() shouldBe SourceLocation("", 0, 0)
    }

    @Test
    fun `AstNode should support index access`() {
        val children = listOf(
            IdentifierNode(loc, "a"),
            IdentifierNode(loc, "b"),
            IdentifierNode(loc, "c")
        )
        val parent = AstNode(children)
        parent[0] shouldBe children[0]
        parent[1] shouldBe children[1]
        parent[2] shouldBe children[2]
    }

    @Test
    fun `AstNode size should return number of children`() {
        AstNode(emptyList()).size() shouldBe 0
        AstNode(listOf(IdentifierNode(loc, "x"))).size() shouldBe 1
        AstNode(listOf(IdentifierNode(loc, "a"), IdentifierNode(loc, "b"))).size() shouldBe 2
    }

    @Test
    fun `AstNode iterator should iterate over children`() {
        val children = listOf(
            IdentifierNode(loc, "x"),
            IdentifierNode(loc, "y")
        )
        val parent = AstNode(children)
        val items: List<Node> = parent.nodes.toList()
        items.shouldHaveSize(2)
        items[0] shouldBe (children[0] as Node)
        items[1] shouldBe (children[1] as Node)
    }

    @Test
    fun `AstNode toString should concatenate children`() {
        val children = listOf(
            IdentifierNode(loc, "a"),
            IdentifierNode(loc, "b")
        )
        val parent = AstNode(children)
        val str = parent.toString()
        str.contains("a") shouldBe true
        str.contains("b") shouldBe true
    }

    // ============ IdentifierNode ============

    @Test
    fun `IdentifierNode should store name and location`() {
        val node = IdentifierNode(loc, "myVar")
        node.name shouldBe "myVar"
        node.getLocation() shouldBe loc
    }

    @Test
    fun `IdentifierNode toString should return name`() {
        IdentifierNode(loc, "foo").toString() shouldBe "foo"
    }

    // ============ NumberNode ============

    @Test
    fun `NumberNode should store value and radix`() {
        val node = NumberNode(loc, 42, 10)
        node.value shouldBe 42
        node.radix shouldBe 10
    }

    @Test
    fun `NumberNode default radix should be 10`() {
        NumberNode(loc, 42).radix shouldBe 10
    }

    @Test
    fun `NumberNode toString decimal`() {
        NumberNode(loc, 255, 10).toString() shouldBe "255"
    }

    @Test
    fun `NumberNode toString hex`() {
        NumberNode(loc, 255, 16).toString() shouldBe "0xff"
    }

    @Test
    fun `NumberNode toString binary`() {
        NumberNode(loc, 0b1010, 2).toString() shouldBe "0b1010"
    }

    @Test
    fun `NumberNode toString octal`() {
        NumberNode(loc, 8, 8).toString() shouldBe "010"
    }

    @Test
    fun `NumberNode negative value`() {
        val node = NumberNode(loc, -100, 10)
        node.value shouldBe -100
    }

    // ============ RegisterNode ============

    @Test
    fun `RegisterNode should store register name`() {
        val node = RegisterNode(loc, "r1")
        node.reg shouldBe "r1"
    }

    @Test
    fun `RegisterNode toString should return formatted name`() {
        RegisterNode(loc, "r20").toString() shouldBe "reg#r20"
    }

    // ============ RegisterGroupNode ============

    @Test
    fun `RegisterGroupNode should store register list`() {
        val node = RegisterGroupNode(loc, listOf("r16", "r17"))
        node.size() shouldBe 2
        node[0] shouldBe "r16"
        node[1] shouldBe "r17"
    }

    @Test
    fun `RegisterGroupNode getReg should return register at index`() {
        val node = RegisterGroupNode(loc, listOf("r16", "r17", "r18"))
        node.getReg(0) shouldBe RegisterNode(loc, "r16")
        node.getReg(1) shouldBe RegisterNode(loc, "r17")
    }

    @Test
    fun `RegisterGroupNode highReg should return first register`() {
        val node = RegisterGroupNode(loc, listOf("r16", "r17"))
        node.highReg() shouldBe RegisterNode(loc, "r16")
    }

    @Test
    fun `RegisterGroupNode lowReg should return last register`() {
        val node = RegisterGroupNode(loc, listOf("r16", "r17"))
        node.lowReg() shouldBe RegisterNode(loc, "r17")
    }

    @Test
    fun `RegisterGroupNode toString should join with dots`() {
        RegisterGroupNode(loc, listOf("r16", "r17", "r18")).toString() shouldBe "r16.r17.r18"
    }

    @Test
    fun `RegisterGroupNode equals should compare by content`() {
        val node1 = RegisterGroupNode(loc, listOf("r16", "r17"))
        val node2 = RegisterGroupNode(loc, listOf("r16", "r17"))
        val node3 = RegisterGroupNode(loc, listOf("r16", "r18"))
        node1 shouldBe node2
        node1 shouldNotBe node3
    }

    @Test
    fun `RegisterGroupNode equals with different size should return false`() {
        val node1 = RegisterGroupNode(loc, listOf("r16"))
        val node2 = RegisterGroupNode(loc, listOf("r16", "r17"))
        node1 shouldNotBe node2
    }

    @Test
    fun `RegisterGroupNode equals with non-RegisterGroupNode should return false`() {
        val node = RegisterGroupNode(loc, listOf("r16"))
        node shouldNotBe "not a register group"
    }

    // ============ DotGroupNode ============

    @Test
    fun `DotGroupNode should store items list`() {
        val items = listOf(
            RegisterNode(loc, "r16"),
            IdentifierNode(loc, "foo")
        )
        val node = DotGroupNode(items)
        node.items shouldBe items
        node.getLocation() shouldBe loc
    }

    // ============ PrefixOperNode ============

    @Test
    fun `PrefixOperNode should store operator and expression`() {
        val expr = IdentifierNode(loc, "x")
        val node = PrefixOperNode(loc, Operator.INC, expr)
        node.operator shouldBe Operator.INC
        node.expression shouldBe expr
    }

    // ============ SuffixOperNode ============

    @Test
    fun `SuffixOperNode should store operator and expression`() {
        val expr = IdentifierNode(loc, "y")
        val node = SuffixOperNode(Operator.DEC, expr)
        node.operator shouldBe Operator.DEC
        node.expression shouldBe expr
        node.getLocation() shouldBe loc
    }

    // ============ BinaryOperNode ============

    @Test
    fun `BinaryOperNode should store operator and operands`() {
        val left = IdentifierNode(loc, "a")
        val right = NumberNode(loc, 1)
        val node = BinaryOperNode(loc, Operator.PLUS, left, right)
        node.operator shouldBe Operator.PLUS
        node.left shouldBe left
        node.right shouldBe right
    }

    // ============ IfNode ============

    @Test
    fun `IfNode should store condition then and else blocks`() {
        val cond = BinaryOperNode(loc, Operator.EQ, IdentifierNode(loc, "x"), NumberNode(loc, 1))
        val thenBlock = IdentifierNode(loc, "a")
        val elseBlock = IdentifierNode(loc, "b")
        val node = IfNode(loc, cond, thenBlock, elseBlock)
        node.cond shouldBe cond
        node.thenBlock shouldBe thenBlock
        node.elseBlock shouldBe elseBlock
    }

    @Test
    fun `IfNode with null else block should have empty else`() {
        val cond = IdentifierNode(loc, "x")
        val thenBlock = IdentifierNode(loc, "a")
        val node = IfNode(loc, cond, thenBlock, null)
        node.hasEmptyElseBlock() shouldBe true
        node.hasNotEmptyElseBlock() shouldBe false
    }

    @Test
    fun `IfNode with empty then block`() {
        val cond = IdentifierNode(loc, "x")
        val thenBlock = CompoundStmt(emptyList())
        val node = IfNode(loc, cond, thenBlock, null)
        node.hasEmptyThenBlock() shouldBe true
        node.hasNotEmptyThenBlock() shouldBe false
    }

    @Test
    fun `IfNode with non-empty then block`() {
        val cond = IdentifierNode(loc, "x")
        val thenBlock = CompoundStmt(listOf(IdentifierNode(loc, "a")))
        val node = IfNode(loc, cond, thenBlock, null)
        node.hasEmptyThenBlock() shouldBe false
        node.hasNotEmptyThenBlock() shouldBe true
    }

    // ============ CompoundStmt ============

    @Test
    fun `CompoundStmt should store nodes list`() {
        val nodes = listOf(IdentifierNode(loc, "a"), IdentifierNode(loc, "b"))
        val stmt = CompoundStmt(nodes)
        stmt.nodes shouldBe nodes
        stmt.size() shouldBe 2
    }

    @Test
    fun `CompoundStmt empty should be empty`() {
        CompoundStmt(emptyList()).isEmpty() shouldBe true
    }

    @Test
    fun `CompoundStmt with nodes should not be empty`() {
        CompoundStmt(listOf(IdentifierNode(loc, "x"))).isEmpty() shouldBe false
    }

    @Test
    fun `CompoundStmt first should return first node`() {
        val nodes = listOf(IdentifierNode(loc, "first"), IdentifierNode(loc, "second"))
        CompoundStmt(nodes).first() shouldBe nodes[0]
    }

    @Test
    fun `CompoundStmt last should return last node`() {
        val nodes = listOf(IdentifierNode(loc, "first"), IdentifierNode(loc, "second"))
        CompoundStmt(nodes).last() shouldBe nodes[1]
    }

    @Test
    fun `CompoundStmt last with filter should return filtered last`() {
        val nodes = listOf(
            IdentifierNode(loc, "a"),
            RegisterNode(loc, "r1"),
            IdentifierNode(loc, "b")
        )
        val stmt = CompoundStmt(nodes)
        val last = stmt.last { it is RegisterNode }
        last shouldBe nodes[1]
    }

    @Test
    fun `CompoundStmt index access`() {
        val nodes = listOf(
            IdentifierNode(loc, "a"),
            IdentifierNode(loc, "b")
        )
        val stmt = CompoundStmt(nodes)
        stmt[0] shouldBe nodes[0]
        stmt[1] shouldBe nodes[1]
    }

    @Test
    fun `CompoundStmt iterator`() {
        val nodes = listOf(
            IdentifierNode(loc, "a"),
            IdentifierNode(loc, "b")
        )
        val stmt = CompoundStmt(nodes)
        stmt.nodes shouldBe nodes
    }

    // ============ FunctionCallNode ============

    @Test
    fun `FunctionCallNode should store name and args`() {
        val args = listOf(IdentifierNode(loc, "x"), NumberNode(loc, 1))
        val node = FunctionCallNode(loc, "func", args)
        node.name shouldBe "func"
        node.args shouldBe args
    }

    @Test
    fun `FunctionCallNode with empty args`() {
        val node = FunctionCallNode(loc, "empty", emptyList())
        node.args shouldBe emptyList()
    }

    // ============ AssemblerInstrNode ============

    @Test
    fun `AssemblerInstrNode should store instruction and args`() {
        val args = listOf(RegisterNode(loc, "r1"), NumberNode(loc, 1))
        val node = AssemblerInstrNode(loc, "ldi", args)
        node.instr shouldBe "ldi"
        node.args shouldBe args
    }

    @Test
    fun `AssemblerInstrNode with null args`() {
        val node = AssemblerInstrNode(loc, "cli", null)
        node.instr shouldBe "cli"
        node.args shouldBe null
    }

    // ============ GotoNode ============

    @Test
    fun `GotoNode should store label name`() {
        val node = GotoNode(loc, "myLabel")
        node.label shouldBe "myLabel"
    }

    // ============ LoopNode ============

    @Test
    fun `LoopNode should store condition and body`() {
        val cond = IdentifierNode(loc, "x")
        val body = CompoundStmt(listOf(IdentifierNode(loc, "a")))
        val node = LoopNode(loc, cond, body)
        node.cond shouldBe cond
        node.body shouldBe body
    }

    @Test
    fun `LoopNode with null condition`() {
        val body = CompoundStmt(listOf(IdentifierNode(loc, "a")))
        val node = LoopNode(loc, null, body)
        node.cond shouldBe null
    }

    // ============ DoWhileNode ============

    @Test
    fun `DoWhileNode should store body and condition`() {
        val body = CompoundStmt(listOf(IdentifierNode(loc, "a")))
        val cond = IdentifierNode(loc, "x")
        val node = DoWhileNode(loc, body, cond)
        node.body shouldBe body
        node.cond shouldBe cond
    }

    // ============ BreakNode and ContinueNode ============

    @Test
    fun `BreakNode should have location`() {
        BreakNode(loc).getLocation() shouldBe loc
    }

    @Test
    fun `ContinueNode should have location`() {
        ContinueNode(loc).getLocation() shouldBe loc
    }

    // ============ Indexed nodes ============

    @Test
    fun `IndexedNode should store name and index`() {
        val name = IdentifierNode(loc, "arr")
        val index = NumberNode(loc, 0)
        val node = IndexedNode(name, index)
        node.name shouldBe name
        node.index shouldBe index
        node.getLocation() shouldBe loc
    }

    @Test
    fun `IndexedRegNode should store register and index`() {
        val reg = RegisterNode(loc, "r16")
        val index = NumberNode(loc, 0)
        val node = IndexedRegNode(reg, index)
        node.reg shouldBe reg
        node.index shouldBe index
        node.getLocation() shouldBe loc
    }

    @Test
    fun `IndexedNumNode should store number and index`() {
        val num = NumberNode(loc, 10)
        val index = IdentifierNode(loc, "i")
        val node = IndexedNumNode(num, index)
        node.num shouldBe num
        node.index shouldBe index
        node.getLocation() shouldBe loc
    }

    // ============ CharLiteralNode and StringLiteralNode ============

    @Test
    fun `CharLiteralNode should store char`() {
        val node = CharLiteralNode(loc, 'A')
        node.char shouldBe 'A'
        node.toString() shouldBe "'A'"
    }

    @Test
    fun `StringLiteralNode should store string`() {
        val node = StringLiteralNode(loc, "hello")
        node.str shouldBe "hello"
        node.toString() shouldBe "\"hello\""
    }

    // ============ LabelNode ============

    @Test
    fun `LabelNode should store name and global flag`() {
        LabelNode(loc, "lbl").name shouldBe "lbl"
        LabelNode(loc, "lbl", true).global shouldBe true
        LabelNode(loc, "lbl", false).global shouldBe false
    }

    // ============ SignedCastNode ============

    @Test
    fun `SignedCastNode should wrap expression`() {
        val expr = IdentifierNode(loc, "x")
        val node = SignedCastNode(loc, expr)
        node.expr shouldBe expr
    }

    // ============ SaveregsNode ============

    @Test
    fun `SaveregsNode should store args and body`() {
        val args = listOf(IdentifierNode(loc, "r16"))
        val body = CompoundStmt(listOf(IdentifierNode(loc, "a")))
        val node = SaveregsNode(loc, args, body)
        node.args shouldBe args
        node.body shouldBe body
    }

    // ============ tryUnpackCompoundStmt ============

    @Test
    fun `tryUnpackCompoundStmt with single element should return element`() {
        val node = IdentifierNode(loc, "x")
        val compound = CompoundStmt(listOf(node))
        tryUnpackCompoundStmt(compound) shouldBe node
    }

    @Test
    fun `tryUnpackCompoundStmt with multiple elements should return compound`() {
        val nodes = listOf(IdentifierNode(loc, "a"), IdentifierNode(loc, "b"))
        val compound = CompoundStmt(nodes)
        tryUnpackCompoundStmt(compound) shouldBe compound
    }

    @Test
    fun `tryUnpackCompoundStmt with non-compound should return as-is`() {
        val node = IdentifierNode(loc, "x")
        tryUnpackCompoundStmt(node) shouldBe node
    }
}
