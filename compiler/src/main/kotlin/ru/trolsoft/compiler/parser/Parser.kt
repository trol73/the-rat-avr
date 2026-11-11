package ru.trolsoft.compiler.parser

import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.therat.lang.*

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

    protected fun nextMeaningToken(): Token {
        if (eof()) {
            errorUnexpectedEof(stream)
        }
        val t = stream.nextToken()
        return if (t is NewLineToken || t is LineCommentToken) {
            nextMeaningToken()
        } else {
            t
        }
    }

    protected fun nextIdentifier(): IdentifierToken {
        val t = nextToken()
        if (t !is IdentifierToken) {
            errorIdentifierExpected(t)
        }
        return t
    }

    protected fun nextIdentifierMultiline(): IdentifierToken {
        val t = nextToken()
        if (t is LineCommentToken || t is NewLineToken) {
            return nextIdentifierMultiline()
        } else if (t !is IdentifierToken) {
            errorIdentifierExpected(t)
        }
        return t
    }

    protected fun nextNumber(): NumberToken {
        val t = nextToken()
        if (t !is NumberToken) {
            errorNumberExpected(t)
        }
        return t
    }

    protected fun nextOperator(): Operator {
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

    protected fun nextKeyword(keyword1: String, keyword2: String): KeywordToken {
        val t = nextToken()
        if (t !is KeywordToken || (t.name != keyword1 && t.name != keyword2)) {
            errorKeywordExpected(t, keyword1, keyword2)
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
//                        next.isOperator("->") -> {
//                            val field = parsePrimary()
//                            BinaryOperNode(
//                                t.location, Operator.ARROW_RIGHT,
//                                IdentifierNode(t.location, t.name),
//                                field
//                            )
//                        }
                        else -> {
                            unreadLast()
                            IdentifierNode(t.location, t.name)
                        }
                    }
                }
            }
            t is NumberToken -> {
                val num = NumberNode(t.location, t.value, t.radix)
                if (eof()) {
                    return num
                }
                val next = nextToken()
                if (next.isBracket('[')) {
                    val index = parseExpression()
                    val closeBracket = nextToken()
                    if (!closeBracket.isBracket(']')) {
                        errorBracketExpected(closeBracket, ']')
                    }
                    IndexedNumNode(num, index)
                } else {
                    unreadLast()
                    num
                }
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
                if (operator == Operator.INC || operator == Operator.DEC) {
                    val expr = parsePrimary()
                    PrefixOperNode(t.location, operator, expr)
                } else if (operator.isPrefix() || operator.isMinusOrPlus()) {
                    val expr = parseExpression(operator)
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
                    // "const" is handled in parseCodeBlock, not in parsePrimary
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

    protected fun parseConst(constToken: Token): ConstValueNode {
        val loc = constToken.location
        // constToken is the 'const' keyword - we need to read the next token for the name
        val t = nextToken()
        // Allow IdentifierToken or RegisterToken (e.g., X, Y, Z are register pairs)
        val name: String = when (t) {
            is IdentifierToken -> t.name
            is RegisterToken -> t.name
            else -> errorIdentifierExpected(t)
        }
        checkOperator("=")
        val expr = parseExpression()
        // consume optional newline/comment
        if (!eof()) {
            val nt = nextToken()
            if (nt !is NewLineToken && nt !is LineCommentToken && nt !is BlockCommentToken) {
                unreadLast()
            }
        }
        return ConstValueNode(loc, name, expr)
    }


    protected fun parseExpression(prevOp: Operator? = null): Node {
        val left = parsePrimary()
        if (eof()) {
            return left
        }
        val next = nextToken()
        when {
            next is OperatorToken -> {
                val operator = Operator.fromToken(next)
                if (prevOp != null && prevOp.isPrefix() && prevOp.isMorePriorityThanRight(operator)) {
                    unreadLast()
                    return left
                } else if (prevOp != null && prevOp == Operator.LOGICAL_NOT && next.isOperator("->")) {
                    return BinaryOperNode(left.getLocation(), Operator.ARROW_RIGHT, left, parsePrimary())
                } else if (operator.isBinary()) {
                    unreadLast()
                    return parseBinaryOperationRight(null, left)
                } else if (operator.isSuffix()) {
                    return SuffixOperNode(operator, left)
                }
            }
//            left is NumberNode && next.isBracket('[') -> {
//                val index = parseExpression()
//                val closeBracket = nextToken()
//                if (!closeBracket.isBracket(']')) {
//                    errorBracketExpected(closeBracket, ']')
//                }
//                val indexedNode = IndexedNumNode(left, index)
//            }
        }
        if (left is IdentifierNode && left.name == "signed") {
            unreadLast()
            val expr = parseExpression()
            return SignedCastNode(left.getLocation(), expr)
        }
        unreadLast()
        return left
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
            if (t.isBracket(')') || t.isBracket(']') || t.isBracket('}') || t is CommaToken ||
                (t is KeywordToken && t.name == "else")) {
                unreadLast()
                return left
            }
            if (t !is OperatorToken) {
                errorOperatorExpected(t)
            }
            val operator = Operator.fromToken(t)

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
                } else if (nextToken.isBracket(')') || nextToken is CommaToken) {
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
            if (eof()) {
                break
            }
            val n = nextToken()
            if (n.isBracket('}')) {
                break
            } else if (n is NewLineToken || n is LineCommentToken) {
                continue
            }
            // Check for const declaration
            if (n is KeywordToken && n.name == "const") {
                // Parse: const <name> = <expr>
                val loc = n.location
                val nameT = nextToken()
                // Allow IdentifierToken or RegisterToken (e.g., X, Y, Z are register pairs)
                val name: String = when (nameT) {
                    is IdentifierToken -> nameT.name
                    is RegisterToken -> nameT.name
                    else -> errorIdentifierExpected(nameT)
                }
                checkOperator("=")
                val expr = parseExpression()
                // consume optional newline/comment
                if (!eof()) {
                    val nt = nextToken()
                    if (nt !is NewLineToken && nt !is LineCommentToken && nt !is BlockCommentToken) {
                        unreadLast()
                    }
                }
                list.add(ConstValueNode(loc, name, expr))
            } else {
                unreadLast()
                val next = parseExpression()
                list.add(next)
            }
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
        return if (t.isBracket('(')) {
            val cond = parseRoundBracketsExpr()
            val next = nextToken()
            if (next.isBracket('{')) {
                val body = parseCodeBlock()
                LoopNode(loc, cond, body)
            } else {
                unreadLast()
                skipWhitespace()
                val body = parseExpression()
                LoopNode(loc, cond, body)
            }
        } else if (t.isBracket('{')) {
            val body = parseCodeBlock()
            LoopNode(loc, null, body)
        } else {
            unreadLast()
            val body = parseExpression()
            LoopNode(loc, null, body)
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
        val elseToken2 = if (elseToken is NewLineToken && !eof()) nextToken() else null

        return if (elseToken.isKeyword("else") || elseToken2 != null && elseToken2.isKeyword("else")) {
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
                args.isEmpty() -> {
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