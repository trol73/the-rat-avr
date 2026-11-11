package ru.trolsoft.therat.avr.compiler

import ru.trolsoft.compiler.compiler.*
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.avr.*

internal fun AvrRatCompiler.compileIf(ifNode: IfNode) {
    val node = resolveConditionAndBreakAndContinue(ifNode)

    when {
        node.thenBlock is GotoNode -> {
            compileIfGoto(node)
        }
        node.hasEmptyElseBlock() -> {
            compileIfWithoutElse(node.cond, tryUnpackCompoundStmt(node.thenBlock), node.getLocation())
        }
        node.hasEmptyThenBlock() && node.hasNotEmptyElseBlock() -> {
            compileIfWithEmptyThenBlock(node)
        }
//        (node.thenBlock is CompoundStmt || node.thenBlock is BinaryOperNode) &&
        node.elseBlock != null -> {
            compileFullIfElse(node)
        }
        else -> {
            errorWrongExpression(node)
        }
    }
}

private fun AvrRatCompiler.compileFullIfElse(node: IfNode) {
    val elseLabel = resolveProcLabel("if_else", node.getLocation(), true)
    val endLabel = resolveProcLabel("if_end", node.getLocation(), true)
    val ifGotoNode = IfNode(
        node.getLocation(),
        PrefixOperNode(node.cond.getLocation(), Operator.LOGICAL_NOT, node.cond),
        GotoNode(node.getLocation(), elseLabel),
        null
    )
    compileIf(ifGotoNode)
    compileNode(node.thenBlock)
    compileJump("rjmp", endLabel, node.thenBlock.getLocation())
    addLabel(elseLabel, node.getLocation())
    compileNode(node.elseBlock!!)
    addLabel(endLabel, node.getLocation())
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
    //} else if (node.cond is RegisterBitNode && node.thenBlock is AssemblerInstrNode && node.thenBlock.getInstructionSize() == 2 && node.elseBlock == null) {
}

private fun AvrRatCompiler.compileIfGoto(node: IfNode) {
    val baseLabel = resolveProcLabel("if", node.getLocation(), true)
    val decomposed = mutableListOf<Node>()
    decomposeIf(node, baseLabel, decomposed)
    for (n in decomposed) {
        when (n) {
            is LabelNode ->
                addLabel(n.name, n.getLocation())

            is IfNode ->
                compileSimpleIfGoto(n)

            else ->
                internalError()
        }
    }
}

private fun AvrRatCompiler.compileIfWithEmptyThenBlock(node: IfNode) {
    val endLabel = resolveProcLabel("if_end", node.getLocation(), true)
    val ifGotoNode = IfNode(
        node.getLocation(),
        node.cond,
        GotoNode(node.getLocation(), endLabel),
        null
    )
    compileIf(ifGotoNode)
    compileNode(tryUnpackCompoundStmt(node.elseBlock!!))
    addLabel(endLabel, node.getLocation())
    // if (expr) {
    // } else {
    //   b2
    // }
    // if (expr) goto end
    //   b2
    // end:
}


