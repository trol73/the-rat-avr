package ru.trolsoft.therat.avr

import ru.trolsoft.therat.lang.*
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*


class AvrParser(stream: TokenStream) : Parser(stream) {
    private var vectors: VectorTableNode? = null

    override fun parse(): AvrAstNode {
        val nodes = mutableListOf<Node>()
        while (!stream.eof()) {
            val t = stream.nextToken()
            when (t) {
                is KeywordToken -> when (t.name) {
                    "proc" -> nodes.add(parseProc(t))
                    "inline" -> nodes.add(parseInline(t))
                    "vectors" -> parseVectors(t)
                    "extern" -> nodes.add(parseExtern(t))
                    "var" -> parseVarDeclaration(nodes)
                    "pin" -> parsePinDeclaration(nodes)
                    "use" -> nodes.add(parseUseBlock(t))
                    else -> errorUnexpectedToken(t)
                }
                is LineCommentToken -> {
                }
                is BlockCommentToken -> {
                }
                is NewLineToken -> {

                }
                is DataTypeToken -> {
                    when (t.name) {
                        "byte", "word", "dword" -> nodes.add(parseDataBlock(t))
                        else -> errorUnexpectedToken(t)
                    }
                }
                is DirectiveToken -> parseDirective(nodes, t)
                is LabelToken -> nodes.add(LabelNode(t.location, t.name, true))
                else -> errorUnexpectedToken(t)
            }
        }
        //return AvrAst(stream.nextToken().location, ast.nodes, ast.data, ast.code, ast.externs, ast.vectorsTable)
        return AvrAstNode(vectors, nodes)
    }


    override fun parseFuncCallArgument(): Node {
        val first = parseExpression()
        val next = nextToken()
        if (next is ColonToken) {
            if (first !is IdentifierNode) {
                errorUnexpectedToken(next)
            }
            val second = parseExpression()
            return CallArgumentNode(first.getLocation(), first.name, second)
        } else if (next is CommaToken || next.isBracket(')')) {
            unreadLast()
            return CallArgumentNode(first.getLocation(), null, first)
        }
        errorUnexpectedToken(next)
    }

    override fun parseKeyword(t: KeywordToken): Node {
        when (t.name) {
            "use" -> return parseUseBlock(t)
        }
        return super.parseKeyword(t)
    }

    private fun parseProc(procToken: KeywordToken, isInline: Boolean = false): ProcNode {
        val name = nextIdentifier()
        checkBracket('(')
        val args = parseProcArgsDefinition()
        checkBracket('{')
        val body = parseCodeBlock()
        return ProcNode(procToken.location, name.name, args, body, isInline)
    }

    private fun parseInline(t: KeywordToken): ProcNode  {
        if (eof()) {
            errorUnexpectedEof(stream)
        }
        val procToken = nextKeyword("proc")
        return parseProc(procToken, true)
    }


