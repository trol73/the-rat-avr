package ru.trolsoft.therat.avr.compiler

import ru.trolsoft.compiler.compiler.errorWrongExpression
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.avr.AvrRatCompiler
import ru.trolsoft.therat.avr.errorUnsupportedOperation

internal fun AvrRatCompiler.compileSuffixOp(node: SuffixOperNode) {
    when (val expr = resolveArg(node.expression)) {
        is RegisterNode -> compileRegOp(expr, node.operator)
        is RegisterGroupNode -> compileRegGroupOp(expr, node.operator)
        else -> errorWrongExpression(node)
    }

}


private fun AvrRatCompiler.compileRegOp(reg: RegisterNode, operator: Operator) {
    when (operator) {
        Operator.INC -> compileAsmReg("inc", reg, reg.getLocation())
        Operator.DEC -> compileAsmReg("dec", reg, reg.getLocation())
        else -> errorWrongExpression(reg)
    }
}

private fun AvrRatCompiler.compileRegGroupOp(group: RegisterGroupNode, operator: Operator) {
    when (operator) {
        Operator.INC ->
            compilePlusAssignOp(BinaryOperNode(group.getLocation(), Operator.PLUS_ASSIGN, group, NumberNode(group.getLocation(), 1)))
        Operator.DEC ->
            compilePlusAssignOp(BinaryOperNode(group.getLocation(), Operator.MINUS_ASSIGN, group, NumberNode(group.getLocation(), 1)))
        else ->
            errorUnsupportedOperation(group)
    }
}
