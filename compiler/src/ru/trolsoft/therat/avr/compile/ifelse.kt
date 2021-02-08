package ru.trolsoft.therat.avr.compile

import ru.trolsoft.compiler.compiler.decomposeIf
import ru.trolsoft.compiler.compiler.errorWrongExpression
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.avr.*

internal fun compileIf(compiler: AvrRatCompiler, ifNode: IfNode) {
    val node = resolveConditionAndBreakAndContinue(compiler, ifNode)

    if (node.thenBlock is GotoNode) {
        val baseLabel = compiler.resolveProcLabel("if", node.getLocation(), true)
        val decomposed = mutableListOf<Node>()
        decomposeIf(node, baseLabel, decomposed)
        for (n in decomposed) {
            when (n) {
                is LabelNode ->
                    //compiler.addProcLabel(n.name, n.getLocation(), true)
                    compiler.addLabel(n.name, n.getLocation())
                is IfNode ->
                    compileSimpleIfGoto(compiler, n)
                else ->
                    internalError()
            }
        }
    } else if (node.thenBlock is CompoundStmt && !node.hasElseBlock()) {
        val endLabel = compiler.resolveProcLabel("if_end", node.getLocation(), true)
        val ifGotoNode = IfNode(
                node.getLocation(),
                PrefixOperNode(node.cond.getLocation(), Operator.LOGICAL_NOT, node.cond),
                GotoNode(node.getLocation(), endLabel),
                null
        )
        compileIf(compiler, ifGotoNode)
        compiler.compileNode(node.thenBlock)
        compiler.addLabel(endLabel, node.getLocation())
        // if (expr) {
        //    b1
        // }
        // if (!expr) goto end
        //   b1
        // end:
    } else if (!node.hasThenBlock() && node.elseBlock != null && node.hasElseBlock()) {
        val endLabel = compiler.resolveProcLabel("if_end", node.getLocation(), true)
        val ifGotoNode = IfNode(
                node.getLocation(),
                node.cond,
                GotoNode(node.getLocation(), endLabel),
                null
        )
        compileIf(compiler, ifGotoNode)
        compiler.compileNode(node.elseBlock)
        compiler.addLabel(endLabel, node.getLocation())
        // if (expr) {
        // } else {
        //   b2
        // }
        // if (expr) goto end
        //   b2
        // end:
    } else if (node.thenBlock is CompoundStmt && node.elseBlock != null) {
        val elseLabel = compiler.resolveProcLabel("if_else", node.getLocation(), true)
        val endLabel = compiler.resolveProcLabel("if_end", node.getLocation(), true)
        val ifGotoNode = IfNode(
                node.getLocation(),
                PrefixOperNode(node.cond.getLocation(), Operator.LOGICAL_NOT, node.cond),
                GotoNode(node.getLocation(), elseLabel),
                null
        )
        compileIf(compiler, ifGotoNode)
        compiler.compileNode(node.thenBlock)
        compiler.compileJump("rjmp", endLabel, node.thenBlock.getLocation())
        compiler.addLabel(elseLabel, node.getLocation())
        compiler.compileNode(node.elseBlock)
        compiler.addLabel(endLabel, node.getLocation())
        // if (expr) {
        //    b1
        // } else {
        //    b2
        // }
        // if (!expr) goto l2
        //   b1
        //   goto end
        // l2:
        //   b2
        // end:
    } else if (node.cond is RegisterBitNode && node.thenBlock is AssemblerInstrNode && node.thenBlock.getInstructionSize() == 2
            // if (reg[bit) asm_instr
            && node.elseBlock == null) {
        compiler.compileAsmRegNum("sbrc", node.cond.reg, node.cond.bit, node.cond.getLocation())
        compiler.compileNode(node.thenBlock)
    } else if (node.cond is RegisterBitNode && node.thenBlock is BinaryOperNode && node.elseBlock == null) {
        compiler.compileAsmRegNum("sbrc", node.cond.reg, node.cond.bit, node.cond.getLocation())
        compiler.compileSizeLimitedNode(node.thenBlock, 1, 2)
    } else if (node.cond is PrefixOperNode && node.cond.operator == Operator.LOGICAL_NOT && node.cond.expression is RegisterBitNode
            && node.thenBlock is AssemblerInstrNode && node.elseBlock == null) {
        // if (!reg[bit) asm_instr
        compiler.compileAsmRegNum("sbrs", node.cond.expression.reg, node.cond.expression.bit, node.cond.getLocation())
        compiler.compileNode(node.thenBlock)
    } else if (node.cond.isLowIoBit() && node.elseBlock == null && node.thenBlock.isSingleWordCommand()) {
        val ioBit = node.cond as IoPortBitNode
        compileIfIoBitExpression(compiler, ioBit, node.cond.getLocation())
        compiler.compileOneWordNode(node.thenBlock)
    } else if (node.cond.isInvertedLowIoBit() && node.elseBlock == null && node.thenBlock.isSingleWordCommand()) {
        val ioBit = (node.cond as PrefixOperNode).expression as IoPortBitNode
        compileIfIoBitExpression(compiler, ioBit, node.cond.getLocation(), true)
        compiler.compileOneWordNode(node.thenBlock)
    } else if (node.elseBlock == null) {
        if (node.cond is BinaryOperNode && node.cond.operator == Operator.NOT_EQ && node.cond.left is RegisterNode && node.cond.right is RegisterNode) {
            if (node.thenBlock is AssemblerInstrNode) {
                compiler.compileAsmRegReg("cpse", node.cond.left, node.cond.right, node.cond.getLocation())
                compiler.compileOneWordNode(node.thenBlock)
                return
            }
        }
        val endLabel = compiler.resolveProcLabel("if_end", node.getLocation(), true)
        val ifGotoNode = IfNode(
                node.getLocation(),
                PrefixOperNode(node.cond.getLocation(), Operator.LOGICAL_NOT, node.cond),
                GotoNode(node.getLocation(), endLabel),
                null
        )
        compileIf(compiler, ifGotoNode)
        compiler.compileNode(node.thenBlock)
        compiler.addLabel(endLabel, node.getLocation())
    } else {
        TODO("${node.thenBlock} ${node.elseBlock}")
    }

    // if (expr) goto
    // if (expr) single_op
    // if (expr) {
    // }
    // if (expr) {
    //    b1
    // } else {
    //    b2
    // }
}

