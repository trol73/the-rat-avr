package ru.trolsoft.compiler.parser

import ru.trolsoft.compiler.compiler.errorArrayOfPrimitivesExpected
import ru.trolsoft.compiler.compiler.errorStructuredFieldNotFound
import ru.trolsoft.compiler.compiler.errorWrongArraySize
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.general.*
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.therat.lang.*


abstract class RatParser(stream: TokenStream) : Parser(stream) {
    protected val structures = mutableMapOf<String, StructuredData>()

    protected abstract fun parseDataType(t: DataTypeToken): DataType

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
                    val node = parseExpression()//parsePrimary()
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

}