private fun AvrRatCompiler.compileIfWithoutElse(cond: Node, thenBlock: Node, loc: SourceLocation) {
    when {
        thenBlock is CompoundStmt -> {
            val endLabel = resolveProcLabel("if_end", loc, true)
            val ifGotoNode = IfNode(
                loc,
                PrefixOperNode(cond.getLocation(), Operator.LOGICAL_NOT, cond),
                GotoNode(loc, endLabel),
                null
            )
            compileIf(ifGotoNode)
            compileNode(thenBlock)
            addLabel(endLabel, loc)
            // if (expr) {
            //    b1
            // }
            // if (!expr) goto end
            //   b1
            // end:
        }

        cond is RegisterBitNode && isSingleInstructionNode(thenBlock) -> {
            compileIfRegisterBitExpression(cond, false)
            compileNode(thenBlock)
        }
        cond.isInvertedRegBit() && isSingleInstructionNode(thenBlock) && (cond is PrefixOperNode && cond.expression is RegisterBitNode) -> {
            // if (!reg[bit) asm_instr
            compileIfRegisterBitExpression(cond.expression, true)
            compileNode(thenBlock)
        }

        cond.isLowIoBit() && isSingleInstructionNode(thenBlock) -> {
            val ioBit = cond as IoPortBitNode
            compileIfIoBitExpression(ioBit, cond.getLocation())
            compileOneWordNode(thenBlock)
        }

        cond.isInvertedLowIoBit() && isSingleInstructionNode(thenBlock) -> {
            val ioBit = (cond as PrefixOperNode).expression as IoPortBitNode
            compileIfIoBitExpression(ioBit, cond.getLocation(), true)
            compileOneWordNode(thenBlock)
        }

        else -> {
            if (cond is BinaryOperNode && cond.operator == Operator.NOT_EQ) {
                val left = resolveArg(cond.left)
                val right = resolveArg(cond.right)
                if (left is RegisterNode && right is RegisterNode && isSingleInstructionNode(thenBlock)) {//&& isOneInstructionNode(thenBlock)) {
                    compileAsmRegReg("cpse", left, right, cond.getLocation())
                    compileOneWordNode(thenBlock)
                    return
                }
            }
            val endLabel = resolveProcLabel("if_end", loc, true)
            val ifGotoNode = IfNode(
                loc,
                PrefixOperNode(cond.getLocation(), Operator.LOGICAL_NOT, cond),
                GotoNode(loc, endLabel),
                null
            )
            compileIf(ifGotoNode)
            compileNode(thenBlock)
            addLabel(endLabel, loc)
        }
    }
}

fun AvrRatCompiler.resolveConditionAndBreakAndContinue(ifNode: IfNode): IfNode {
    val thenBlock = tryUnpackCompoundStmt(ifNode.thenBlock)
    val elseBlock = if (ifNode.elseBlock != null ) tryUnpackCompoundStmt(ifNode.elseBlock) else ifNode.elseBlock
    val cond = resolveArg(ifNode.cond)
    val resolvedThen = when (thenBlock) {
        is BreakNode -> {
            val endLabel = getCycleEndLabel() ?: errorParentCycleNotFound(thenBlock)
            GotoNode(thenBlock.getLocation(), endLabel)
        }
        is ContinueNode -> {
            val startLabel = getCycleStartLabel() ?: errorParentCycleNotFound(thenBlock)
            GotoNode(thenBlock.getLocation(), startLabel)
        }
        is BinaryOperNode -> {
            BinaryOperNode(thenBlock.getLocation(), thenBlock.operator, resolveArg(thenBlock.left), resolveArg(thenBlock.right))
        }
        else -> thenBlock
    }
    val resolvedElse = when (elseBlock) {
        is BreakNode -> {
            val endLabel = getCycleEndLabel() ?: errorParentCycleNotFound(elseBlock)
            GotoNode(elseBlock.getLocation(), endLabel)
        }
        is ContinueNode -> {
            val startLabel = getCycleStartLabel() ?: errorParentCycleNotFound(elseBlock)
            GotoNode(elseBlock.getLocation(), startLabel)
        }
        is BinaryOperNode -> {
            BinaryOperNode(elseBlock.getLocation(), elseBlock.operator, resolveArg(elseBlock.left), resolveArg(elseBlock.right))
        }
        else -> elseBlock
    }
    return IfNode(
            ifNode.getLocation(),
            cond,
            resolvedThen,
            resolvedElse
    )
}