fun resolveConditionAndBreakAndContinue(compiler: AvrRatCompiler, ifNode: IfNode): IfNode {
    val thenB = ifNode.thenBlock
    val elseB = ifNode.elseBlock
    val cond = compiler.resolveArg(ifNode.cond)
    val resolvedThen = when (thenB) {
        is BreakNode -> {
            val endLabel = compiler.getCycleEndLabel() ?: errorParentCycleNotFound(thenB)
            GotoNode(thenB.getLocation(), endLabel)
        }
        is ContinueNode -> {
            val startLabel = compiler.getCycleStartLabel() ?: errorParentCycleNotFound(thenB)
            GotoNode(thenB.getLocation(), startLabel)
        }
        is BinaryOperNode -> {
            BinaryOperNode(thenB.getLocation(), thenB.operator, compiler.resolveArg(thenB.left), compiler.resolveArg(thenB.right))
        }
        else -> thenB
    }
    val resolvedElse = when (elseB) {
        is BreakNode -> {
            val endLabel = compiler.getCycleEndLabel() ?: errorParentCycleNotFound(elseB)
            GotoNode(elseB.getLocation(), endLabel)
        }
        is ContinueNode -> {
            val startLabel = compiler.getCycleStartLabel() ?: errorParentCycleNotFound(elseB)
            GotoNode(elseB.getLocation(), startLabel)
        }
        is BinaryOperNode -> {
            BinaryOperNode(elseB.getLocation(), elseB.operator, compiler.resolveArg(elseB.left), compiler.resolveArg(elseB.right))
        }
        else -> elseB
    }
    return IfNode(
            ifNode.getLocation(),
            cond,
            resolvedThen,
            resolvedElse
    )
}

