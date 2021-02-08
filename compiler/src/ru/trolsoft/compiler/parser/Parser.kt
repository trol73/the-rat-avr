package ru.trolsoft.compiler.parser

import ru.trolsoft.therat.lang.*
import ru.trolsoft.compiler.lexer.TokenStream

abstract class Parser(val stream: TokenStream) {

    abstract fun parse(): Node
    abstract fun parseFuncCallArgument(): Node

    protected fun eof(): Boolean = stream.eof()
    protected fun unreadLast() = stream.unreadLast()

    protected fun nextToken(): Token {
        if (eof()) {
            errorUnexpectedEof(stream)
        }
        return stream.nextToken()
    }

    protected fun nextIdentifier(): IdentifierToken {
        val t = nextToken()
        if (t !is IdentifierToken) {
            errorIdentifierExpected(t)
        }
        return t
    }

    private fun nextOperator(): Operator {
        val t = nextToken()
        if (t !is OperatorToken) {
            errorOperatorExpected(t)
        }
        return Operator.fromToken(t)
    }

    protected fun checkColon() {
        val t = nextToken()
        if (t !is ColonToken) {
            errorCharExpected(':', t)
        }
    }

    protected fun checkOperator(op: String) {
        val t = nextToken()
        if (t !is OperatorToken) {
            errorOperatorExpected(op, t)
        }
    }

    protected fun checkBracket(bracket: Char): Token {
        val t = nextToken()
        if (!t.isBracket(bracket)) {
            errorCharExpected(bracket, t)
        }
        return t
    }

    protected fun skipWhitespace() {
        if (eof()) {
            return
        }
        while (!eof()) {
            val t = nextToken()
            if (t is NewLineToken || t is LineCommentToken || t is BlockCommentToken) {
                continue
            } else {
                unreadLast()
                break
            }
        }

    }
    protected fun checkNewLineOrComment() {
        if (eof()) {
            return
        }
        val t = stream.nextToken()
        if (t is NewLineToken || t is LineCommentToken || t is BlockCommentToken) {
            return
        }
        errorUnexpectedToken(t)
    }

    protected fun nextRegister(): RegisterToken {
        val t = nextToken()
        if (t !is RegisterToken) {
            errorRegisterExpected(t)
        }
        return t
    }

    protected fun nextKeyword(keyword: String): KeywordToken {
        val t = nextToken()
        if (t !is KeywordToken || t.name != keyword) {
            errorKeywordExpected(t, keyword)
        }
        return t
    }

    protected fun nextBracket(ch: Char){
        val t = nextToken()
        if (!t.isBracket(ch)) {
            errorBracketExpected(t, ch)
        }
    }


