package ru.trolsoft.therat.avr.compiler

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.general.CycleBlock
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.arch.avr.AvrCmd
import ru.trolsoft.therat.avr.*

internal fun AvrRatCompiler.compileLoop(loop: LoopNode) {

    if (loop.cond == null) {
        val (cycle, until) = compileLoopBody(loop)
        when (until) {
            null -> {
                compileJump("rjmp", cycle.startLabel, loop.getLocation())
            }
            is Pair<*, *> -> {
                val (cond, termInstr) = until as Pair<BinaryOperNode, Any?>
                if (termInstr is String) {
                    // Handle: loop { if (condition) ret/ijmp }
                    // Emit: body; if (!condition) rjmp start; ret/ijmp; end:
                    compileTerminalJumpCondition(cond, cycle.startLabel, termInstr, loop.getLocation())
                } else {
                    // Handle: loop { if (condition) break }
                    val revertedCondition = cond.revert()
                    compileIf(IfNode(
                        cond.getLocation(),
                        revertedCondition,
                        GotoNode(cond.getLocation(), cycle.startLabel), null
                    ))
                }
            }

            is IoPortBitNode, is RegisterBitNode -> {
                val revertedCondition = PrefixOperNode(
                    until.getLocation(),
                    Operator.LOGICAL_NOT,
                    until
                )
                compileIf(IfNode(
                    until.getLocation(),
                    revertedCondition,
                    GotoNode(until.getLocation(), cycle.startLabel), null
                ))
            }

            is PrefixOperNode -> {
                if (until.operator != Operator.LOGICAL_NOT) {
                    internalError()
                }
                compileIf(IfNode(
                    until.getLocation(),
                    until.expression,
                    GotoNode(until.getLocation(), cycle.startLabel), null
                ))
            }
        }
        addLabel(cycle.endLabel, loop.getLocation())
        return
    }
    val condition = resolveArg(loop.cond)

    when {
        condition is RegisterNode -> {
            compileLoopReg(loop, condition)
        }
        condition is RegisterGroupNode -> {
            compileLoopRegPair(loop, condition)
        }
        condition is IdentifierNode -> {
            val arg = resolveProcVar(condition.name) ?: errorInvalidArgument(condition)
            when (arg) {
                is RegisterNode -> compileLoopReg(loop, arg)
                is RegisterGroupNode if arg.isHighPair() -> {
                    compileLoopRegPair(loop, arg)
                }
                else -> errorRegisterOrPairExpected(condition)
            }
        }
        condition is BinaryOperNode && condition.operator == Operator.ASSIGN -> {
            val conditionArg = resolveArg(condition.left)
            when (conditionArg) {
                is RegisterNode -> checkAndCompileAssignOp(condition, 0xff)
                is RegisterGroupNode if conditionArg.size() == 2 -> {
                    checkAndCompileAssignOp(condition, 0xffff)
                }
                else -> compileAssignOp(condition)
            }
            when (conditionArg) {
                is RegisterNode -> compileLoopReg(loop, conditionArg)
                is RegisterGroupNode if conditionArg.size() == 2 -> {
                    compileLoopRegPair(loop, conditionArg)
                }
                else -> errorRegisterOrPairExpected(condition.left)
            }
        }
        condition is IoPortBitNode -> {
            compileLoopIoBit(loop, condition)
        }
        condition is PrefixOperNode && condition.operator == Operator.LOGICAL_NOT && condition.expression is IoPortBitNode -> {
            compileLoopIoBit(loop, condition.expression, true)
        }
        else -> {
            errorWrongLoopSyntax(loop)
        }
    }
}