private fun compileSimpleIfGoto(compiler: AvrRatCompiler, node: IfNode) {
    val cond = compiler.resolveArg(node.cond)
    val label = (node.thenBlock as GotoNode).label
    val thenLoc = node.thenBlock.getLocation()

    val signed = cond is SignedCastNode
    val condition = if (cond is SignedCastNode) {
        cond.expr
    } else {
        cond
    }
    if (condition is BinaryOperNode) {
        val left = compiler.resolveArg(condition.left)
        val right = compiler.resolveArg(condition.right)
        if (left is RegisterNode && right is NumberNode) {
            compileIfRegValue(compiler, condition.operator, left, right, label, signed, node.getLocation())
        } else if (left is RegisterNode && right is RegisterNode) {
            compileIfRegRegOrGroups(compiler, condition.operator, left, right, label, signed, node.getLocation())
        } else if (left is RegisterGroupNode && right is RegisterGroupNode) {
            compileIfRegRegOrGroups(compiler, condition.operator, left, right, label, signed, node.getLocation())
        } else {
            TODO("l=$left r=$right\n")
        }
    } else if (condition is RegisterBitNode) {
        compileIfRegisterBitExpression(compiler, condition)
        compiler.compileJump("rjmp", label, thenLoc)
    } else if (condition is IoPortBitNode) {
        if (condition.port.name == "SREG") {
            compileIfFlagExpression(compiler, condition.bitIndex, false, condition.getLocation(), label)
        } else {
            compileIfIoBitExpression(compiler, condition, node.getLocation())
            compiler.compileJump("rjmp", label, thenLoc)
        }
    } else if (condition is PrefixOperNode && condition.operator == Operator.LOGICAL_NOT) {
        val expr = compiler.resolveArg(condition.expression)
        when (expr) {
            is RegisterBitNode -> {
                compileIfRegisterBitExpression(compiler, expr, true)
                compiler.compileJump("rjmp", label, thenLoc)
            }
            is IoPortBitNode -> {
                if (expr.port.name == "SREG") {
                    compileIfFlagExpression(compiler, expr.bitIndex, true, expr.getLocation(), label)
                } else {
                    compileIfIoBitExpression(compiler, expr, node.getLocation(), true)
                    compiler.compileJump("rjmp", label, thenLoc)
                }
            }
            is BinaryOperNode -> {
                val left = compiler.resolveArg(expr.left)
                val right = compiler.resolveArg(expr.right)
                val invOp = inverseBinaryCompareOperation(expr.operator, expr.getLocation())
                if (left is RegisterNode && right is NumberNode) {
                    compileIfRegValue(compiler, invOp, left, right, label, signed, node.getLocation())
                } else if (left is RegisterNode && right is RegisterNode) {
                    compileIfRegRegOrGroups(compiler, invOp, left, right, label, signed, node.getLocation())
                } else if (left is RegisterGroupNode && right is RegisterGroupNode) {
                    compileIfRegRegOrGroups(compiler, invOp, left, right, label, signed, node.getLocation())
                } else {
                    TODO("l=$left r=$right\n")
                }
            }
            is SignedCastNode -> {
                if (expr.expr !is BinaryOperNode) {
                    errorWrongExpression(expr)
                }
                val left = compiler.resolveArg(expr.expr.left)
                val right = compiler.resolveArg(expr.expr.right)
                val invOp = inverseBinaryCompareOperation(expr.expr.operator, expr.expr.getLocation())
                if (left is RegisterNode && right is NumberNode) {
                    compileIfRegValue(compiler, invOp, left, right, label, true, node.getLocation())
                } else if (left is RegisterNode && right is RegisterNode) {
                    compileIfRegRegOrGroups(compiler, invOp, left, right, label, true, node.getLocation())
                } else if (left is RegisterGroupNode && right is RegisterGroupNode) {
                    compileIfRegRegOrGroups(compiler, invOp, left, right, label, true, node.getLocation())
                } else {
                    TODO("l=$left r=$right\n")
                }
            }
            else ->
                TODO("$expr\n")
        }
    } else {
        TODO("$condition\n")
    }

}

