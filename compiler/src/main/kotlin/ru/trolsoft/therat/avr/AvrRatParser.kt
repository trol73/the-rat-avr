package ru.trolsoft.therat.avr

import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.general.StructUnion
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.lang.*

class AvrRatParser(stream: TokenStream) : RatParser(stream) {
    private var vectors: VectorTableNode? = null

    override fun parse(): AvrAstNode {
        val nodes = mutableListOf<Node>()
        while (!stream.eof()) {
            when (val t = stream.nextToken()) {
                is KeywordToken -> when (t.name) {
                    "proc" -> nodes.add(parseProc(t))
                    "inline" -> nodes.add(parseInline())
                    "vectors" -> parseVectors(t)
                    "extern" -> nodes.add(parseExtern(t))
                    "var" -> parseVarDeclaration(nodes)
                    "pin" -> parsePinDeclaration(nodes)
                    "bus" -> parseBusDeclaration(nodes)
                    "use" -> nodes.add(parseUseBlock(t))
                    "reuse" -> nodes.add(parseReuseBlock(t))
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
        return AvrAstNode(vectors, structures, nodes)
    }

    override fun parseKeyword(t: KeywordToken): Node {
        when (t.name) {
            "use" -> return parseUseBlock(t)
            "reuse" -> return parseReuseBlock(t)
        }
        return super.parseKeyword(t)
    }

    override fun parseDataType(t: DataTypeToken): DataType =
        when (t.name) {
            "byte" -> DataType.BYTE
            "word" -> DataType.WORD
            "dword" -> DataType.DWORD
            "qword" -> DataType.QWORD
            "ptr" -> DataType.PTR
            "prgptr" -> DataType.PRGPTR
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
            val expression = resolveRegisterOrAlias(nextToken())
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

    private fun parseRegisterOrIdentifierAlias(first: IdentifierToken): Node {
        val checkDot = nextToken()
        if (checkDot is DotToken) {
            return parseDottedGroup(first)
        } else {
            unreadLast()
            // Check if this is a special register pair (X, Y, Z)
            val regName = first.name.lowercase()
            return when (regName) {
                "x" -> RegisterGroupNode(first.location, listOf("r27", "r26"))
                "y" -> RegisterGroupNode(first.location, listOf("r29", "r28"))
                "z" -> RegisterGroupNode(first.location, listOf("r31", "r30"))
                else -> IdentifierNode(first.location, first.name)
            }
        }
    }

    private fun resolveRegisterOrAlias(token: Token): Node =
        when (token) {
            is RegisterToken -> {
                val regName = token.name.lowercase()
                when (regName) {
                    "x" -> RegisterGroupNode(token.location, listOf("r27", "r26"))
                    "y" -> RegisterGroupNode(token.location, listOf("r29", "r28"))
                    "z" -> RegisterGroupNode(token.location, listOf("r31", "r30"))
                    else -> parseRegisterOrGroup(token) as Node
                }
            }
            is IdentifierToken -> parseRegisterOrIdentifierAlias(token)
            else -> token as Node
        }

    private fun parseInline(): ProcNode  {
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
            val cmd = parseExpression()
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
                val proc = ProcNode(varOrProc.location, name.name, args, mutableListOf(), CompoundStmt(listOf()), false)
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
            val expression = resolveRegisterOrAlias(nextToken())
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

    private fun parseUseDeclaration(t: KeywordToken): UseNode {
        val reg = parsePrimary()
        if (reg !is RegisterNode && reg !is RegisterGroupNode && reg !is IdentifierNode && reg !is DotGroupNode) {
            errorRegisterOrGroupExpected(reg)
        }
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

    private fun parseReuseBlock(t: KeywordToken): Node {
        val first = parseReuseDeclaration(t)
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
            list.add(parseReuseDeclaration(t))
        }
        return if (list != null) CompoundStmt(list) else first
    }

    private fun parseReuseDeclaration(t: KeywordToken): UseNode {
        val reg = parsePrimary()
        if (reg !is RegisterNode && reg !is RegisterGroupNode && reg !is IdentifierNode && reg !is DotGroupNode) {
            errorRegisterOrGroupExpected(reg)
        }
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
        return UseNode(t.location, reg, name.name, capture, isReuse = true)
    }



    private fun parsePinDeclaration(nodes: MutableList<Node>) {
        val name = nextIdentifier()
        checkOperator("=")
        val pin = nextIdentifier()
        val node = PinDeclarationNode(name.location, name.name, pin.name)
        nodes.add(node)
        checkNewLineOrComment()
    }

    private fun parseBusDeclaration(nodes: MutableList<Node>) {
        val name = nextIdentifier()
        val equalOrBracket = nextToken()
        val size = if (equalOrBracket.isOperator("=")) {         // bus name = [...]
            -1
        } else if (equalOrBracket.isBracket('[')) {          // bus name[size] = [...]
            val sz = parseExpression()
            if (sz !is NumberNode) {
                errorNumberExpected(sz)
            }
            checkBracket(']')
            checkOperator("=")
            sz.value
        } else {
            errorOperatorExpected(equalOrBracket)
        }
        checkBracket('[')
        val pins = mutableListOf<Node>()
        while (true) {
            val pin = nextIdentifier()
            val next = nextToken()
            when {
                next.isBracket(']') -> {
                    pins.add(IdentifierNode(pin.location, pin.name))
                    break
                }
                next is CommaToken -> {
                    pins.add(IdentifierNode(pin.location, pin.name))
                }
                next is DotToken -> {
                    val secondDot = nextToken()
                    if (secondDot !is DotToken) {
                        errorUnexpectedToken(secondDot)
                    }
                    val lastRangePin = nextIdentifier()
                    val fromPin = IdentifierNode(pin.location, pin.name)
                    val toPin = IdentifierNode(lastRangePin.location, lastRangePin.name)
                    pins.add(BusRangeNode(fromPin, toPin))
                    val next2 = nextToken()
                    if (next2.isBracket(']')) {
                        break
                    } else if (next2 !is CommaToken) {
                        errorUnexpectedToken(next2)
                    }
                }
                else -> {
                    errorUnexpectedToken(next)
                }
            }
        }
        val node = BusDeclarationNode(name.location, name.name, pins, size)
        nodes.add(node)
        checkNewLineOrComment()
    }

    override fun parseAssemblerInstruction(cmd: AssemblerToken): Node {
        when (cmd.name) {
            "push", "pop" -> return parseMultiplePushPop(cmd)
            "rcall", "call" -> return parseFuncCallWithResult(cmd)
        }
        return super.parseAssemblerInstruction(cmd)
    }

    private fun parseFuncCallWithResult(cmd: AssemblerToken): Node {
        // Parse: rcall/call func() -> (name: reg, ...) or rcall/call func() -> reg
        val funcName = nextIdentifier()
        
        // Check for optional () after function name
        skipWhitespace()
        val openBracket = nextToken()
        
        val funcArgs = mutableListOf<Node>()
        var hasParens = false
        
        if (openBracket.isBracket('(')) {
            hasParens = true
            // Parse function arguments if present
            while (true) {
                val argToken = nextToken()
                when {
                    argToken.isBracket(')') -> {
                        break
                    }
                    funcArgs.isEmpty() -> {
                        unreadLast()
                        funcArgs.add(parseFuncCallArgument())
                    }
                    argToken is CommaToken -> {
                        funcArgs.add(parseFuncCallArgument())
                    }
                    else -> errorCharExpected(')', argToken)
                }
            }
        } else {
            unreadLast()
        }
        val loc = cmd.location
        // Create appropriate node based on whether we have parentheses
        val funcOrLabel = if (hasParens) {
            FunctionCallNode(loc, funcName.name, funcArgs)
        } else {
            IdentifierNode(loc, funcName.name)
        }
        
        // Check for -> dest (only if next meaningful token is ->)
        val arrowToken = nextToken()
        val resultExpr = if (hasParens && arrowToken is OperatorToken && arrowToken.name == "->") {
            // Parse destination: either single register or (name: reg, ...)
            skipWhitespace()
            val destToken = nextToken()
            if (destToken.isBracket('(')) {
                // Parse named destinations: (name: reg, ...)
                parseResultDestinations()
            } else {
                // Single register destination
                unreadLast()
                parseExpression()
            }
        } else {
            // Not a result assignment, put the token back
            unreadLast()
            null
        }
        
        val arg = if (resultExpr != null) {
            BinaryOperNode(loc, Operator.ARROW_RIGHT, funcOrLabel, resultExpr)
        } else {
            funcOrLabel
        }
        
        return AssemblerInstrNode(loc, cmd.name, listOf(arg))
    }

    private fun parseResultDestinations(): Node {
        // Parse: (name: reg, ...) as a list of CallArgumentNode
        var t = nextToken()
        val destinations = mutableListOf<CallArgumentNode>()
        
        if (t.isBracket(')')) {
            return CompoundStmt(destinations)
        }
        
        unreadLast()
        while (true) {
            val nameToken = nextToken()
            if (nameToken is IdentifierToken) {
                checkColon()
                val regToken = nextToken()
                val regNode = resolveRegisterOrAlias(regToken)
                val dest = CallArgumentNode(nameToken.location, nameToken.name, regNode)
                destinations.add(dest)
            } else {
                errorIdentifierExpected(nameToken)
            }
            
            t = nextToken()
            if (t.isBracket(')')) {
                break
            }
            if (t !is CommaToken) {
                errorCharExpected(')', t)
            }
        }
        
        return if (destinations.size == 1) {
            destinations[0]
        } else {
            CompoundStmt(destinations)
        }
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

}




fun errorVectorsAlreadyDefined(token: Token): Nothing {
    throw ParserException("Vectors table already defined", token.location)
}

private fun errorRegisterOrGroupExpected(node: Node): Nothing {
    throw ParserException("Register or group expected", node.getLocation())
}



