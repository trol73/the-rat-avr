package ru.trolsoft.therat.i8085

import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.general.StructUnion
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.lang.*

class I8085RatParser(stream: TokenStream) : RatParser(stream) {

    override fun parse(): I8085AstNode {
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
                    } else {
                        errorUnexpectedToken(t)
                    }
                }
                else -> {
                    errorUnexpectedToken(t)
                }
            }
        }
        return I8085AstNode(structures, nodes)
    }

    private fun parseProc(procToken: KeywordToken, isInline: Boolean = false): ProcNode {
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
        checkBracket('{')
        val body = parseCodeBlock()
        return ProcNode(procToken.location, name.name, args, results, body, isInline)
    }

    private fun parseProcResultsDefinition(): List<ProcArgumentNode> {
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
                val proc = ProcNode(varOrProc.location, name.name, args, mutableListOf(), CompoundStmt(listOf()), false)
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
            else -> throw Exception("Internal error, wrong data type: " + t.name)
        }


    private fun parseProcArgsDefinition(): List<ProcArgumentNode> {
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
            //val expression = parseRegisterOrGroup()
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
}