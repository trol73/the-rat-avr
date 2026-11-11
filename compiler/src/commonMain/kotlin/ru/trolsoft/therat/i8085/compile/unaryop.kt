package ru.trolsoft.therat.i8085.compile

import ru.trolsoft.compiler.compiler.errorWrongExpression
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.i8085.I8085RatCompiler
import ru.trolsoft.therat.i8085.RegisterPairNode

internal fun I8085RatCompiler.compileSuffixOp(node: SuffixOperNode) {
    when (val expr = resolveArg(node.expression)) {
        is RegisterNode -> compileRegOp(expr, node.operator)
        is RegisterPairNode -> compileRegPairOp(expr, node.operator)
        else -> errorWrongExpression(node)
    }

}


internal fun I8085RatCompiler.compileRegOp(reg: RegisterNode, operator: Operator) {
    when (operator) {
        Operator.INC -> compileAsmReg("inr", reg, reg.getLocation())
        Operator.DEC -> compileAsmReg("dcr", reg, reg.getLocation())
        else -> errorWrongExpression(reg)
    }
}

internal fun I8085RatCompiler.compileRegPairOp(pair: RegisterPairNode, operator: Operator) {
    when (operator) {
        Operator.INC -> compileAsmPair("inx", pair, pair.getLocation())
        Operator.DEC -> compileAsmPair("dcx", pair, pair.getLocation())
        else -> errorWrongExpression(pair)
    }
}