private fun AvrRatCompiler.compileSimpleIfGoto(node: IfNode) {
    val cond = resolveArg(node.cond)
    val label = (node.thenBlock as GotoNode).label
    val thenLoc = node.thenBlock.getLocation()

    val signed = cond is SignedCastNode
    val condition = if (cond is SignedCastNode) {
        cond.expr
    } else {
        cond
    }
    when {
        condition is BinaryOperNode -> {
            compileSimpleIfGoto(condition, label, signed, node, node.thenBlock)
        }
        condition is RegisterBitNode -> {
            compileIfRegisterBitExpression(condition)
            compileJump("rjmp", label, thenLoc)
        }
        condition is IoPortBitNode -> {
            if (condition.port.name == "SREG") {
                compileIfFlagExpression(condition.bitIndex, false, condition.getLocation(), label)
            } else {
                compileIfIoBitExpression(condition, node.getLocation())
                compileJump("rjmp", label, thenLoc)
            }
        }
        condition is PrefixOperNode && condition.operator == Operator.LOGICAL_NOT -> {
            when (val inverted = condition.expression) {
                is BinaryOperNode if inverted.operator == Operator.EQ -> {
                    val deInverted = BinaryOperNode(inverted.getLocation(), Operator.NOT_EQ, inverted.left, inverted.right)
                    compileSimpleIfGoto(deInverted, label, signed, node, node.thenBlock)
                }

                is BinaryOperNode if inverted.operator == Operator.NOT_EQ -> {
                    val deInverted = BinaryOperNode(inverted.getLocation(), Operator.EQ, inverted.left, inverted.right)
                    compileSimpleIfGoto(deInverted, label, signed, node, node.thenBlock)
                }

                else -> {
                    compileSimpleIfGotoForInvertedCondition(node, condition.expression, signed)
                }
            }
        }
        else -> {
            errorWrongExpression(condition)
        }
    }
}

private fun AvrRatCompiler.compileSimpleIfGoto(
    binOp: BinaryOperNode,
    label: String,
    signed: Boolean,
    node: IfNode,
    thenBlock: GotoNode
) {
    val left = resolveArg(binOp.left)
    val right = resolveArg(binOp.right)
    when {
        left is RegisterNode && right is NumberNode -> {
            compileIfRegValue(binOp.operator, left, right, label, signed, node.getLocation())
        }

        left is RegisterNode && right is RegisterNode -> {
            compileIfRegRegOrGroups(binOp.operator, left, right, label, signed, node.getLocation())
        }

        left is RegisterGroupNode && right is NumberNode -> {
            compileIfRegRegOrGroups(binOp.operator, left, right, label, signed, node.getLocation())
        }

        left is RegisterGroupNode && right is RegisterGroupNode -> {
            compileIfRegRegOrGroups(binOp.operator, left, right, label, signed, node.getLocation())
        }

        left is PrefixOperNode && right is PrefixOperNode -> {
            compileSuffixOp(SuffixOperNode(left.operator, left.expression))
            compileSuffixOp(SuffixOperNode(right.operator, right.expression))
            val conditionWithoutPrefix =
                BinaryOperNode(binOp.getLocation(), binOp.operator, left.expression, right.expression)
            val nodeWithoutPrefix =
                IfNode(node.getLocation(), conditionWithoutPrefix, thenBlock, node.elseBlock)
            compileSimpleIfGoto(nodeWithoutPrefix)
        }

        left is PrefixOperNode -> {
            if (right is NumberNode && right.value == 0 && left.operator.isDecOrInc() && (binOp.operator == Operator.EQ || binOp.operator == Operator.NOT_EQ)) {
                val arg = left.expression
                val loc = binOp.getLocation()
                if (arg is RegisterNode) {
                    compileRegIncDec(arg, left.operator)
                    compileIfFlagExpression(1, binOp.operator == Operator.NOT_EQ, loc, label)
                    return
                } else if (arg is RegisterGroupNode && (arg.isHighPair() || arg.isR25R24())) {
                    val cmdNode = if (left.operator == Operator.DEC) {
                        AssemblerInstrNode(loc, "sbiw", listOf(arg, NumberNode(loc, 1)))
                    } else {
                        AssemblerInstrNode(loc, "adiw", listOf(arg, NumberNode(loc, 1)))
                    }
                    compileAssembler(cmdNode)
                    compileIfFlagExpression(1, binOp.operator == Operator.NOT_EQ, loc, label)
                    return
                }
            }
            compileSuffixOp(SuffixOperNode(left.operator, left.expression))
            val conditionWithoutPrefix =
                BinaryOperNode(binOp.getLocation(), binOp.operator, left.expression, right)
            val nodeWithoutPrefix =
                IfNode(node.getLocation(), conditionWithoutPrefix, thenBlock, node.elseBlock)
            compileSimpleIfGoto(nodeWithoutPrefix)
        }

        right is PrefixOperNode -> {
            compileSuffixOp(SuffixOperNode(right.operator, right.expression))
            val conditionWithoutPrefix =
                BinaryOperNode(binOp.getLocation(), binOp.operator, left, right.expression)
            val nodeWithoutPrefix =
                IfNode(node.getLocation(), conditionWithoutPrefix, thenBlock, node.elseBlock)
            compileSimpleIfGoto(nodeWithoutPrefix)
        }

        else -> {
            errorWrongExpression(node)
        }
    }
}