    open fun parsePrimary(): Node {
        val t = nextToken()
        return when {
            t is IdentifierToken -> {
                if (eof()) {
                    IdentifierNode(t.location, t.name)
                } else {
                    val next = nextToken()
                    when {
                        next.isBracket('(') -> {
                            parseFunctionCall(t)
                        }
                        next.isBracket('[') -> {
                            val index = parseSquareBracketsExpr()
                            val name = IdentifierNode(t.location, t.name)
                            IndexedNode(name, index)
                        }
                        next is DotToken -> {
                            parseDottedGroup(t)
                        }
                        else -> {
                            unreadLast()
                            IdentifierNode(t.location, t.name)
                        }
                    }
                }
            }
            t is NumberToken -> {
                NumberNode(t.location, t.value, t.radix)
            }
            t.isBracket('(') -> {
                parseRoundBracketsExpr()
            }
            t is RegisterToken -> {
                val res = parseRegisterOrGroup(t)
                if (res is RegisterNode && !eof()) {
                    val next = nextToken()
                    if (next.isBracket('[')) {
                        val index = parseSquareBracketsExpr()
                        IndexedRegNode(res, index)
                    } else {
                        unreadLast()
                        res
                    }
                } else {
                    res
                }
            }
            t is OperatorToken -> {
                val operator = Operator.fromToken(t)
                if (operator.isPrefix() || operator.isMinusOrPlus()) {
                    val expr = parseExpression()
                    if (expr is NumberNode && operator == Operator.MINUS) {
                        NumberNode(expr.getLocation(), -expr.value, expr.radix)
                    } else if (expr is NumberNode && operator == Operator.PLUS) {
                        expr
                    } else {
                        PrefixOperNode(t.location, operator, expr)//parsePrimary())
                    }
                } else {
                    errorUnexpectedToken(t)
                }
            }
            t is KeywordToken -> {
                when (t.name) {
                    "loop" -> parseLoop(t)
                    "if" -> parseIf(t)
                    "goto" -> parseGoto(t)
                    "do" -> parseDoWhile(t)
                    "saveregs" -> parseSaveregs(t)
                    "break" -> BreakNode(t.location)
                    "continue" -> ContinueNode(t.location)
                    else -> parseKeyword(t)
                }
            }
            t is AssemblerToken -> {
                parseAssemblerInstruction(t)
            }
            t is LabelToken -> {
                LabelNode(t.location, t.name)
            }
            t is GlobalLabelToken -> {
                LabelNode(t.location, t.name, true)
            }

//            t is BlockCommentToken -> {
//
//            }
            t is NewLineToken || t is LineCommentToken || t is BlockCommentToken -> {
                parsePrimary()
            }
            t is CharLiteralToken -> {
                CharLiteralNode(t.location, t.char)
            }
            t is StringLiteralToken -> {
                StringLiteralNode(t.location, t.str)
            }
            t is DataTypeToken -> {
                when (t.name) {
                    "byte", "word" -> parseDataBlock(t)
                    else -> errorUnexpectedToken(t)
                }
            }
            else -> {
                errorUnexpectedToken(t)
            }
        }
    }

    protected open fun parseKeyword(t: KeywordToken): Node {
        errorUnexpectedKeyword(t)
    }


    protected fun parseExpression(): Node {
        val left = parsePrimary()
        if (eof()) {
            return left
        }
        val next = nextToken()
//        while (next is NewLineToken) {
//            next = nextToken()
//        }
        when (next) {
            is OperatorToken -> {
                val operator = Operator.fromToken(next)
                if (operator.isBinary()) {
                    unreadLast()
                    return parseBinaryOperationRight(null, left)
                } else if (operator.isSuffix()) {
                    return SuffixOperNode(operator, left)
                }
            }
        }
        if (left is IdentifierNode && left.name == "signed") {
            unreadLast()
            val expr = parseExpression()
            return SignedCastNode(left.getLocation(), expr)
        }
        unreadLast()
        return left
        //errorUnexpectedToken(next)
        //return parseBinaryOperationRight(0, left)

    }

    protected fun parseRegisterOrGroup(firstReg: RegisterToken): Node {
        if (eof()) {
            return RegisterNode(firstReg.location, firstReg.name)
        }
        val next = nextToken()
        if (next !is DotToken) {
            unreadLast()
            return RegisterNode(firstReg.location, firstReg.name)
        }
        val list = getDottedList(firstReg)
        var onlyRegs = true
        for (t in list) {
            if (t !is RegisterToken) {
                onlyRegs = false
                break
            }
        }
        return if (onlyRegs) {
            val regs = mutableListOf<String>()
            for (reg in list) {
                regs.add((reg as RegisterToken).name)
            }
            RegisterGroupNode(firstReg.location, regs)
        } else {
            buildDottedGroup(list)
        }
    }

    private fun getDottedList(first: Token): List<Token> {
        val list = mutableListOf(first)
        while (true) {
            val reg = nextToken()
            if (reg !is RegisterToken && reg !is IdentifierToken) {
                errorRegisterExpected(reg)
            }
            //val reg = nextRegister()
            list.add(reg)
            if (eof()) {
                break
            }
            val next = nextToken()
            if (next !is DotToken) {
                unreadLast()
                break
            }
        }
        return list
    }