fun compileIfRegRegOrGroups(compiler: AvrRatCompiler, operator: Operator, left: Node, right: Node, label: String,
                            signed: Boolean, location: SourceLocation) {
    if (operator == Operator.GREAT) {
        // r1 > r2 -> r2 < r1
        compileIfRegRegOrGroups(compiler, Operator.LESS, right, left, label, signed, location)
        return
    } else if (operator == Operator.LESS_EQ) {
        // r1 <= r2 -> r2 >= r1
        compileIfRegRegOrGroups(compiler, Operator.GREAT_EQ, right, left, label, signed, location)
        return
    }
    if (left is RegisterNode && right is RegisterNode) {
        if (operator == Operator.NOT_EQ) {
            compiler.compileAsmRegReg("cpse", left, right, location)
            compiler.compileJump("rjmp", label, location)
            return
        } else {
            addCompareInstruction(compiler, left, right, location)
        }
    } else if (left is RegisterGroupNode && right is RegisterGroupNode) {
        addCompareInstruction(compiler, left, right, location)
    } else {
        internalError()
    }

    when (operator) {
        Operator.EQ ->
            compiler.compileJump("breq", label, location)
        Operator.NOT_EQ ->
            compiler.compileJump("brne", label, location)
        Operator.LESS -> {
            when {
                signed ->
                    compiler.compileJump("brlt", label, location)
                else ->
                    compiler.compileJump("brlo", label, location)
            }
        }
        Operator.GREAT_EQ -> {
            when {
                signed ->
                    compiler.compileJump("brge", label, location)
                else ->
                    compiler.compileJump("brsh", label, location)
            }
        }
        else ->
            errorUnsupportedOperation(location)
    }
}

private fun inverseBinaryCompareOperation(op: Operator, loc: SourceLocation): Operator {
    return when (op) {
        Operator.EQ -> Operator.NOT_EQ
        Operator.NOT_EQ -> Operator.EQ
        Operator.GREAT -> Operator.LESS_EQ
        Operator.LESS -> Operator.GREAT_EQ
        Operator.LESS_EQ -> Operator.GREAT
        Operator.GREAT_EQ -> Operator.LESS
        Operator.ARROW_RIGHT -> errorWrongExpression(loc)
        else -> internalError()
    }
}

private fun addCompareInstruction(compiler: AvrRatCompiler, left: Node, right: Node, location: SourceLocation) {
    if (left is RegisterNode && right is NumberNode && right.value == 0) {
        compiler.compileAsmReg("tst", left, location)
    } else if (left is RegisterNode && right is RegisterNode) {
        compiler.compileAsmRegReg("cp", left, right, location)
    } else if (left is RegisterNode && right is NumberNode) {
        compiler.compileAsmRegNum("cpi", left, right, location)
    } else if (left is RegisterGroupNode && right is RegisterGroupNode) {
        if (left.size() != right.size()) {
            errorSizeMismatch(left.getLocation(), left.size(), right.size())
        }
        for (i in 0 until left.size()) {
            val j = left.size() - 1 - i
            val reg1 = left.buildRegisterNode(j)
            val reg2 = right.buildRegisterNode(j)
            val cmd = if (i == 0) "cp" else "cpc"
            compiler.compileAsmRegReg(cmd, reg1, reg2, location)
        }
    } else {
        errorUnsupportedOperation(left)
    }
}

private fun compileIfRegValue(compiler: AvrRatCompiler, operator: Operator, left: RegisterNode, right: NumberNode, label: String,
                                      signed: Boolean, location: SourceLocation) {
    //  BRCS = BRLO, BRCC = BRSH
    when (operator) {
        Operator.EQ -> {
            addCompareInstruction(compiler, left, right, location)
            compiler.compileJump("breq", label, location)
        }
        Operator.NOT_EQ -> {
            addCompareInstruction(compiler, left, right, location)
            compiler.compileJump("brne", label, location)
        }
        Operator.LESS -> {
            addCompareInstruction(compiler, left, right, location)
            when {
                right.value == 0 ->
                    compiler.compileJump("brmi", label, location)
                signed ->
                    compiler.compileJump("brlt", label, location)
                else ->
                    compiler.compileJump("brlo", label, location)
            }
        }
        Operator.GREAT_EQ -> {
            addCompareInstruction(compiler, left, right, location)
            when {
                right.value == 0 ->
                    compiler.compileJump("brpl", label, location)
                signed ->
                    compiler.compileJump("brge", label, location)
                else ->
                    compiler.compileJump("brsh", label, location)
            }
        }
        Operator.GREAT -> {
            // x > k  ->   x >= k+1
            compileIfRegValue(compiler, Operator.GREAT_EQ, left, NumberNode(right.getLocation(), right.value+1), label, signed, location)
        }
        Operator.LESS_EQ -> {
            // x <= k -> x < k+1
            compileIfRegValue(compiler, Operator.LESS, left, NumberNode(right.getLocation(), right.value+1), label, signed, location)
        }
        else ->
            errorUnsupportedOperation(location)
    }
}