private fun AvrRatCompiler.compileSimpleIfGotoForInvertedCondition(node: IfNode, expression: Node, signed: Boolean) {
    val thenLoc = node.thenBlock.getLocation()
    val label = (node.thenBlock as GotoNode).label

    when (val expr = resolveArg(expression)) {
        is RegisterBitNode -> {
            compileIfRegisterBitExpression(expr, true)
            compileJump("rjmp", label, thenLoc)
        }
        is IoPortBitNode -> {
            if (expr.port.name == "SREG") {
                compileIfFlagExpression(expr.bitIndex, true, expr.getLocation(), label)
            } else {
                compileIfIoBitExpression(expr, node.getLocation(), true)
                compileJump("rjmp", label, thenLoc)
            }
        }
        is BinaryOperNode -> {
            val left = resolveArg(expr.left)
            val right = resolveArg(expr.right)
            val invOp = inverseBinaryCompareOperation(expr.operator, expr.getLocation())
            when {
                left is RegisterNode && right is NumberNode -> {
                    compileIfRegValue(invOp, left, right, label, signed, node.getLocation())
                }
                left is RegisterNode && right is RegisterNode -> {
                    compileIfRegRegOrGroups(invOp, left, right, label, signed, node.getLocation())
                }
                left is RegisterGroupNode && right is RegisterGroupNode -> {
                    compileIfRegRegOrGroups(invOp, left, right, label, signed, node.getLocation())
                }
                left is RegisterGroupNode && right is NumberNode -> {
                    compileIfGroupValue(invOp, left, right, label, node.getLocation())
                }
                left is PrefixOperNode && right is PrefixOperNode -> {
                    compileSuffixOp(SuffixOperNode(left.operator, left.expression))
                    compileSuffixOp(SuffixOperNode(right.operator, right.expression))
                    val exprWithoutPrefix = BinaryOperNode(expr.getLocation(), expr.operator, left.expression, right.expression)
                    compileSimpleIfGotoForInvertedCondition(node, exprWithoutPrefix, signed)
                }
                left is PrefixOperNode -> {
                    compileSuffixOp(SuffixOperNode(left.operator, left.expression))
                    val exprWithoutPrefix = BinaryOperNode(expr.getLocation(), expr.operator, left.expression, right)
                    compileSimpleIfGotoForInvertedCondition(node, exprWithoutPrefix, signed)
                }
                right is PrefixOperNode -> {
                    compileSuffixOp(SuffixOperNode(right.operator, right.expression))
                    val exprWithoutPrefix = BinaryOperNode(expr.getLocation(), expr.operator, left, right.expression)
                    compileSimpleIfGotoForInvertedCondition(node, exprWithoutPrefix, signed)
                }
                else -> {
                    errorWrongExpression(node)
                }
            }
        }
        is SignedCastNode -> {
            if (expr.expr !is BinaryOperNode) {
                errorWrongExpression(expr)
            }
            val left = resolveArg(expr.expr.left)
            val right = resolveArg(expr.expr.right)
            val invOp = inverseBinaryCompareOperation(expr.expr.operator, expr.expr.getLocation())
            when {
                left is RegisterNode && right is NumberNode -> {
                    compileIfRegValue(invOp, left, right, label, true, node.getLocation())
                }
                left is RegisterNode && right is RegisterNode -> {
                    compileIfRegRegOrGroups(invOp, left, right, label, true, node.getLocation())
                }
                left is RegisterGroupNode && right is RegisterGroupNode -> {
                    compileIfRegRegOrGroups(invOp, left, right, label, true, node.getLocation())
                }
                else -> {
                    errorWrongExpression(node)
                }
            }
        }
        else ->
            errorWrongExpression(node)
    }
}

