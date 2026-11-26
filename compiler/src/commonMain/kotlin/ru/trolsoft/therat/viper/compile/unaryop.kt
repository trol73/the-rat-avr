package ru.trolsoft.therat.viper.compile

import ru.trolsoft.compiler.compiler.errorWrongExpression
import ru.trolsoft.compiler.parser.Operator
import ru.trolsoft.compiler.parser.RegisterNode
import ru.trolsoft.compiler.parser.SuffixOperNode
import ru.trolsoft.therat.viper.RegisterPairNode
import ru.trolsoft.therat.viper.ViperRatCompiler

internal fun ViperRatCompiler.compileSuffixOp(node: SuffixOperNode) {
    when (val expr = resolveArg(node.expression)) {
        is RegisterNode -> compileRegOp(expr, node.operator)
        is RegisterPairNode -> compileRegPairOp(expr, node.operator)
        else -> errorWrongExpression(node)
    }
}

internal fun ViperRatCompiler.compileRegOp(reg: RegisterNode, operator: Operator) {
    when (operator) {
        Operator.INC -> compileAsmReg("inc", reg, reg.getLocation())
        Operator.DEC -> compileAsmReg("dec", reg, reg.getLocation())
        else -> errorWrongExpression(reg)
    }
}

internal fun ViperRatCompiler.compileRegPairOp(pair: RegisterPairNode, operator: Operator) {
    when (operator) {
        Operator.INC -> compileAsmPair("inc", pair, pair.getLocation())
        Operator.DEC -> compileAsmPair("dec", pair, pair.getLocation())
        else -> errorWrongExpression(pair)
    }
}