private fun compileIfRegisterBitExpression(compiler: AvrRatCompiler, node: RegisterBitNode, inverse: Boolean = false) {
    val cmd = if (inverse) "sbrs" else "sbrc"
    compiler.compileAsmRegNum(cmd, node.reg, node.bit, node.getLocation())
}

internal fun compileIfIoBitExpression(compiler: AvrRatCompiler, node: IoPortBitNode, loc: SourceLocation, inverse: Boolean = false) {
    val cmd = if (inverse) "sbis" else "sbic"
    val asmArgs = listOf(
            NumberNode(loc, node.port.address, 16),
            NumberNode(loc, node.bitIndex, 2)
    )
    val cmdNode = AssemblerInstrNode(loc, cmd, asmArgs)
    compiler.compileAssembler(cmdNode)
}

fun compileIfFlagExpression(compiler: AvrRatCompiler, bitIndex: Int, inverse: Boolean, loc: SourceLocation, label: String) {
    val cmd = if (inverse) "brbc" else "brbs"
    val args = listOf(
            NumberNode(loc, bitIndex, 10),
            IdentifierNode(loc, label)
    )
    val cmdNode = AssemblerInstrNode(loc, cmd, args)
    compiler.compileAssembler(cmdNode)

//BRBC s, A	Переход если флаг S сброшен	Если SREG(S) = 0	—
//BRBS s, A
}


private fun Node.isSingleWordCommand(): Boolean {
    if (this is AssemblerInstrNode && this.getInstructionSize() == 2) {
        return true
    }
    if (this is BinaryOperNode && left !is BinaryOperNode && right !is BinaryOperNode) {
        return true
    }
    return false
}

private fun Node.isLowIoBit(): Boolean {
    return this is IoPortBitNode && this.port.address <= 31
}

private fun Node.isInvertedLowIoBit(): Boolean {
    return this is PrefixOperNode && operator == Operator.LOGICAL_NOT && expression is IoPortBitNode && expression.port.address <= 31
}

private fun Node.isSimpleCompare(): Boolean {
    return this is BinaryOperNode && (operator == Operator.EQ || operator == Operator.NOT_EQ ||
            operator == Operator.GREAT || operator == Operator.GREAT_EQ ||
            operator == Operator.LESS|| operator == Operator.LESS_EQ)
}