fun AvrRatCompiler.compileIfRegRegOrGroups(operator: Operator, left: Node, right: Node, label: String,
                            signed: Boolean, location: SourceLocation) {
    when {
        left is RegisterGroupNode && right is NumberNode && (operator == Operator.EQ || operator == Operator.NOT_EQ) -> {
            compileIfGroupEqConst(left, right, location, operator, label)
            return
        }
        operator == Operator.GREAT -> {
            // r1 > r2 -> r2 < r1
            compileIfRegRegOrGroups(Operator.LESS, right, left, label, signed, location)
            return
        }
        operator == Operator.LESS_EQ -> {
            // r1 <= r2 -> r2 >= r1
            compileIfRegRegOrGroups(Operator.GREAT_EQ, right, left, label, signed, location)
            return
        }
        left is RegisterNode && right is RegisterNode -> {
            if (operator == Operator.NOT_EQ) {
                compileAsmRegReg("cpse", left, right, location)
                compileJump("rjmp", label, location)
                return
            } else {
                addCompareInstruction(left, right, location)
            }
        }
        left is RegisterGroupNode && right is RegisterGroupNode -> {
            addCompareInstruction(left, right, location)
        }
        else -> {
            internalError()
        }
    }

    when (operator) {
        Operator.EQ -> compileJump("breq", label, location)
        Operator.NOT_EQ -> compileJump("brne", label, location)
        Operator.LESS -> {
            when {
                signed -> compileJump("brlt", label, location)
                else -> compileJump("brlo", label, location)
            }
        }
        Operator.GREAT_EQ -> {
            when {
                signed -> compileJump("brge", label, location)
                else -> compileJump("brsh", label, location)
            }
        }
        else -> errorUnsupportedOperation(location)
    }
}

private fun AvrRatCompiler.compileIfGroupEqConst(
    left: RegisterGroupNode,
    right: NumberNode,
    location: SourceLocation,
    operator: Operator,
    label: String
) {
    // Для EQ внутри цикла (if-break оптимизация) нужен fallthrough
    // Для обычного if — одинаковый branch для всех байт
    if (operator == Operator.EQ && !cycles.isEmpty()) {
        compileIfGroupEqConstWithFallthrough(left, right, location, operator, label)
    } else {
        for (i in 0 until left.size()) {
            val j = left.size() - 1 - i
            val reg = left.buildRegisterNode(j)
            val num = right.extractByteNode(i)
            compileAsmRegNum("cpi", reg, num, location)
            val cmd = if (operator == Operator.EQ) "breq" else "brne"
            compileJump(cmd, label, location)
        }
    }
}

private fun AvrRatCompiler.compileIfGroupEqConstWithFallthrough(
    left: RegisterGroupNode,
    right: NumberNode,
    location: SourceLocation,
    operator: Operator,
    label: String
) {
    val size = left.size()
    // Генерируем промежуточные метки для всех байт кроме последнего
    val fallthroughLabels = mutableListOf<String>()
    for (i in 0 until size - 1) {
        fallthroughLabels.add(resolveProcLabel("if_end", location, true))
    }
    
    for (i in 0 until size) {
        val j = size - 1 - i
        val reg = left.buildRegisterNode(j)
        val num = right.extractByteNode(i)
        compileAsmRegNum("cpi", reg, num, location)
        
        if (i < size - 1) {
            // Промежуточный байт: brne на промежуточную метку
            compileJump("brne", fallthroughLabels[i], location)
        } else {
            // Последний байт: breq/brne на финальный label
            val cmd = if (operator == Operator.EQ) "breq" else "brne"
            compileJump(cmd, label, location)
        }
    }
    
    // Добавляем все промежуточные метки
    for (lbl in fallthroughLabels) {
        addLabel(lbl, location)
    }
}


