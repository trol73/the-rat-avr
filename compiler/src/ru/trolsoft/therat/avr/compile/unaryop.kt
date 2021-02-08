package ru.trolsoft.therat.avr.compile

import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.avr.AvrRatCompiler
import ru.trolsoft.therat.avr.errorUnsupportedOperation

internal fun compileSuffixOp(compiler: AvrRatCompiler, node: SuffixOperNode) {
    val expr = compiler.resolveArg(node.expression)
    when (expr) {
        is RegisterNode -> compileRegOp(compiler, expr, node.operator)
        is RegisterGroupNode -> compileRegGroupOp(compiler, expr, node.operator)
        else ->
            TODO("$expr, ${node.operator} ${node.getLocation()}\n")
    }

}


private fun compileRegOp(compiler: AvrRatCompiler, reg: RegisterNode, operator: Operator) {
    when (operator) {
        Operator.INC -> compiler.compileAsmReg("inc", reg, reg.getLocation())
        Operator.DEC -> compiler.compileAsmReg("dec", reg, reg.getLocation())
        else ->
            TODO("$operator\n")
    }
}

private fun compileRegGroupOp(compiler: AvrRatCompiler, group: RegisterGroupNode, operator: Operator) {
    when (operator) {
        Operator.INC ->
            compilePlusAssignOp(compiler, BinaryOperNode(group.getLocation(), Operator.PLUS_ASSIGN, group, NumberNode(group.getLocation(), 1)))
        Operator.DEC ->
            compilePlusAssignOp(compiler, BinaryOperNode(group.getLocation(), Operator.MINUS_ASSIGN, group, NumberNode(group.getLocation(), 1)))
        else ->
            errorUnsupportedOperation(group)
    }
}