    private fun buildDottedGroup(list: List<Token>): DotGroupNode {
        val items = mutableListOf<Node>()
        for (item in list) {
            if (item is RegisterToken) {
                val reg = RegisterNode(item.location, item.name)
                items.add(reg)
            } else {
                val identifier = item as IdentifierToken
                val node = IdentifierNode(item.location, identifier.name)
                items.add(node)
            }
        }
        return DotGroupNode(items)
    }

    protected fun parseDottedGroup(first: IdentifierToken): DotGroupNode {
        val list = getDottedList(first)
        return buildDottedGroup(list)
    }

    protected fun parseIdentifierOrGroup(first: IdentifierToken): Node {
        val checkDot = nextToken()
        return if (checkDot is DotToken) {
            parseDottedGroup(first)
        } else {
            unreadLast()
            IdentifierNode(first.location, first.name)
        }
    }


    private fun parseBinaryOperationRight(prevOperator: Operator?, leftArg: Node): Node {
        var left = leftArg

        var end = false
        while (true) {
            val t = nextToken()
            if (t is NewLineToken || t is LineCommentToken || t is BlockCommentToken) {
                unreadLast()
                return left
            }
            if (t.isBracket(')') || t is CommaToken) {
                unreadLast()
                return left
            }
            if (t !is OperatorToken) {
                errorOperatorExpected(t)
            }
            val operator = Operator.fromToken(t)

//            val operator = nextOperator()

            // If this is a binop that binds at least as tightly as the current binop, consume it, otherwise we are done.
            if (prevOperator != null && prevOperator.isMorePriorityThanRight(operator)) {
                unreadLast()
                return left
            }
            var right = parsePrimary()

            // If BinOp binds less tightly with RHS than the operator after RHS, let the pending operator take RHS as its LHS.
            if (!eof()) {
                val nextToken = nextToken()
                unreadLast()
                if (nextToken is OperatorToken) {
                    val nextOperator = Operator.fromToken(nextToken)
                    if (nextOperator.isMorePriorityThanLeft(operator)) {
                        if (nextOperator.isSuffix()) {
                            right = SuffixOperNode(nextOperator, right)
                            nextToken()
                            end = true
                        } else {
                            right = parseBinaryOperationRight(operator, right)
                        }
                    }
                } else if (nextToken.isBracket(')')) {
                    end = true
                } else if (nextToken is NewLineToken || nextToken is LineCommentToken || nextToken is BlockCommentToken) {
                    end = true
                }
            } else {
                end = true
            }

            // Merge LHS/RHS
            left = BinaryOperNode(t.location, operator, left, right)
            if (end || eof()) {
                return left
            }
        }
    }

    private fun parseRoundBracketsExpr(): Node {
        val v = parseExpression()
        val t = nextToken()
        if (!t.isBracket(')')) {
            errorCharExpected(')', t)
        }
        return v
    }

    private fun parseSquareBracketsExpr(): Node {
        val v = parseExpression()
        val t = nextToken()
        if (!t.isBracket(']')) {
            errorCharExpected(']', t)
        }
        return v
    }

    protected fun parseCodeBlock(): CompoundStmt {
        val list = mutableListOf<Node>()
        while (true) {
            val n = nextToken()
            if (n.isBracket('}')) {
                break
            } else if (n is NewLineToken || n is LineCommentToken) {
                continue
            }
            unreadLast()
            val next = parseExpression()
            list.add(next)
        }
        return CompoundStmt(list)
    }

    protected fun parseArraySize(): Node? {
        if (!stream.eof()) {
            val next = stream.nextToken()
            if (next.isBracket('[')) {
                val result = parseExpression()
                checkBracket(']')
                return result
            } else {
                unreadLast()
            }
        }
        return null
    }


    protected fun parseLoop(loopToken: Token): LoopNode {
        val loc = loopToken.location
        val t = nextToken()
        when {
            t.isBracket('(') -> {
                val cond = parseRoundBracketsExpr()
                val next = nextToken()
                return if (next.isBracket('{')) {
                    val body = parseCodeBlock()
                    LoopNode(loc, cond, body)
                } else {
                    unreadLast()
                    skipWhitespace()
                    val body = parseExpression()
                    LoopNode(loc, cond, body)
                }
            }
            t.isBracket('{') -> {
                val body = parseCodeBlock()
                return LoopNode(loc, null, body)
            }
            else -> {
                unreadLast()
                val body = parseExpression()
                return LoopNode(loc, null, body)
            }
        }
    }