private fun AvrRatCompiler.compileTerminalJumpCondition(cond: BinaryOperNode, startLabel: String, termInstr: String, location: SourceLocation) {
    val left = resolveArg(cond.left)
    val right = resolveArg(cond.right)
    when {
        left is RegisterNode && right is NumberNode -> {
            val invOp = inverseBinaryCompareOperation(cond.operator, cond.getLocation())
            compileIfRegValue(invOp, left, right, startLabel, false, cond.getLocation())
        }
        left is RegisterNode && right is RegisterNode -> {
            val invOp = inverseBinaryCompareOperation(cond.operator, cond.getLocation())
            compileIfRegRegOrGroups(invOp, left, right, startLabel, false, cond.getLocation())
        }
        left is RegisterGroupNode && right is NumberNode -> {
            val invOp = inverseBinaryCompareOperation(cond.operator, cond.getLocation())
            compileIfRegRegOrGroups(invOp, left, right, startLabel, false, cond.getLocation())
        }
        left is RegisterGroupNode && right is RegisterGroupNode -> {
            val invOp = inverseBinaryCompareOperation(cond.operator, cond.getLocation())
            compileIfRegRegOrGroups(invOp, left, right, startLabel, false, cond.getLocation())
        }
        left is PrefixOperNode && (left.operator == Operator.INC || left.operator == Operator.DEC) -> {
            compileSuffixOp(SuffixOperNode(left.operator, left.expression))
            val condWithoutPrefix = BinaryOperNode(
                cond.getLocation(),
                cond.operator,
                left.expression,
                right
            )
            val left2 = resolveArg(condWithoutPrefix.left)
            val right2 = resolveArg(condWithoutPrefix.right)
            val invOp = inverseBinaryCompareOperation(cond.operator, cond.getLocation())
            when {
                left2 is RegisterNode && right2 is NumberNode -> {
                    compileIfRegValue(invOp, left2, right2, startLabel, false, cond.getLocation())
                }
                left2 is RegisterNode && right2 is RegisterNode -> {
                    compileIfRegRegOrGroups(invOp, left2, right2, startLabel, false, cond.getLocation())
                }
                else -> {
                    val ifNode = IfNode(
                        cond.getLocation(),
                        PrefixOperNode(cond.getLocation(), Operator.LOGICAL_NOT, condWithoutPrefix),
                        GotoNode(cond.getLocation(), startLabel),
                        null
                    )
                    compileIf(ifNode)
                }
            }
        }
        else -> {
            val ifNode = IfNode(
                cond.getLocation(),
                PrefixOperNode(cond.getLocation(), Operator.LOGICAL_NOT, cond),
                GotoNode(cond.getLocation(), startLabel),
                null
            )
            compileIf(ifNode)
        }
    }
    val termNode = AssemblerInstrNode(cond.getLocation(), termInstr, null)
    compileAssembler(termNode)
}

private fun AvrRatCompiler.checkAndCompileAssignOp(condition: BinaryOperNode, maxVal: Int) {
    if (condition.right is NumberNode) {
        val num = condition.right.value
        when {
            num <= maxVal -> {
                compileAssignOp(condition)
            }
            num == maxVal+1 -> {
                val zeroVal = NumberNode(condition.right.getLocation(), 0)
                val newCond = BinaryOperNode(condition.getLocation(), Operator.ASSIGN,
                        condition.left, zeroVal)
                compileAssignOp(newCond)
            }
            else -> {
                errorValueTooBig(condition.right)
            }
        }
    } else {
        compileAssignOp(condition)
    }
}


/**
 * Возвращает пару тело цикла + операция перехода в его начало.
 * Pair<BinaryOperNode, String?> где String? = terminal instruction ("ret"/"ijmp") или null для break
 * IoPortBitNode, RegisterBitNode, PrefixOperNode - для break
 */
private fun AvrRatCompiler.compileLoopBody(loop: LoopNode): Pair<CycleBlock, Any?> {
    val startLabel = addProcLabel("loop", loop.getLocation(), true)
    val endLabel = resolveProcLabel("loopEnd", loop.getLocation(), true)
    val cycleRec = CycleBlock(startLabel, endLabel)
    cycles.push(cycleRec)

    val (body, until) = if (loop.cond == null) optimizeVoidConditionBody(loop.body) else Pair(loop.body, null)

    compileNode(body)
    cycles.pop()

    return Pair(cycleRec, until)
}

private fun AvrRatCompiler.optimizeVoidConditionBody(body: Node): Pair<Node, Any?> {
    if (body is CompoundStmt && !body.isEmpty()) {
        val lastIf = body.last()
        if (lastIf is IfNode) {
            val unpackedThen = tryUnpackCompoundStmt(lastIf.thenBlock)
            if (unpackedThen is BreakNode && lastIf.hasEmptyElseBlock()) {
                val cond = lastIf.cond
                val resolved = resolveArg(cond)
                when {
                    resolved is BinaryOperNode -> {
                        val left = resolveArg(resolved.left)
                        val right = resolveArg(resolved.right)

                        when {
                            resolved.operator == Operator.EQ && left is RegisterNode && right is RegisterNode -> {
                                val newBody = body.replaceLastTo(
                                    AssemblerInstrNode(
                                        lastIf.getLocation(),
                                        AvrCmd.CPSE.name,
                                        listOf(left, right)
                                    )
                                )
                                return Pair(newBody, null)
                            }
                            resolved.operator == Operator.NOT_EQ -> {
                                return Pair(body.removeLast(), cond as BinaryOperNode to false)
                            }
                            resolved.operator == Operator.EQ -> {
                                return Pair(body.removeLast(), resolved to false)
                            }
                        }
                    }

                    resolved is IoPortBitNode || resolved is RegisterBitNode -> {
                        return Pair(body.removeLast(), resolved)
                    }

                    resolved is PrefixOperNode && resolved.operator == Operator.LOGICAL_NOT -> {
                        val expr = resolveArg(resolved.expression)
                        if (expr is RegisterBitNode || (expr is IoPortBitNode)) {
                            val newBody = body.removeLast()
                            return Pair(newBody, resolved)
                        }
                    }
                }
            }

            val termInstr = unpackedThen.getTerminalInstruction()
            if (termInstr != null && lastIf.hasEmptyElseBlock()) {
                return Pair(body.removeLast(), lastIf.cond as BinaryOperNode to termInstr)
            }
        }
    }
    return Pair(body, null)
}

