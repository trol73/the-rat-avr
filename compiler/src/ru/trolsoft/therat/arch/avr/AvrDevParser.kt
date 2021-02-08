package ru.trolsoft.therat.arch.avr

import ru.trolsoft.therat.lang.*
import ru.trolsoft.compiler.lexer.CachedFileLoader
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*

private data class FakeNode(val loc: SourceLocation) : Node() {
    override fun getLocation(): SourceLocation = loc
}

class AvrDevParser(stream: TokenStream) : Parser(stream) {
    private var deviceName: String? = null
    private var progSize: Int = 0
    private var ramSize: Int = 0
    private var ramStart: Int = 0
    private var eepromSize: Int = 0
    private val interrupts = mutableListOf<Interrupt>()
    val registers = mutableListOf<IoRegister>()

    fun parseDevice(): AvrDevice {
        parse()

        return AvrDevice(deviceName!!, progSize, ramStart, ramSize, eepromSize, Interrupts(interrupts), registers)
    }

    override fun parse(): Node {
        while (!stream.eof()) {
            skipWhitespace()
            if (stream.eof()) {
                break
            }
            val t = nextToken()
            if (t !is IdentifierToken) {
                errorUnexpectedToken(t)
            }
            when (t.name) {
                "device" -> parseDeviceBlock()
                "interrupts" -> parseInterrupts()
                "registers" -> parseRegisters()
                else -> errorUnexpectedToken(t)
            }
        }
        return FakeNode(stream.getEofLocation())
    }


    private fun parseDeviceBlock(): Node {
        val startBracket = checkBracket('{')
        while (true) {
            skipWhitespace()
            val t = nextToken()
            if (t.isBracket('}')) {
                break
            } else if (t !is IdentifierToken) {
                errorIdentifierExpected(t)
            }
            checkOperator("=")
            val value = nextToken()
            if (value !is StringLiteralToken && value !is NumberToken) {
                errorUnexpectedToken(value)
            }
            parseProperty(t.name, value)
        }
        return FakeNode(startBracket.location)
    }

    private fun parseProperty(name: String, value: Token) {
        when (name) {
            "name" -> deviceName = strVal(value)
            "prog_size" -> progSize = numVal(value)
            "eeprom_size" -> eepromSize = numVal(value)
            "ram_size" -> ramSize = numVal(value)
            "ram_start" -> ramStart = numVal(value)
            else -> {
                throw ParserException("Unexpected param", value.location)
            }
        }
    }

    private fun strVal(value: Token): String {
        if (value !is StringLiteralToken) {
            errorStringLiteralExpected(value)
        }
        return value.str
    }

    private fun numVal(value: Token): Int {
        if (value !is NumberToken) {
            errorNumberExpected(value)
        }
        return value.value
    }

    private fun parseInterrupts(): Node {
        val startBracket = checkBracket('{')
        while (true) {
            skipWhitespace()
            val t = nextToken()
            if (t.isBracket('}')) {
                break
            } else if (t !is IdentifierToken) {
                errorIdentifierExpected(t)
            }
            checkOperator("=")
            val value = nextToken()
            if (value !is NumberToken) {
                errorUnexpectedToken(value)
            }
            interrupts.add(Interrupt(t.name, value.value))
        }
        return FakeNode(startBracket.location)
    }

    private fun parseRegisters(): Node {
        val startBracket = checkBracket('{')
        while (true) {
            skipWhitespace()
            val t = nextToken()
            if (t.isBracket('}')) {
                break
            } else if (t !is IdentifierToken) {
                errorIdentifierExpected(t)
            }
            checkBracket('(')
            val call = parseFunctionCall(t)
            val name = call.name
            val size = call.args.size
            if ((call.args.size != 1 && call.args.size != 2) || call.args[0] !is NumberNode) {
                throw ParserException("Wrong register address", call.getLocation())
            }
            val offset = (call.args[0] as NumberNode).value
            val next = nextToken()
            if (next.isBracket('{')) {
                val block = parseCodeBlock()
                registers.add(IoRegister(name, offset, size, registerBits(block, size)))
            } else {
                registers.add(IoRegister(name, offset, size, listOf()))
                unreadLast()
            }
        }
        return FakeNode(startBracket.location)
    }

    private fun registerBits(block: CompoundStmt, size: Int): List<RegisterBit> {
        val res = mutableListOf<RegisterBit>()
        for (node in block.nodes) {
            if (node !is BinaryOperNode || node.operator != Operator.ASSIGN) {
                throw ParserException("Wrong bit definition", node.getLocation())
            }
            if (node.left !is IdentifierNode) {
                errorIdentifierExpected(node.left)
            }
            if (node.right !is NumberNode) {
                errorNumberExpected(node.right)
            }
            val number = node.right.value
            if (number < 0 || number >= size*8) {
                throw ParserException("Value out of range", node.right.getLocation())
            }
            val name = node.left.name
            val bit = RegisterBit(name, number)
            res.add(bit)
        }
        return res
    }


    override fun parseFuncCallArgument(): Node {
        val t = nextToken()
        if (t !is NumberToken) {
            errorNumberExpected(t)
        }
        return NumberNode(t.location, t.value, t.radix)
    }

}

fun parseAvrDeviceDefinitionFile(filePath: String, loader: FileLoader?): AvrDevice {
    val stream = if (loader != null) {
        loader.loadSource(filePath)
    } else {
        val scanner = Scanner()
        CachedFileLoader(scanner).loadSource(filePath)
    }
    stream.remove { it is LineCommentToken || it is BlockCommentToken }
    return AvrDevParser(stream).parseDevice()
}