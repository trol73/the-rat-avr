package ru.trolsoft.therat.avr.compile

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.avr.*

internal fun compileLoop(compiler: AvrRatCompiler, loop: LoopNode) {

    if (loop.cond == null) {
        val cycle = compileLoopBody(compiler, loop)
        compiler.compileJump("rjmp", cycle.startLabel, loop.getLocation())
        compiler.addLabel(cycle.endLabel, loop.getLocation())
        return
    }
    val condition = compiler.resolveArg(loop.cond)

    when {
        condition is RegisterNode -> {
            compileLoopReg(compiler, loop, condition)
        }
        condition is RegisterGroupNode -> {
            compileLoopRegPair(compiler, loop, condition)
        }
        condition is IdentifierNode -> {
            val arg = compiler.resolveProcVar(condition.name) ?: errorInvalidArgument(condition)
            if (arg is RegisterNode) {
                compileLoopReg(compiler, loop, arg)
            } else if (arg is RegisterGroupNode && arg.isHighPair()) {
                compileLoopRegPair(compiler, loop, arg)
            } else {
                errorRegisterOrPairExpected(condition)
            }
        }
        condition is BinaryOperNode && condition.operator == Operator.ASSIGN -> {
            if (condition.left is RegisterNode) {
                checkAndCompileAssignOp(condition, 0xff, compiler)
            } else if (condition.left is RegisterGroupNode && condition.left.isHighPair()) {
                checkAndCompileAssignOp(condition, 0xffff, compiler)
            } else {
                compileAssignOp(compiler, condition)
            }
            val conditionArg = compiler.resolveArg(condition.left)
            if (conditionArg is RegisterNode) {
                compileLoopReg(compiler, loop, conditionArg)
            } else if (conditionArg is RegisterGroupNode && conditionArg.isHighPair()) {
                compileLoopRegPair(compiler, loop, conditionArg)
            } else {
                errorRegisterOrPairExpected(condition.left)
            }
        }
        condition is IoPortBitNode -> {
            compileLoopIoBit(compiler, loop, condition)
        }
        condition is PrefixOperNode && condition.operator == Operator.LOGICAL_NOT && condition.expression is IoPortBitNode -> {
            compileLoopIoBit(compiler, loop, condition.expression, true)
        }
        else -> {
            errorWrongLoopSyntax(loop)
        }
    }
}

private fun checkAndCompileAssignOp(condition: BinaryOperNode, maxVal: Int, compiler: AvrRatCompiler) {
    if (condition.right is NumberNode) {
        val num = condition.right.value
        when {
            num <= maxVal -> {
                compileAssignOp(compiler, condition)
            }
            num == maxVal+1 -> {
                val zeroVal = NumberNode(condition.right.getLocation(), 0)
                val newCond = BinaryOperNode(condition.getLocation(), Operator.ASSIGN,
                        condition.left, zeroVal)
                compileAssignOp(compiler, newCond)
            }
            else -> {
                errorValueTooBig(condition.right)
            }
        }
    } else {
        compileAssignOp(compiler, condition)
    }
}


private fun compileLoopBody(compiler: AvrRatCompiler, loop: LoopNode): CycleBlock {
    val startLabel = compiler.addProcLabel("loop", loop.getLocation(), true)
    val endLabel = compiler.resolveProcLabel("loopEnd", loop.getLocation(), true)
    val cycleRec = CycleBlock(startLabel, endLabel)
    compiler.cycles.push(cycleRec)
    compiler.compileNode(loop.body)
    compiler.cycles.pop()

    return cycleRec
}

private fun compileLoopReg(compiler: AvrRatCompiler, loop: LoopNode, reg: RegisterNode) {
    val cycle = compileLoopBody(compiler, loop)
    compiler.compileAsmReg("dec", reg, reg.getLocation())
    compiler.compileJump("brne", cycle.startLabel, loop.getLocation())
    compiler.addLabel(cycle.endLabel, loop.getLocation())
}

private fun compileLoopRegPair(compiler: AvrRatCompiler, loop: LoopNode, group: RegisterGroupNode) {
    if (!group.isHighPair()) {
        errorWrongLoopSyntax(loop)
    }
    val cycle = compileLoopBody(compiler, loop)
    compiler.compileAsmRegNum("sbiw", group.lowReg(), 1, group.getLocation())
    compiler.compileJump("brne", cycle.startLabel, loop.getLocation())
    compiler.addLabel(cycle.endLabel, loop.getLocation())
}

private fun errorWrongLoopSyntax(node: Node): Nothing {
    throw CompilerException("Wrong loop syntax", node.getLocation())
}

private fun compileLoopIoBit(compiler: AvrRatCompiler, loop: LoopNode, ioBit: IoPortBitNode, invert: Boolean = false) {
    val cycle = compileLoopBody(compiler, loop)
    compileIfIoBitExpression(compiler, ioBit, loop.getLocation(), invert)
    compiler.compileJump("rjmp", cycle.startLabel, loop.getLocation())
    compiler.addLabel(cycle.endLabel, loop.getLocation())
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