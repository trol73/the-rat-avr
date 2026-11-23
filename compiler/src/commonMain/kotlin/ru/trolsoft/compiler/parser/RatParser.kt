package ru.trolsoft.compiler.parser

import ru.trolsoft.compiler.compiler.errorArrayOfPrimitivesExpected
import ru.trolsoft.compiler.compiler.errorStructuredFieldNotFound
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.general.*
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.therat.lang.*


abstract class RatParser(
    stream: TokenStream,
    private val scanner: Scanner
) : Parser(stream) {
    protected val structures = mutableMapOf<String, StructuredData>()
    val templates = mutableMapOf<String, TemplateNode>()

    protected abstract fun parseDataType(t: DataTypeToken): DataType
    protected abstract fun parseProcArgsDefinition(): List<ProcArgumentNode>
    protected abstract fun parseProcResultsDefinition(): List<ProcArgumentNode>

    /**
     * Common implementation for parsing procedure declarations.
     * Subclasses must override parseProcArgsDefinition() and parseProcResultsDefinition()
     * to handle architecture-specific register resolution.
     */
    protected fun parseProc(procToken: KeywordToken, isInline: Boolean = false): ProcNode {
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

    protected fun parseVarDeclaration(nodes: MutableList<Node>) {
        val name = nextIdentifier()
        when (val comaOrColon = nextToken()) {
            is ColonToken -> {
                addVarOrArrayDeclaration(listOf(name), nodes)
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
                        addVarOrArrayDeclaration(names, nodes)
                        return
                    } else {
                        errorCharExpected(':', t)
                    }
                }
            }
            else -> errorCharExpected(':', comaOrColon)
        }
    }

    private fun addVarOrArrayDeclaration(names: List<IdentifierToken>, nodes: MutableList<Node>) {
        val type: DataType
        val structName: String?
        when (val t = nextToken()) {
            is DataTypeToken -> {
                type = parseDataType(t)
                structName = null
            }
            is IdentifierToken -> {
                type = DataType.STRUCTURED
                if (structures[t.name] == null) {
                    errorUnknownDataType(t)
                }
                structName = t.name
            }
            else -> errorDataTypeExpected(t)
        }

        val arraySize = parseArraySize()
        for (name in names) {
            val node = if (arraySize == null) VarDeclarationNode(name.location, name.name, type, structName)
            else DataArrayDeclarationNode(name.location, name.name, type, arraySize, structName)
            nodes.add(node)
        }

        // check 'absolute'
        if (!eof() && names.size == 1) {
            val t = nextToken()
            if (t.isKeyword("absolute")) {
                val addr = nextNumber()
                val nodeWithAbsolute = when (val src = nodes.last()) {
                    is VarDeclarationNode -> VarDeclarationNode(src.getLocation(), src.name, src.dataType, src.structName, addr.value)
                    is DataArrayDeclarationNode -> DataArrayDeclarationNode(src.getLocation(), src.name, src.dataType, src.size, src.structName, addr.value)
                    else -> internalError()
                }
                nodes[nodes.size-1] = nodeWithAbsolute
            } else {
                unreadLast()
            }
        }

        checkNewLineOrComment()
    }
    protected fun parseStructOrUnionDeclaration(type: StructUnion) {
        nextBracket('{')
        skipWhitespace()
        val items = mutableListOf<DataItem>()
        while (true) {
            when (val next = nextToken()) {
                is BracketToken -> {
                    if (next.bracket == '}') {
                        break
                    } else {
                        errorUnexpectedBracket(next)
                    }
                }
                is DataTypeToken -> {
                    parseStructItem(items, next)
                }
                is IdentifierToken -> {
                    parseStructItem(items, next)
                }
                else -> {
                    errorUnexpectedToken(next)
                }
            }
        }
        val structName = nextIdentifier()
        val name = structName.name
        if (structures.keys.contains(name)) {
            errorConflictingStructName(structName)
        }
        structures[name] = StructuredData(type, name, items)
    }

    private fun parseStructItem(items: MutableList<DataItem>, typeToken: Token) {
        when (val nameOrStar = nextToken()) {
            is IdentifierToken -> {
                val itm = parseStructItem(typeToken, nameOrStar, false)
                checkStructItem(items, nameOrStar)
                items.add(itm)
                skipWhitespace()
            }
            is OperatorToken -> {
                if (nameOrStar.isOperator("*")) {
                    val name = nextIdentifier()
                    val itm = parseStructItem(typeToken, name, true)
                    checkStructItem(items, name)
                    items.add(itm)
                    skipWhitespace()
                } else {
                    errorUnexpectedToken(nameOrStar)
                }
            }
            else -> errorUnexpectedToken(typeToken)
        }
    }

    private fun parseStructItem(
        typeToken: Token,
        name: IdentifierToken,
        isPointer: Boolean
    ) = when (typeToken) {
        is DataTypeToken -> {
            parseStructPrimitiveItemOrArray(name.name, isPointer, typeToken)
        }
        is IdentifierToken -> {
            parseNestedStructItem(name.name, isPointer, typeToken)
        }
        else -> {
            internalError()
        }
    }

    private fun parseStructPrimitiveItemOrArray(name: String, isPointer: Boolean, dataTypeToken: DataTypeToken): DataItem {
        val dataType = parseDataType(dataTypeToken)
        val bracketOrNewLine = nextToken()
        return if (bracketOrNewLine.isBracket('[')) {
            val arraySize = nextNumber().value
            nextBracket(']')
            val baseItem = PrimitiveItem(name, dataType.ramSize, dataType)
            val arrayBaseItem = if (!isPointer) {
                baseItem
            } else {
                PointerItem(name, 2, baseItem)
            }
            ArrayItem(name, arraySize*arrayBaseItem.size, arrayBaseItem, arraySize)
        } else {
            val baseItem = PrimitiveItem(name, dataType.ramSize, dataType)
            if (!isPointer) {
                baseItem
            } else {
                PointerItem(name, 2, baseItem)
            }
        }
    }

    private fun parseNestedStructItem(name: String, isPointer: Boolean, structName: IdentifierToken): DataItem {
        val struct = structures[structName.name] ?: errorUnknownTypeName(structName)
        val bracketOrNewLine = nextToken()
        return if (bracketOrNewLine.isBracket('[')) {
            val arraySize = nextNumber().value
            nextBracket(']')
            val baseItem = StructItem(name, struct.getSize(), struct)
            val arrayBaseItem = if (!isPointer) {
                baseItem
            } else {
                PointerItem(name, 2, baseItem)
            }
            ArrayItem(name, arraySize*arrayBaseItem.size, arrayBaseItem, arraySize)
        } else {
            val baseItem = StructItem(name, struct.getSize(), struct)
            if (!isPointer) {
                baseItem
            } else {
                PointerItem(name, 2, baseItem)
            }
        }
    }


    private fun checkStructItem(items: List<DataItem>, nameToken: IdentifierToken) {
        for (itm in items) {
            if (itm.name == nameToken.name) {
                errorDuplicateMember(nameToken)
            }
        }
    }

    protected fun parseTypedefStruct() {
        val type = nextKeyword("struct", "union")
        if (type.name == "struct") {
            parseStructOrUnionDeclaration(StructUnion.STRUCT)
        } else {
            parseStructOrUnionDeclaration(StructUnion.UNION)
        }
    }

    protected fun parseStructuredDataBlock(struct: StructuredData, loc: SourceLocation): DataBlockNode {
        nextBracket('[')
        nextBracket(']')
        nextBracket('{')
        val items = mutableListOf<Node>()
        while (true) {
            val t = nextToken()
            if (t.isBracket('}')) {
                break
            } else if (t.isBracket('{')) {
                items.add(parseStructuredDataItem(struct, t.location))
                skipWhitespace()
                val next = nextToken()
                when {
                    next is CommaToken -> {
                        // ignore
                    }
                    next.isBracket('}') -> {
                        break
                    }
                    next.isBracket('{') -> {
                        unreadLast()
                    }
                    else -> {
                        errorUnexpectedToken(next)
                    }
                }
            }
        }
        return DataBlockNode(loc, DataType.STRUCTURED, items, struct.name)
    }

    private fun parseStructuredDataItem(struct: StructuredData, loc: SourceLocation): Node {
        val items = mutableMapOf<String, Node>()
        while (true) {
            val name = nextIdentifierMultiline()
            checkOperator("=")

            val mayBeBracket = nextToken()
            val value = if (mayBeBracket.isBracket('[')) {
                val item = struct.getItem(name.name) ?: errorStructuredFieldNotFound(name, struct.name)
                if (item is ArrayItem && item.source is PrimitiveItem) {
                    parseArrayDataItem(mayBeBracket.location, item.source. dataType, struct.name)
                } else {
                    errorArrayOfPrimitivesExpected(mayBeBracket.location)
                }
            } else {
                unreadLast()
                val firstValuePart = parsePrimary()
                val next = nextToken()
                if (next.isOperator("/")) {
                    val divider = parsePrimary()
                    BinaryOperNode(firstValuePart.getLocation(), Operator.DIV, firstValuePart, divider)
                } else {
                    unreadLast()
                    firstValuePart
                }
            }
            items[name.name] = value
            val next = nextMeaningToken()
            if (next is CommaToken) {
                continue
            } else if (next.isBracket('}')) {
                break
            } else {
                errorUnexpectedToken(next)
            }
        }
        return MapNode(loc, items, struct)
    }

    private fun parseArrayDataItem(loc: SourceLocation, type: DataType, structName: String?): DataBlockNode {
        val items = mutableListOf<Node>()
        while (true) {
            val item = parsePrimary()
            items.add(item)
            val t = nextToken()
            if (t.isBracket(']')) {
                break
            } else if (t !is CommaToken) {
                errorUnexpectedToken(t)
            }
        }
        return DataBlockNode(loc, type, items, structName)
    }

    override fun parseDataBlock(t: DataTypeToken): DataBlockNode {
        nextBracket('[')
        val optionalSize = nextToken()
        unreadLast()
        val declaredSize = if (optionalSize is NumberToken) {
            parseExpression()
        } else {
            null
        }
        nextBracket(']')
        val next = nextToken()
        when {
            next is StringLiteralToken -> {
                val node = StringLiteralNode(next.location, next.str)
                return DataBlockNode(t.location, parseDataType(t), listOf(node))
            }
            next.isBracket('{') -> {
                val items = mutableListOf<Node>()
                while (true) {
                    val canBeBracket = nextToken()
                    if (canBeBracket.isBracket('}')) {
                        break
                    }
                    unreadLast()
                    val node = parseExpression()
                    items.add(node)
                    var commaOrEnd = nextToken()
                    var endLine = false
                    while (commaOrEnd is NewLineToken || commaOrEnd is LineCommentToken) {
                        commaOrEnd = nextToken()
                        endLine = true
                    }
                    if (commaOrEnd.isBracket('}')) {
                        break
                    } else if (commaOrEnd !is CommaToken) {
                        if (endLine) {
                            unreadLast()
                        } else {
                            errorCommaOrCloseBracketExpected(commaOrEnd)
                        }
                    }
                }
                return DataBlockNode(t.location, parseDataType(t), items, declaredSize = declaredSize)
            }
            else -> {
                errorUnexpectedToken(next)
            }
        }
    }

    protected fun parseDirective(nodes: MutableList<Node>, t: DirectiveToken) {
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
        } else {
            errorUnexpectedToken(next)
        }
        checkNewLineOrComment()
    }

    fun parseTemplate() {
        val name = nextIdentifier()
        if (templates.keys.contains(name.name)) {
            errorTemplateAlreadyExists(name)
        }
        checkBracket('(')
        val args = mutableListOf<String>()
        while (true) {
            val nameOrBracket = nextToken()
            if (nameOrBracket.isBracket(')')) {
                break
            } else if (nameOrBracket is IdentifierToken) {
                val argName = nameOrBracket.name
                if (args.contains(argName)) {
                    errorDuplicateMember(nameOrBracket)
                }
                args.add(argName)
            }
        }

        skipWhitespace()
        checkBracket('{')
        var bracketCode = 1
        val tokens = mutableListOf<Token>()
        while (true) {
            val next = nextToken()
            if (next.isBracket('}')) {
                bracketCode--
                if (bracketCode == 0) {
                    break
                }
            } else if (next.isBracket('{')) {
                bracketCode++
            }
            tokens.add(next)
        }
        val template = TemplateNode(name.location, name.name, args, tokens)
        templates[name.name] = template
    }

    protected fun tryProcessTemplate(name: IdentifierToken): Boolean {
        val template = templates[name.name] ?: return false
        val args = parseTemplateImplArgs()
        val impl = implementTemplate(template, args)
        stream.markLast()
        stream.replaceMarked(impl)
        stream.rewindBack()
        return true
    }

    private fun parseTemplateImplArgs(): Map<String, Token> {
        nextBracket('(')
        val args = mutableMapOf<String, Token>()
        while (true) {
            val nameOrBracket = nextToken()
            if (nameOrBracket.isBracket(')')) {
                break
            }
            if (nameOrBracket is CommaToken) {
                continue
            }
            if (nameOrBracket !is IdentifierToken) {
                errorIdentifierExpected(nameOrBracket)
            }
            val argName = nameOrBracket.name
            if (args.contains(argName)) {
                errorDuplicateMember(nameOrBracket)
            }

            checkColon()

            val firstToken = nextToken()
            if (firstToken.isBracket('[')) {
                val arrayTokens = mutableListOf<Token>()
                arrayTokens.add(firstToken)  // Add '[' to mark this as an array
                var depth = 1
                while (depth > 0) {
                    val token = nextToken()
                    when {
                        token.isBracket('[') || token.isBracket('{') || token.isBracket('(') -> {
                            depth++
                            arrayTokens.add(token)
                        }
                        token.isBracket(']') || token.isBracket('}') || token.isBracket(')') -> {
                            depth--
                            arrayTokens.add(token)
                        }
                        else -> {
                            arrayTokens.add(token)
                        }
                    }
                }
                args[argName] = GroupToken(arrayTokens, firstToken.location)
            } else {
                val tokens = mutableListOf<Token>()
                tokens.add(firstToken)
                while (true) {
                    val next = nextToken()
                    if (next.isBracket(')')) {
                        unreadLast()
                        break
                    } else if (next is CommaToken) {
                        break
                    }
                    tokens.add(next)
                }
                args[argName] = GroupToken(tokens, firstToken.location)
            }
        }
        return args
    }

    private fun implementTemplate(template: TemplateNode, args: Map<String, Token>): MutableList<Token> {
        val arrayArgName = args.entries.firstOrNull { (name, token) -> 
            (token as? GroupToken)?.tokens?.firstOrNull()?.isBracket('[') == true
        }?.key
        
        return if (arrayArgName != null) {
            // Expand arrays into multiple template instantiations
            val allArrayArgs = args.filterValues { 
                (it as? GroupToken)?.tokens?.firstOrNull()?.isBracket('[') == true 
            }
            val arrayElements = allArrayArgs.mapValues { (_, token) ->
                extractArrayElements((token as GroupToken).tokens)
            }
            
            // Verify all arrays have the same length
            val expectedLength = arrayElements.values.first().size
            for ((name, elements) in arrayElements) {
                if (elements.size != expectedLength) {
                    throw ParserException("Template array argument '$name' has ${elements.size} elements, but '${allArrayArgs.keys.first()}' has $expectedLength elements", 
                        template.getLocation())
                }
            }
            
            val result = mutableListOf<Token>()
            for (i in 0 until expectedLength) {
                // Build per-iteration args
                val iterationArgs = mutableMapOf<String, Token>()
                for ((name, token) in args) {
                    when {
                        (token as? GroupToken)?.tokens?.firstOrNull()?.isBracket('[') == true -> {
                            val elements = extractArrayElements(token.tokens)
                            iterationArgs[name] = GroupToken(elements[i], token.location)
                        }
                        else -> {
                            val substituted = substituteTemplateVars(token, arrayElements, i)
                            iterationArgs[name] = substituted
                        }
                    }
                }
                result.addAll(generateTemplateTokens(template, iterationArgs))
            }
            result
        } else {
            generateTemplateTokens(template, args)
        }
    }
    
    /**
     * Substitute ${varName} tokens in a GroupToken with values from the array.
     * For example, if token is "r${varName}" and num[i] = 24, returns "r24".
     */
    private fun substituteTemplateVars(token: Token, arrayElements: Map<String, List<MutableList<Token>>>, index: Int): Token {
        val group = token as? GroupToken ?: return token

        val hasTemplateVar = group.tokens.any { it is TemplateVarToken }
        if (!hasTemplateVar) {
            return token
        }

        val substituted = mutableListOf<Token>()
        for (t in group.tokens) {
            if (t is TemplateVarToken) {
                val varElements = arrayElements[t.name]
                if (varElements != null && index < varElements.size) {
                    substituted.addAll(varElements[index])
                } else {
                    substituted.add(t)
                }
            } else {
                substituted.add(t)
            }
        }
        
        // Join all tokens into a single identifier
        val joinedName = substituted.joinToString("") { it.str() }
        val firstToken = substituted.first()

        return when {
            scanner.isRegister(joinedName) -> {
                RegisterToken(joinedName, firstToken.location)
            }
            scanner.isDataType(joinedName) -> {
                DataTypeToken(joinedName, firstToken.location)
            }
            scanner.isAssemblerInstruction(joinedName) -> {
                AssemblerToken(joinedName, firstToken.location)
            }
            else -> {
                IdentifierToken(joinedName, firstToken.location)
            }
        }
    }
    
    private fun extractArrayElements(tokens: List<Token>): List<MutableList<Token>> {
        val elements = mutableListOf<MutableList<Token>>()
        val current = mutableListOf<Token>()
        var depth = 0

        for ((i, token) in tokens.withIndex()) {
            when {
                i == 0 -> {
                    if (token.isBracket('[')) {
                        continue
                    } else {
                        errorCharExpected('[', token)
                    }
                }
                i == tokens.size-1 -> {
                    if (token.isBracket(']')) {
                        continue
                    } else {
                        errorCharExpected(']', token)
                    }
                }

                token.isBracket('{') || token.isBracket('(') -> {
                    depth++
                    current.add(token)
                }
                token.isBracket('}') || token.isBracket(')') -> {
                    depth--
                    current.add(token)
                }
                token is CommaToken && depth == 0 -> {
                    elements.add(current.toMutableList())
                    current.clear()
                }
                else -> {
                    current.add(token)
                }
            }
        }
        if (current.isNotEmpty()) {
            elements.add(current.toMutableList())
        }
        return elements
    }
    
    private fun generateTemplateTokens(template: TemplateNode, args: Map<String, Token>): MutableList<Token> {
        val tokens = mutableListOf<Token>()
        for (t in template.body) {
            when (t) {
                is IdentifierToken -> {
                    val v = args[t.name]
                    if (v != null) {
                        val group = v as? GroupToken
                        tokens.addAll(group?.tokens ?: listOf(v))
                        continue
                    }
                    tokens.add(t)
                }
                is TemplateVarToken -> {
                    val v = args[t.name]
                    if (v != null) {
                        val group = v as? GroupToken
                        tokens.addAll(group?.tokens ?: listOf(v))
                    } else {
                        tokens.add(t)
                    }
                }
                else -> tokens.add(t)
            }
        }
        return tokens
    }
}