/*
; 		if (rmp.r0.ZH.ZL >= rDiv4.rDiv3.rDiv2.rDiv1) {		; compare with divident
cp	ZL, R5		; compare with divident
		cpc	ZH, R6		; compare with divident
		cpc	r0, R7		; compare with divident
		cpc	R16, R8		; compare with divident
		brlo	Divide__if_682		; compare with divident

if (a && b) goto lbl        -> if (!a) goto @end    if (b) goto lbl     @end:
if (a || b) goto lbl        -> if (a) goto lbl      if (b) goto lbl

if (!(a && b)) goto lbl = if (!a || !b) goto lbl
if (!(a || b)) goto lbl = if (!a && !b) goto lbl


if (!(a && b)) goto lbl     -> if (!a) goto lbl     if (!b) goto lbl
if (!(a || b)) goto lbl     -> if (a) goto @end     if (!b) goto lbl    @end:




if ((a || b) && c) goto lbl     -> if (a) goto @l1      if (!b) goto @end   @l1: if (c) goto lbl
if ((a && b) || c) goto lbl     -> if (!a) goto @l1     if (b) goto @end    @l1  if (c) goto lbl

if (!(a||b)) goto @end  -> if (!a && !b) goto @end -> if (a) goto @end1    if (!b) goto @end    @end1
if (c) goto lbl


@Throws(SyntaxException::class)
private fun compileIfExpression(expr: Expression, inverse: Boolean, isBlock: Boolean): Boolean {
    if (!expr.getFirst().isKeyword("if")) {
        throw RuntimeException("'if' not found")
    }
    checkExpressionMinLength(expr, 5)
    expr.removeFirst() // if
    val signed = checkSignedIf(expr)
    if (!expr.getFirst().isOperator("(")) {
        unexpectedExpressionError("if without ()")
    }
    val closeBracketIndex = expr.findCloseBracketIndex(0)
    if (closeBracketIndex < 0) {
        unexpectedExpressionError("close bracket not found ')'")
    }
    val condition = expr.subExpression(1, closeBracketIndex - 1)
    val body = expr.subExpression(closeBracketIndex + 1)

    val hasOr = condition.operatorsCount("||") > 0
    val hasAnd = condition.operatorsCount("&&") > 0
    if (hasOr && hasAnd) {
        unsupportedOperationError()
    }
    return if (hasOr) {
        compileMultipleOrIf(condition, body, signed, inverse, isBlock)
    } else if (hasAnd) {
        compileMultipleAndIf(condition, body, signed, inverse, isBlock)
    } else {
        compileSimpleIf(condition, body, inverse, signed)
    }
}


@Throws(SyntaxException::class)
private fun compileMultipleOrIf(condition: Expression, body: Expression, signed: Boolean, inverse: Boolean,
                                isBlock: Boolean): Boolean {
    val conditionsList = condition.splitByOperator("||")
    var result = true
    if (isBlock) {
        val lastBody = Expression()
        val bodyEndLabel = body.get(1).asString()
        val bodyStartLabel = bodyEndLabel + "_body"
        lastBody.add(Token(Token.TYPE_KEYWORD, "goto"))
        lastBody.add(Token(Token.TYPE_OTHER, bodyStartLabel))
        for (i in conditionsList.indices) {
            val c = conditionsList.get(i)
            val last = i == conditionsList.size - 1
            val thisBody = if (last) body else lastBody
            if (!compileSimpleIf(c, thisBody, last, signed)) {
                result = false
            }
        }
        addLabel(bodyStartLabel)
    } else {
        for (c in conditionsList) {
            if (!compileSimpleIf(c, body, inverse, signed)) {
                result = false
            }
        }
    }
    return result
}

@Throws(SyntaxException::class)
private fun compileMultipleAndIf(condition: Expression, body: Expression, signed: Boolean, inverse: Boolean, isBlock: Boolean): Boolean {
    val conditionsList = condition.splitByOperator("&&")
    var result = true
    if (isBlock) {
        for (c in conditionsList) {
            if (!compileSimpleIf(c, body, inverse, signed)) {
                result = false
            }
        }
    } else {
        val labelEnd = parser.generateLabelName("if_and_")
        val firstBodies = Expression()
        firstBodies.add(Token(Token.TYPE_KEYWORD, "goto"))
        firstBodies.add(Token(Token.TYPE_OTHER, labelEnd))

        for (i in conditionsList.indices) {
            val c = conditionsList.get(i)
            val last = i == conditionsList.size - 1
            val thisBody = if (!last) firstBodies else body
            val thisInverse = last == inverse
            if (!compileSimpleIf(c, thisBody, thisInverse, signed)) {
                result = false
            }
        }
        addLabel(labelEnd)
    }
    return result
}


@Throws(SyntaxException::class)
private fun compileIfTwoRegsNotEquals(reg1: Token, reg2: Token, instruction: AsmInstruction) {
    addCommand(CPSE, reg1, reg2)
    addCommand(instruction)
}


@Throws(SyntaxException::class)
private fun compileIfFlagExpression(not: Boolean, flag: Token, label: Token) {
    val cmd = if (not) BRANCH_IF_FLAG_CLEAR_MAP.get(flag.asString()) else BRANCH_IF_FLAG_SET_MAP.get(flag.asString())
    addCommand(cmd, label)
}



*/
