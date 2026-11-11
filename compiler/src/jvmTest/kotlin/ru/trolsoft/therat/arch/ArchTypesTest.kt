package ru.trolsoft.therat.arch

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.Node
import kotlin.test.Test

class ArgNumberTest {

    @Test
    fun `ArgNumber should return correct value`() {
        val arg = ArgNumber(42)
        arg.value shouldBe 42
    }

    @Test
    fun `ArgNumber toString should return numeric string`() {
        ArgNumber(0).toString() shouldBe "0"
        ArgNumber(1).toString() shouldBe "1"
        ArgNumber(255).toString() shouldBe "255"
    }

    @Test
    fun `ArgNumber compareTo should work correctly`() {
        val arg = ArgNumber(10)
        arg.compareTo(10) shouldBe 0
        arg.compareTo(5) shouldBe 5
        arg.compareTo(15) shouldBe -5
    }

    @Test
    fun `ArgNumber with negative value`() {
        val arg = ArgNumber(-1)
        arg.value shouldBe -1
        arg.toString() shouldBe "-1"
        arg.compareTo(0) shouldBe -1
    }
}

class ArgNumberWithRadixTest {

    @Test
    fun `ArgNumberWithRadix zero should return 0 for any radix`() {
        ArgNumberWithRadix(0, 16).toString() shouldBe "0"
        ArgNumberWithRadix(0, 2).toString() shouldBe "0"
        ArgNumberWithRadix(0, 10).toString() shouldBe "0"
    }

    @Test
    fun `ArgNumberWithRadix hex radix should return 0x prefix`() {
        ArgNumberWithRadix(255, 16).toString() shouldBe "0xFF"
        ArgNumberWithRadix(16, 16).toString() shouldBe "0x10"
        ArgNumberWithRadix(10, 16).toString() shouldBe "0xA"
        ArgNumberWithRadix(0xAB, 16).toString() shouldBe "0xAB"
    }

    @Test
    fun `ArgNumberWithRadix binary radix should return 0b prefix`() {
        ArgNumberWithRadix(0, 2).toString() shouldBe "0"
        ArgNumberWithRadix(1, 2).toString() shouldBe "0b1"
        ArgNumberWithRadix(255, 2).toString() shouldBe "0b11111111"
        ArgNumberWithRadix(16, 2).toString() shouldBe "0b10000"
    }

    @Test
    fun `ArgNumberWithRadix decimal radix should return plain number`() {
        ArgNumberWithRadix(42, 10).toString() shouldBe "42"
        ArgNumberWithRadix(255, 10).toString() shouldBe "255"
    }

    @Test
    fun `ArgNumberWithRadix unknown radix should delegate to parent`() {
        ArgNumberWithRadix(42, 8).toString() shouldBe "42"
        ArgNumberWithRadix(42, 3).toString() shouldBe "42"
    }
}

class ArgLabelTest {

    private fun makeLabel(
        name: String = "myLabel",
        localName: String = "myLabel",
        resolver: (Int, Int) -> Int = { _, _ -> 0 }
    ) = ArgLabel(name, localName, SourceLocation("test.s", 1, 1), resolver)

    @Test
    fun `ArgLabel with same name and localName should return name`() {
        val label = makeLabel(name = "lbl", localName = "lbl")
        label.toString() shouldBe "lbl"
    }

    @Test
    fun `ArgLabel with localName ending with name should return name`() {
        val label = makeLabel(name = "main", localName = "_main")
        label.toString() shouldBe "_main ; main"
    }

    @Test
    fun `ArgLabel with different name and localName should return both`() {
        val label = makeLabel(name = "func", localName = "label1")
        label.toString() shouldBe "label1 ; func"
    }

    @Test
    fun `ArgLabel should store name and localName`() {
        val label = makeLabel(name = "myName", localName = "myLocal")
        label.name shouldBe "myName"
        label.localName shouldBe "myLocal"
    }

    @Test
    fun `ArgLabel should have SourceLocation`() {
        val location = SourceLocation("main.s", 5, 10)
        val label = ArgLabel("lbl", "lbl", location, { _, _ -> 0 })
        label.location shouldBe location
    }

    @Test
    fun `ArgLabel resolved should default to null`() {
        val label = makeLabel()
        label.resolved shouldBe null
    }

    @Test
    fun `ArgLabel resolved should be settable`() {
        val label = makeLabel()
        label.resolved = 42
        label.resolved shouldBe 42
    }

    @Test
    fun `ArgLabel resolver should be called`() {
        var called = false
        val label = ArgLabel("lbl", "lbl", SourceLocation("test.s", 1, 1), { current, target ->
            called = true
            current + target
        })
        val result = label.resolver(10, 20)
        called shouldBe true
        result shouldBe 30
    }
}

class DeferredNumberArgTest {

    private fun makeNode(): Node = object : Node {
        override fun getLocation(): SourceLocation = SourceLocation("test.s", 1, 1)
    }