internal fun inverseBinaryCompareOperation(op: Operator, loc: SourceLocation): Operator =
    when (op) {
        Operator.EQ -> Operator.NOT_EQ
        Operator.NOT_EQ -> Operator.EQ
        Operator.GREAT -> Operator.LESS_EQ
        Operator.LESS -> Operator.GREAT_EQ
        Operator.LESS_EQ -> Operator.GREAT
        Operator.GREAT_EQ -> Operator.LESS
        Operator.ARROW_RIGHT -> errorWrongExpression(loc)
        else -> errorUnsupportedOperation(loc)
    }

private fun AvrRatCompiler.addCompareInstruction(left: Node, right: Node, location: SourceLocation) {
    when {
        left is RegisterNode && right is NumberNode && right.value == 0 -> {
            compileAsmReg("tst", left, location)
        }
        left is RegisterNode && right is RegisterNode -> {
            compileAsmRegReg("cp", left, right, location)
        }
        left is RegisterNode && right is NumberNode -> {
            if (right.value > 0xff) {
                errorValueTooBig(right)
            }
            compileAsmRegNum("cpi", left, right, location)
        }
        left is RegisterGroupNode && right is RegisterGroupNode -> {
            if (left.size() != right.size()) {
                errorSizeMismatch(left, right)
            }
            for (i in 0 until left.size()) {
                val j = left.size() - 1 - i
                val reg1 = left.buildRegisterNode(j)
                val reg2 = right.buildRegisterNode(j)
                val cmd = if (i == 0) "cp" else "cpc"
                compileAsmRegReg(cmd, reg1, reg2, location)
            }
        }
        else -> {
            errorUnsupportedOperation(left)
        }
    }
}

internal fun AvrRatCompiler.compileIfRegValue(operator: Operator, left: RegisterNode, right: NumberNode, label: String,
                                      signed: Boolean, location: SourceLocation) {
    //  BRCS = BRLO, BRCC = BRSH
    when (operator) {
        Operator.EQ -> {
            addCompareInstruction(left, right, location)
            compileJump("breq", label, location)
        }

        Operator.NOT_EQ -> {
            addCompareInstruction(left, right, location)
            compileJump("brne", label, location)
        }

        Operator.LESS -> {
            addCompareInstruction(left, right, location)
            when {
                right.value == 0 -> compileJump("brmi", label, location)
                signed -> compileJump("brlt", label, location)
                else -> compileJump("brlo", label, location)
            }
        }

        Operator.GREAT_EQ -> {
            addCompareInstruction(left, right, location)
            when {
                right.value == 0 -> compileJump("brpl", label, location)
                signed -> compileJump("brge", label, location)
                else -> compileJump("brsh", label, location)
            }
        }

        Operator.GREAT -> {
            // x > k  ->   x >= k+1
            compileIfRegValue(
                Operator.GREAT_EQ,
                left,
                NumberNode(right.getLocation(), right.value + 1),
                label,
                signed,
                location
            )
        }

        Operator.LESS_EQ -> {
            // x <= k -> x < k+1
            compileIfRegValue(
                Operator.LESS,
                left,
                NumberNode(right.getLocation(), right.value + 1),
                label,
                signed,
                location
            )
        }

        else -> {
            errorUnsupportedOperation(location)
        }
    }
}

