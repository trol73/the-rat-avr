package ru.trolsoft.therat.viper.compile

import ru.trolsoft.compiler.compiler.errorUnsupportedOperation
import ru.trolsoft.compiler.general.CycleBlock
import ru.trolsoft.compiler.parser.BinaryOperNode
import ru.trolsoft.compiler.parser.LoopNode
import ru.trolsoft.compiler.parser.Operator
import ru.trolsoft.compiler.parser.RegisterNode
import ru.trolsoft.compiler.parser.SuffixOperNode
import ru.trolsoft.therat.viper.ViperRatCompiler

internal fun ViperRatCompiler.compileLoop(loop: LoopNode) {
    if (loop.cond == null) {
        val cycle = compileLoopBody(loop)
        compileAsmLabel("jmp", cycle.startLabel, loop.getLocation())
        return
    }
    val condition = resolveArg(loop.cond)
    when {
        condition is RegisterNode -> compileLoopReg(loop, condition)
        condition is BinaryOperNode && condition.operator == Operator.ASSIGN && condition.left is RegisterNode -> {
            compileBinaryOp(condition)
            compileLoopReg(loop, condition.left)
        }
        else -> errorUnsupportedOperation(loop.cond)
    }
}

/**
 * Возвращает пару тело цикла + операцию перехода в его начало. Если последняя null, будет использован jmp _lbl_start
 */
private fun ViperRatCompiler.compileLoopBody(loop: LoopNode): CycleBlock {
    val startLabel = addProcLabel("loop", loop.getLocation(), true)
    val endLabel = resolveProcLabel("loopEnd", loop.getLocation(), true)
    val cycleRec = CycleBlock(startLabel, endLabel)
    cycles.push(cycleRec)

    compileNode(loop.body)
    cycles.pop()

    return cycleRec
}


private fun ViperRatCompiler.compileLoopReg(loop: LoopNode, reg: RegisterNode) {
    val cycle = compileLoopBody(loop)
    compileSuffixOp(SuffixOperNode(Operator.DEC, reg))
    compileAsmLabel("jnz", cycle.startLabel, loop.getLocation())
    addLabel(cycle.endLabel, loop.getLocation())
}