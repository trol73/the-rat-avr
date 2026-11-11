package ru.trolsoft.therat.i8085.compile

import ru.trolsoft.compiler.compiler.*
import ru.trolsoft.compiler.compiler.errorParentCycleNotFound
import ru.trolsoft.compiler.compiler.errorUnexpectedExpression
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.i8085.I8085RatCompiler

internal fun I8085RatCompiler.compileIf(ifNode: IfNode) {
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
        node.thenBlock is CompoundStmt && node.elseBlock != null -> {
            compileFullIfElse(node, node.thenBlock, node.elseBlock)
        }
        else -> {
            errorWrongExpression(node)
        }
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

private fun I8085RatCompiler.compileFullIfElse(
    node: IfNode,
    thenBlock: CompoundStmt,
    elseBlock: Node
) {
    val elseLabel = resolveProcLabel("if_else", node.getLocation(), true)
    val endLabel = resolveProcLabel("if_end", node.getLocation(), true)
    val ifGotoNode = IfNode(
        node.getLocation(),
        PrefixOperNode(node.cond.getLocation(), Operator.LOGICAL_NOT, node.cond),
        GotoNode(node.getLocation(), elseLabel),
        null
    )
    compileIf(ifGotoNode)
    compileNode(thenBlock)
    compileAsmLabel("jmp", endLabel, thenBlock.getLocation())
    addLabel(elseLabel, node.getLocation())
    compileNode(elseBlock)
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

private fun I8085RatCompiler.compileIfGoto(node: IfNode) {
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

private fun I8085RatCompiler.compileIfWithEmptyThenBlock(node: IfNode) {
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


fun I8085RatCompiler.resolveConditionAndBreakAndContinue(ifNode: IfNode): IfNode {
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

private fun I8085RatCompiler.compileSimpleIfGoto(node: IfNode) {
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
            val left = resolveArg(condition.left)
            val right = resolveArg(condition.right)
            when {
                left is RegisterNode && right is NumberNode -> {
                    compileIfRegValue(condition.operator, left, right, label, signed, node.getLocation())
                }
                else -> errorUnexpectedExpression(condition)
            }
        }
    }
}

private fun I8085RatCompiler.compileIfRegValue(operator: Operator, left: RegisterNode, right: NumberNode, label: String,
                                             signed: Boolean, location: SourceLocation) {
    if (left.isA()) {
        when (operator) {
            Operator.EQ -> {
//                if (right.value == 0) {
//                    TODO
//                } else {
                    compileAsmNum("cpi", right, location)
                    compileAsmLabel("jz", label, location)
//                }
            }
            Operator.NOT_EQ -> {
//                if (right.value == 0) {
//                    TODO
//                } else {
                    compileAsmNum("cpi", right, location)
                    compileAsmLabel("jnz", label, location)
//                }
            }
            else -> errorUnexpectedExpression(left)
        }
    } else {
        errorUnexpectedExpression(left)
    }

}

private fun I8085RatCompiler.compileIfWithoutElse(cond: Node, thenBlock: Node, loc: SourceLocation) {
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

//        cond is RegisterBitNode && isSingleInstructionNode(thenBlock) -> {
//            compileIfRegisterBitExpression(cond, false)
//            compileNode(thenBlock)
//        }
    }
}