internal fun AvrRatCompiler.compileIfGroupValue(operator: Operator, left: RegisterGroupNode, right: NumberNode, label: String, location: SourceLocation) {
    val maxVal = (1 shl (8*left.size())) - 1
    if (right.value > maxVal) {
        errorValueTooBig(right)
    }

    when (operator) {
        Operator.EQ, Operator.NOT_EQ -> {
        if (operator == Operator.EQ) {
            errorWrongExpression(location)
        }
            compileIfGroupEqConst(left, right, location, operator, label)
        }
        Operator.LESS -> {
            errorWrongExpression(location)
//            addCompareInstruction(compiler, left, right, location)
//            when {
//                right.value == 0 ->
//                    compiler.compileJump("brmi", label, location)
//                signed ->
//                    compiler.compileJump("brlt", label, location)
//                else ->
//                    compiler.compileJump("brlo", label, location)
//            }
        }
        Operator.GREAT_EQ -> {
            errorWrongExpression(location)
//            addCompareInstruction(compiler, left, right, location)
//            when {
//                right.value == 0 ->
//                    compiler.compileJump("brpl", label, location)
//                signed ->
//                    compiler.compileJump("brge", label, location)
//                else ->
//                    compiler.compileJump("brsh", label, location)
//            }
        }
        Operator.GREAT -> {
            errorWrongExpression(location)
            // x > k  ->   x >= k+1
            //compileIfRegValue(compiler, Operator.GREAT_EQ, left, NumberNode(right.getLocation(), right.value+1), label, signed, location)
        }
        Operator.LESS_EQ -> {
            errorWrongExpression(location)
            // x <= k -> x < k+1
            //compileIfRegValue(compiler, Operator.LESS, left, NumberNode(right.getLocation(), right.value+1), label, signed, location)
        }
        else ->
            errorUnsupportedOperation(location)
    }
}


private fun AvrRatCompiler.compileIfRegisterBitExpression(node: RegisterBitNode, inverse: Boolean = false) {
    val cmd = if (inverse) "sbrs" else "sbrc"
    compileAsmRegNum(cmd, node.reg, node.bit, node.getLocation())
}

internal fun AvrRatCompiler.compileIfIoBitExpression(node: IoPortBitNode, loc: SourceLocation, inverse: Boolean = false) {
    val cmd = if (inverse) "sbis" else "sbic"
    val asmArgs = listOf(
            NumberNode(loc, node.port.address, 16),
            NumberNode(loc, node.bitIndex, 10)
    )
    val cmdNode = AssemblerInstrNode(loc, cmd, asmArgs)
    compileAssembler(cmdNode)
}

fun AvrRatCompiler.compileIfFlagExpression(bitIndex: Int, inverse: Boolean, loc: SourceLocation, label: String) {
    val cmd = if (inverse) "brbc" else "brbs"
    val args = listOf(
            NumberNode(loc, bitIndex, 10),
            IdentifierNode(loc, label)
    )
    val cmdNode = AssemblerInstrNode(loc, cmd, args)
    compileAssembler(cmdNode)

//BRBC s, A	Переход если флаг S сброшен	Если SREG(S) = 0	—
//BRBS s, A
}


private fun Node.isLowIoBit(): Boolean {
    return this is IoPortBitNode && this.port.address <= 31
}

private fun Node.isInvertedLowIoBit() =
    this is PrefixOperNode && operator == Operator.LOGICAL_NOT && expression is IoPortBitNode && expression.port.address <= 31

private fun Node.isInvertedRegBit() =
    this is PrefixOperNode && operator == Operator.LOGICAL_NOT && expression is RegisterBitNode

private fun tryUnpackCompoundStmt(node: Node): Node {
    if (node is CompoundStmt && node.size() == 1) {
        val item = node.first()
        // TODO тут нужно рассмотреть случай вызова с передачей аргументов без их фактической передачи, когда регистры совпадают с самими собой)
        if (item is AssemblerInstrNode && !item.args.isNullOrEmpty()) {
            val arg = item.args.first()
            if (arg is FunctionCallNode && arg.args.isNotEmpty()) {
                return node
            }
        }
        return item
    }
    return node
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

*/