    protected fun parseIf(ifToken: Token): IfNode {
        checkBracket('(')
        val cond = parseRoundBracketsExpr()
        val next = nextToken()
        val thenCode = if (next.isBracket('{')) {
            parseCodeBlock()
        } else {
            unreadLast()
            parseExpression()
        }

        if (eof()) {
            return IfNode(ifToken.location, cond, thenCode, null)
        }
        val elseToken = nextToken()
        return if (elseToken.isKeyword("else")) {
            val t = nextToken()
            val elseCode = if (t.isBracket('{')) {
                parseCodeBlock()
            } else {
                unreadLast()
                parseExpression()
            }
            IfNode(ifToken.location, cond, thenCode, elseCode)
        } else {
            unreadLast()
            IfNode(ifToken.location, cond, thenCode, null)
        }
    }

    private fun parseGoto(gotoToken: Token): GotoNode {
        return GotoNode(gotoToken.location, nextIdentifier().name)
    }

    private fun parseDoWhile(doToken: Token): DoWhileNode {
        nextBracket('{')
        val body = parseCodeBlock()
        nextKeyword("while")
        checkBracket('(')
        val cond = parseRoundBracketsExpr()
        return DoWhileNode(doToken.location, body, cond)
    }

    protected fun parseFunctionCall(nameToken: IdentifierToken): FunctionCallNode {
        val args = mutableListOf<Node>()
        while (true) {
            val t = nextToken()
            when {
                t.isBracket(')') -> {
                    return FunctionCallNode(nameToken.location, nameToken.name, args)
                }
                args.size == 0 -> {
                    unreadLast()
                    args.add(parseFuncCallArgument())
                }
                t is CommaToken -> {
                    args.add(parseFuncCallArgument())
                }
                else -> errorCharExpected(')', t)
            }
        }
    }

    private fun parseSaveregs(nameToken: KeywordToken): SaveregsNode {
        checkBracket('(')
        val args = mutableListOf<Node>()
        while (true) {
            val t = nextToken()
            when {
                t.isBracket(')') -> {
                    checkBracket('{')
                    val body = parseCodeBlock()
                    return SaveregsNode(nameToken.location, args, body)
                }
                args.size == 0 -> {
                    unreadLast()
                    args.add(parsePrimary())
                }
                t is CommaToken -> {
                    args.add(parsePrimary())
                }
                else -> errorCharExpected(')', t)
            }
        }
    }

    protected open fun parseAssemblerInstruction(cmd: AssemblerToken): Node {
        if (eof()) {
            return AssemblerInstrNode(cmd.location, cmd.name, null)
        }
        val next = nextToken()
        if (next is NewLineToken || next is LineCommentToken) {
            return AssemblerInstrNode(cmd.location, cmd.name, null)
        }
        unreadLast()
        val firstOp = parseExpression()
        val args = mutableListOf(firstOp)
        while (true) {
            if (eof()) {
                return AssemblerInstrNode(cmd.location, cmd.name, args)
            }
            val commaExpected = asmRequiresComma(cmd.name, args)
            if (!commaExpected && eof()) {
                return AssemblerInstrNode(cmd.location, cmd.name, args)
            }
            val next2 = nextToken()
            if (next2 is NewLineToken || next2 is LineCommentToken) {
                return AssemblerInstrNode(cmd.location, cmd.name, args)
            }
            if (commaExpected) {
                if (next2 !is CommaToken) {
                    errorUnexpectedToken(next2)
                }
            } else {
                unreadLast()
            }
            val nextArg = parseExpression()
            args.add(nextArg)
        }
    }

    open fun parseDataBlock(t: DataTypeToken): DataBlockNode {
        errorUnexpectedToken(t)
    }

    protected open fun asmRequiresComma(cmd: String, args: List<Node>): Boolean {
        return true
    }



}