    private fun parseVectors(vectorsToken: KeywordToken): VectorTableNode {
        if (vectors != null) {
            errorVectorsAlreadyDefined(vectorsToken)
        }
        val map = mutableMapOf<String, Node>()
        skipWhitespace()
        checkBracket('{')
        while (true) {
            skipWhitespace()
            val t = nextToken()
            if (t.isBracket('}')) {
                break
            }
            if (t !is LabelToken) {
                errorLabelExpected(t)
            }
            skipWhitespace()
            val cmd = parsePrimary()
            map[t.name] = cmd
        }
        val res = VectorTableNode(vectorsToken.location, map)
        vectors = res
        return res
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
                val proc = ProcNode(varOrProc.location, name.name, args, CompoundStmt(listOf()), false)
                ExternProcNode(externToken.location, proc)
            }
            else -> errorInvalidExternSyntax(varOrProc)
        }
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
            val next = nextToken()
            val expression = when (next) {
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

    private fun parseVarDeclaration(nodes: MutableList<Node>) {
        val name = nextIdentifier()
        val comaOrColon = nextToken()
        when (comaOrColon) {
            is ColonToken -> {
                val type = nextDataType()
                val arraySize = parseArraySize()
                val node = if (arraySize == null) VarDeclarationNode(name.location, name.name, type)
                    else DataArrayDeclarationNode(name.location, name.name, type, arraySize)

                nodes.add(node)
                checkNewLineOrComment()
            }
            is CommaToken -> {
                val names = mutableListOf(name)
                while (true) {
                    val nextName = nextIdentifier()
                    names.add(nextName)
                    val t = nextToken()
                    if (t is CommaToken) {
                        continue
                    } else if (t is ColonToken) {
                        val type = nextDataType()
                        val arraySize = parseArraySize()
                        for (n in names) {
                            val node = if (arraySize == null) VarDeclarationNode(n.location, n.name, type)
                            else DataArrayDeclarationNode(n.location, n.name, type, arraySize)
                            nodes.add(node)
                        }
                        checkNewLineOrComment()
                        return
                    } else {
                        errorCharExpected(':', t)
                    }
                }
            }
            else -> errorCharExpected(':', comaOrColon)
        }
    }

    private fun parseUseDeclaration(t: KeywordToken): UseNode {
        val reg = parsePrimary()
        if (reg !is RegisterNode && reg !is RegisterGroupNode) {
            errorRegisterOrGroupExpected(reg)
        }
                //nextRegister()
        nextKeyword("as")
        val name = nextIdentifier()
        val capture: Boolean
        if (!eof()) {
            val next = nextToken()
            if (next.isOperator("!")) {
                capture = true
            } else {
                capture = false
                unreadLast()
            }
        } else {
            capture = false
        }
        return UseNode(t.location, reg, name.name, capture)
    }


    private fun parseUseBlock(t: KeywordToken): Node {
        val first = parseUseDeclaration(t)
        var list: MutableList<UseNode>? = null
        while (!eof()) {
            val comma = nextToken()
            if (comma !is CommaToken) {
                unreadLast()
                return if (list != null) CompoundStmt(list) else  first
            }
            if (list == null) {
                list = mutableListOf(first)
            }
            list.add(parseUseDeclaration(t))
        }
        return if (list != null) CompoundStmt(list) else first
    }



    private fun parsePinDeclaration(nodes: MutableList<Node>) {
        val name = nextIdentifier()
        checkOperator("=")
        val pin = nextIdentifier()
        val node = PinDeclarationNode(name.location, name.name, pin.name)
        nodes.add(node)
        checkNewLineOrComment()
    }

    private fun parseDirective(nodes: MutableList<Node>, t: DirectiveToken) {
        val next = nextToken()
        if (next is NewLineToken) {
            val node = DirectiveNode(t.location, t.name, null)
            nodes.add(node)
        } else if (next.isBracket('(')) {
            val nameToken = IdentifierToken(t.name, t.location)
            val call = parseFunctionCall(nameToken)
            val args = mutableListOf<Node>()
            for (ca in call.args) {
                val arg = (ca as CallArgumentNode).expr
                args.add(arg)
            }
            val node = DirectiveNode(t.location, t.name, args)
            nodes.add(node)
        }
        checkNewLineOrComment()
    }


    private fun nextDataType(): DataType {
        val t = nextToken()
        if (t !is DataTypeToken) {
            errorDataTypeExpected(t)
        }
        return DataType.fromToken(t)
    }

    override fun parseAssemblerInstruction(cmd: AssemblerToken): Node {
        if (cmd.name == "push" || cmd.name == "pop") {
            return parseMultiplePushPop(cmd)
        }
        return super.parseAssemblerInstruction(cmd)
    }

    private fun parseMultiplePushPop(cmd: AssemblerToken): Node {
        val nodes = mutableListOf<AssemblerInstrNode>()
        while (true) {
            if (eof()) {
                break
            }
            val next = nextToken()
            if (next is NewLineToken || next is LineCommentToken) {
                break
            }
            if (next.isBracket('}')) {
                unreadLast()
                break
            }
            unreadLast()
            val arg = parseExpression()
//            val arg = IdentifierNode(next.location, next.)
            val node = AssemblerInstrNode(arg.getLocation(), cmd.name, listOf(arg))
            nodes.add(node)
        }
        return when {
            nodes.isEmpty() -> errorRegisterExpected(cmd)
            nodes.size == 1 -> nodes.first()
            else -> CompoundStmt(nodes)
        }
    }

    override fun parseDataBlock(t: DataTypeToken): DataBlockNode {
        nextBracket('[')
        nextBracket(']')
        val next = nextToken()
        when {
            next is StringLiteralToken -> {
                val node = StringLiteralNode(next.location, next.str)
                return DataBlockNode(t.location, DataType.fromToken(t), listOf(node))
            }
            next.isBracket('{') -> {
                val items = mutableListOf<Node>()
                while (true) {
                    val canBeBracket = nextToken()
                    if (canBeBracket.isBracket('}')) {
                        break
                    }
                    unreadLast()
                    val node = parseExpression()//parsePrimary()
                    items.add(node)
                    var comaOrEnd = nextToken()
                    var endLine = false
                    while (comaOrEnd is NewLineToken || comaOrEnd is LineCommentToken) {
                        comaOrEnd = nextToken()
                        endLine = true
                    }
                    if (comaOrEnd.isBracket('}')) {
                        break
                    } else if (comaOrEnd !is CommaToken) {
                        if (endLine) {
                            unreadLast()
                        } else {
                            errorCommaOrCloseBrackedExpected(comaOrEnd)
                        }
                    }
                }
                return DataBlockNode(t.location, DataType.fromToken(t), items)
            }
            else -> {
                errorUnexpectedToken(next)
            }
        }

    }



//    private fun parseExpression(): ExpressionNode {
//        while (!stream.eof()) {
//            val t = nextToken(stream)
//        }
//
//
//        return t
//    }
}




fun errorVectorsAlreadyDefined(token: Token): Nothing {
    throw ParserException("Vectors table already defined", token.location)
}

private fun errorCommaOrCloseBrackedExpected(token: Token): Nothing {
    throw ParserException("',' or '}' expected", token.location)
}

private fun errorRegisterOrGroupExpected(node: Node): Nothing {
    throw ParserException("Register or group expected", node.getLocation())
}