private fun CompoundStmt.replaceLastTo(node: Node): CompoundStmt {
    val newBodyNodes = mutableListOf<Node>()
    for (i in 0 until size()-1) {
        newBodyNodes.add(this[i])
    }
    newBodyNodes.add(node)
    return CompoundStmt(newBodyNodes)
}

private fun CompoundStmt.removeLast(): CompoundStmt {
    val newBodyNodes = mutableListOf<Node>()
    for (i in 0 until size()-1) {
        newBodyNodes.add(this[i])
    }
    return CompoundStmt(newBodyNodes)
}

private fun AvrRatCompiler.compileLoopReg(loop: LoopNode, reg: RegisterNode) {
    val cycle = compileLoopBody(loop).first
    compileAsmReg("dec", reg, reg.getLocation())
    compileJump("brne", cycle.startLabel, loop.getLocation())
    addLabel(cycle.endLabel, loop.getLocation())
}

private fun AvrRatCompiler.compileLoopRegPair(loop: LoopNode, group: RegisterGroupNode) {
    val cycle = compileLoopBody(loop).first
//    if (group.isHighPair() || group.isR25R24()) {
//        compileAsmPairNum("sbiw", group, 1, group.getLocation())
//    } else
    if (group.size() == 2) {
        this.compileSuffixOp(SuffixOperNode(Operator.DEC, group))
    } else {
        errorWrongLoopSyntax(loop)
    }

    compileJump("brne", cycle.startLabel, loop.getLocation())
    addLabel(cycle.endLabel, loop.getLocation())
}

private fun errorWrongLoopSyntax(node: Node): Nothing {
    throw CompilerException("Wrong loop syntax", node.getLocation())
}

private fun AvrRatCompiler.compileLoopIoBit(loop: LoopNode, ioBit: IoPortBitNode, invert: Boolean = false) {
    val cycle = compileLoopBody(loop).first
    compileIfIoBitExpression(ioBit, loop.getLocation(), invert)
    compileJump("rjmp", cycle.startLabel, loop.getLocation())
    addLabel(cycle.endLabel, loop.getLocation())
}

private fun BinaryOperNode.revert() =
    BinaryOperNode(getLocation(), inverseBinaryCompareOperation(operator, getLocation()), left, right)

private fun Node.getTerminalInstruction(): String? = when (this) {
    is AssemblerInstrNode -> if (instr == "ret" || instr == "ijmp") instr else null
    else -> null
}

/*
    public void compileLoopStart(TokenString src, Expression expr) throws SyntaxException {
        setup(src);
        if (!expr.getLast().isOperator("{")) {
            error("'{' expected");
        }
        Expression argExpr = null;
        if (expr.get(1).isOperator("(")) {
            if (!expr.getLast(1).isOperator(")")) {
                error("')' not found");
            }
            argExpr = expr.subExpression(2, expr.size() - 3);
            if (argExpr.isEmpty()) {
                argExpr = null;
            }
        } else if (expr.size() != 2) {
            error("wrong loop syntax");
        }
        String argName = argExpr != null ? argExpr.getFirst().asString() + "_" : "";
        Block block = parser.addNewBlock(BLOCK_LOOP, argExpr, "loop_" + argName);

        if (argExpr != null) {
            Token reg = argExpr.getFirst();
            if (!reg.isRegister()) {
                error("register expected: " + reg);
            }
            if (argExpr.size() > 1) {
                if (!argExpr.get(1).isOperator("=")) {
                    error("wrong loop argument expression");
                }
                new ExpressionsCompiler(parser, mainCompiler).compile(src, argExpr.copy(), out);
            }
        }
        addLabel(block.getLabelStart());
        cleanup();
    }


    public void compileLoopEnd(TokenString src, Block block) throws SyntaxException {
        setup(src);
        if (block.expr != null) {
            Token reg = block.expr.getFirst();
            addCommand(DEC, reg);
            addCommand(BRNE, block.getLabelStart());
        } else {
            addCommand(RJMP, block.getLabelStart());
        }
        if (block.getLabelEnd() != null) {
            addLabel(block.getLabelEnd());
        }
        cleanup();
    }
 */