    @Test
    fun `DeferredNumberArg with resolved value should return hex string`() {
        val arg = DeferredNumberArg(makeNode(), { _, _ -> 0 })
        arg.resolved = 255
        arg.toString() shouldBe "ff"
    }

    @Test
    fun `DeferredNumberArg with resolvedStr should return resolvedStr`() {
        val arg = DeferredNumberArg(makeNode(), { _, _ -> 0 })
        arg.resolvedStr = "abc"
        arg.toString() shouldBe "abc"
    }

    @Test
    fun `DeferredNumberArg with both resolved and resolvedStr should prefer resolved`() {
        val arg = DeferredNumberArg(makeNode(), { _, _ -> 16 })
        arg.resolved = 16
        arg.resolvedStr = "xyz"
        arg.toString() shouldBe "10"
    }

    @Test
    fun `DeferredNumberArg with no resolved should return node name`() {
        val arg = DeferredNumberArg(makeNode(), { _, _ -> 0 })
        arg.toString() shouldBe "<???>"
    }
}

class ArgExternTest {

    @Test
    fun `ArgExtern should return value`() {
        ArgExtern("PORTB").toString() shouldBe "PORTB"
        ArgExtern("0x20").toString() shouldBe "0x20"
    }

    @Test
    fun `ArgExternWithOffset positive offset should concatenate with +`() {
        ArgExternWithOffset("base", 0).toString() shouldBe "base+0"
        ArgExternWithOffset("base", 5).toString() shouldBe "base+5"
    }

    @Test
    fun `ArgExternWithOffset negative offset should concatenate without +`() {
        ArgExternWithOffset("base", -1).toString() shouldBe "base-1"
        ArgExternWithOffset("base", -10).toString() shouldBe "base-10"
    }
}

class AsmCallTest {

    private fun makeCall(cmd: AsmCmd = object : AsmCmd {
        override fun size(vararg args: AsmArg): Int = 1
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
        override fun str(vararg args: AsmArg): String = "test"
    }, args: List<AsmArg> = emptyList()) = AsmCall(cmd, args.toTypedArray(), SourceLocation("test.s", 1, 1))

    @Test
    fun `AsmCall should equal itself`() {
        val call = makeCall()
        call shouldBe call
    }

    @Test
    fun `AsmCall with same cmd and args should be equal`() {
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg): Int = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg): String = "test"
        }
        val arg = ArgNumber(1)
        val call1 = AsmCall(cmd, arrayOf(arg), SourceLocation("test.s", 1, 1))
        val call2 = AsmCall(cmd, arrayOf(arg), SourceLocation("test.s", 1, 1))
        call1 shouldBe call2
    }

    @Test
    fun `AsmCall with different cmd should not be equal`() {
        val cmd1 = object : AsmCmd {
            override fun size(vararg args: AsmArg): Int = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg): String = "a"
        }
        val cmd2 = object : AsmCmd {
            override fun size(vararg args: AsmArg): Int = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg): String = "b"
        }
        val call1 = AsmCall(cmd1, emptyArray(), SourceLocation("test.s", 1, 1))
        val call2 = AsmCall(cmd2, emptyArray(), SourceLocation("test.s", 1, 1))
        call1 shouldNotBe call2
    }

    @Test
    fun `AsmCall with different args should not be equal`() {
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg): Int = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg): String = "test"
        }
        val call1 = AsmCall(cmd, arrayOf(ArgNumber(1)), SourceLocation("test.s", 1, 1))
        val call2 = AsmCall(cmd, arrayOf(ArgNumber(2)), SourceLocation("test.s", 1, 1))
        call1 shouldNotBe call2
    }

    @Test
    fun `AsmCall with null should not be equal`() {
        val call = makeCall()
        (call as Any) shouldNotBe null
    }

    @Test
    fun `AsmCall with wrong type should not be equal`() {
        val call = makeCall()
        call shouldNotBe "not an AsmCall"
    }

    @Test
    fun `AsmCall hashCode should be consistent`() {
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg): Int = 1
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg): String = "test"
        }
        val arg = ArgNumber(1)
        val call1 = AsmCall(cmd, arrayOf(arg), SourceLocation("test.s", 1, 1))
        val call2 = AsmCall(cmd, arrayOf(arg), SourceLocation("test.s", 1, 1))
        call1.hashCode() shouldBe call2.hashCode()
    }

    @Test
    fun `AsmCall size should delegate to command size`() {
        val cmd = object : AsmCmd {
            override fun size(vararg args: AsmArg): Int = args.size * 2
            override fun compile(out: CompilerOutput, vararg args: AsmArg) {}
            override fun str(vararg args: AsmArg): String = "test"
        }
        val call = AsmCall(cmd, arrayOf(ArgNumber(1), ArgNumber(2)), SourceLocation("test.s", 1, 1))
        call.size() shouldBe 4
    }
}
