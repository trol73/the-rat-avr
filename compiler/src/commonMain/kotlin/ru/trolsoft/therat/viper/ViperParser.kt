package ru.trolsoft.therat.viper

import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.general.StructUnion
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.CompoundStmt
import ru.trolsoft.compiler.parser.ExternProcNode
import ru.trolsoft.compiler.parser.ExternVarNode
import ru.trolsoft.compiler.parser.LabelNode
import ru.trolsoft.compiler.parser.Node
import ru.trolsoft.compiler.parser.ProcArgumentNode
import ru.trolsoft.compiler.parser.ProcNode
import ru.trolsoft.compiler.parser.RatParser
import ru.trolsoft.compiler.parser.errorCharExpected
import ru.trolsoft.compiler.parser.errorInvalidExternSyntax
import ru.trolsoft.compiler.parser.errorRegisterExpected
import ru.trolsoft.compiler.parser.errorUnexpectedEof
import ru.trolsoft.compiler.parser.errorUnexpectedToken
import ru.trolsoft.therat.lang.BlockCommentToken
import ru.trolsoft.therat.lang.CommaToken
import ru.trolsoft.therat.lang.DataTypeToken
import ru.trolsoft.therat.lang.DirectiveToken
import ru.trolsoft.therat.lang.IdentifierToken
import ru.trolsoft.therat.lang.KeywordToken
import ru.trolsoft.therat.lang.LabelToken
import ru.trolsoft.therat.lang.LineCommentToken
import ru.trolsoft.therat.lang.NewLineToken
import ru.trolsoft.therat.lang.OperatorToken
import ru.trolsoft.therat.lang.RegisterToken
import ru.trolsoft.therat.lang.Scanner

class ViperRatParser(
    stream: TokenStream,
    scanner: Scanner
) : RatParser(stream, scanner) {

    override fun parse(): ViperAstNode {
        val nodes = mutableListOf<Node>()
        while (!stream.eof()) {
            when (val t = stream.nextToken()) {
                is KeywordToken -> when (t.name) {
                    "proc" -> nodes.add(parseProc(t))
                    "inline" -> nodes.add(parseInline(t))
                    "extern" -> nodes.add(parseExtern(t))
                    "var" -> parseVarDeclaration(nodes)
//                    "use" -> nodes.add(parseUseBlock(t))
                    "struct" -> parseStructOrUnionDeclaration(StructUnion.STRUCT)
                    "union" -> parseStructOrUnionDeclaration(StructUnion.UNION)
                    "typedef" -> parseTypedefStruct()
                    "const" -> nodes.add(parseConst(t))
                    "template" -> parseTemplate()
                    else -> errorUnexpectedToken(t)
                }
                is LineCommentToken -> {
                    // ignore
                }
                is BlockCommentToken -> {
                    // ignore
                }
                is NewLineToken -> {
                    // ignore
                }
                is DataTypeToken -> {
                    when (t.name) {
                        "byte", "word", "dword", "qword" -> nodes.add(parseDataBlock(t))
                        else -> errorUnexpectedToken(t)
                    }
                }
                is DirectiveToken -> {
                    parseDirective(nodes, t)
                }
                is LabelToken -> {
                    nodes.add(LabelNode(t.location, t.name, true))
                }
                is IdentifierToken -> {
                    val struct = structures[t.name]
                    if (struct != null) {
                        nodes.add(parseStructuredDataBlock(struct, t.location))
                    } else if (!tryProcessTemplate(t)) {
                        errorUnexpectedToken(t)
                    }
                }
                else -> {
                    errorUnexpectedToken(t)
                }
            }
        }
        return ViperAstNode(structures, nodes)
    }

    override fun parseProcArgsDefinition(): List<ProcArgumentNode> {
        // name: reg1, reg2, ...
        // name: group1 | reg1, group2 | reg2, ...
        var t = nextToken()
        val list = mutableListOf<ProcArgumentNode>()
        if (t.isBracket(')')) {
            return list
        }
        unreadLast()
        while (true) {
            val name = nextIdentifier()
            checkColon()
            val expression = when (val next = nextToken()) {
                is RegisterToken -> parseRegisterOrGroup(next)
                is IdentifierToken -> parseIdentifierOrGroup(next)
                else -> errorRegisterExpected(next)
            }
            val arg = ProcArgumentNode(name.location, name.name, expression)
            list.add(arg)
            t = nextToken()
            if (t.isBracket(')')) {
                break
            }
            if (t !is CommaToken) {
                errorCharExpected(')', t)
            }
        }
        return list
    }

    override fun parseProcResultsDefinition(): List<ProcArgumentNode> {
        var t = nextToken()
        val list = mutableListOf<ProcArgumentNode>()
        if (t.isBracket(')')) {
            return list
        }
        unreadLast()
        while (true) {
            val name = nextIdentifier()
            checkColon()
            val expression = when (val next = nextToken()) {
                is RegisterToken -> parseRegisterOrGroup(next)
                is IdentifierToken -> parseIdentifierOrGroup(next)
                else -> errorRegisterExpected(next)
            }
            val arg = ProcArgumentNode(name.location, name.name, expression)
            list.add(arg)
            t = nextToken()
            if (t.isBracket(')')) {
                break
            }
            if (t !is CommaToken) {
                errorCharExpected(')', t)
            }
        }
        return list
    }

    private fun parseInline(t: KeywordToken): ProcNode {
        if (eof()) {
            errorUnexpectedEof(stream)
        }
        val procToken = nextKeyword("proc")
        return parseProc(procToken, true)
    }

    private fun parseExtern(externToken: KeywordToken): Node {
        val varOrProc = nextToken() as? KeywordToken ?: errorInvalidExternSyntax(externToken)
        return when (varOrProc.name) {
            "var" -> {
                val list = mutableListOf<Node>()
                parseVarDeclaration(list)
                ExternVarNode(externToken.location, list)
            }
            "proc" -> {
                val name = nextIdentifier()
                checkBracket('(')
                val args = parseProcArgsDefinition()
                val results = mutableListOf<ProcArgumentNode>()
                // Check for results specification: -> (name: reg, ...)
                skipWhitespace()
                val t = nextToken()
                if (t is OperatorToken && t.name == "->") {
                    checkBracket('(')
                    results.addAll(parseProcResultsDefinition())
                } else {
                    stream.unreadLast(t)
                }
                val proc = ProcNode(varOrProc.location, name.name, args, results, CompoundStmt(listOf()), false)
                nextKeyword("absolute")
                val offset = nextNumber()
                ExternProcNode(externToken.location, proc, offset.value)
            }
            else -> errorInvalidExternSyntax(varOrProc)
        }
    }

    override fun parseDataType(t: DataTypeToken): DataType =
        when (t.name) {
            "byte" -> DataType.BYTE
            "word" -> DataType.WORD
            "dword" -> DataType.DWORD
            "qword" -> DataType.QWORD
            "ptr" -> DataType.PTR
            "uint8_t" -> DataType.BYTE
            "uint16_t" -> DataType.WORD
            "uint32_t" -> DataType.DWORD
            "uint64_t" -> DataType.QWORD
            "int8_t" -> DataType.BYTE
            "int16_t" -> DataType.WORD
            "int32_t" -> DataType.DWORD
            "int64_t" -> DataType.QWORD
            "char" -> DataType.BYTE
            else -> throw Exception("Internal error, wrong data type: " + t.name)
        }
}
