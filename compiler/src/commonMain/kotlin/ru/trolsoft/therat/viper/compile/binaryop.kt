package ru.trolsoft.therat.viper.compile

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.compiler.errorLimitedValueExpected
import ru.trolsoft.compiler.compiler.errorUnsupportedOperation
import ru.trolsoft.compiler.compiler.errorWrongExpression
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.BinaryOperNode
import ru.trolsoft.compiler.parser.MemArrayNode
import ru.trolsoft.compiler.parser.Node
import ru.trolsoft.compiler.parser.NumberNode
import ru.trolsoft.compiler.parser.Operator
import ru.trolsoft.compiler.parser.PortArrayNode
import ru.trolsoft.compiler.parser.PrefixOperNode
import ru.trolsoft.compiler.parser.RegisterNode
import ru.trolsoft.compiler.parser.SuffixOperNode
import ru.trolsoft.compiler.parser.VariableNode
import ru.trolsoft.compiler.parser.errorNumberExpected
import ru.trolsoft.therat.viper.RegisterPairNode
import ru.trolsoft.therat.viper.ViperRatCompiler
import kotlin.math.absoluteValue

internal fun ViperRatCompiler.compileBinaryOp(binop: BinaryOperNode) {
    when (binop.operator) {
        Operator.ASSIGN -> compileAssignOp(binop)
        Operator.PLUS_ASSIGN -> compilePlusAssignOp(binop)
        Operator.MINUS_ASSIGN -> compilePlusAssignOp(binop)
        Operator.OR_ASSIGN -> compileOrAndXorAssignOp(binop)
        Operator.XOR_ASSIGN -> compileOrAndXorAssignOp(binop)
        Operator.AND_ASSIGN -> compileOrAndXorAssignOp(binop)
        Operator.SHL_ASSIGN -> compileShiftAssignOp(binop)
        Operator.SHR_ASSIGN -> compileShiftAssignOp(binop)
        Operator.MUL_ASSIGN -> compileMulAssignOp(binop)
//        Operator.ARROW_LEFT -> compileArrowLeftOp(binop)

        else -> errorWrongExpression(binop)
    }
}

internal fun ViperRatCompiler.compileAssignOp(assign: BinaryOperNode) {
    compileAssignOp(assign.left, assign.right, assign.getLocation())
}

internal fun ViperRatCompiler.compileAssignOp(leftArg: Node, rightArg: Node, loc: SourceLocation) {
    val left = resolveArg(leftArg)
    val right = resolveArg(rightArg)
    when {
        left is RegisterNode -> {
            compileAssignRegisterTo(left, right, loc)
        }
        left is RegisterPairNode -> {
            compileAssignPairTo(left, right, loc)
        }
        right is RegisterNode -> {
            compileAssignNodeToRegister(left, right, loc)
        }
        right is BinaryOperNode && right.operator == Operator.ASSIGN -> {
            compileAssignOp(right)
            compileAssignOp(left, right.left, loc)
        }
        left is MemArrayNode -> {
            compileAssignMemTo(left, right, loc)
        }
        left is VariableNode -> {
            val leftRes = left.toMemArrayItem()
            if (leftRes is MemArrayNode) {
                compileAssignMemTo(leftRes, right, loc)
            } else {
                errorWrongExpression(leftArg)
            }
        }
        else -> {
            errorWrongExpression(leftArg)
        }
    }
}

fun ViperRatCompiler.compileAssignMemTo(memDest: MemArrayNode, src: Node, loc: SourceLocation) {
    when {
        memDest.index is NumberNode -> {
            when {
                isRegWithOptionalIncOrDec(src, "HL") ->
                    compileWithPrefixOrSuffix(src, loc) {
                        compileAsmNum("shld", memDest.index, loc)
                    }
                isRegWithOptionalIncOrDec(src, "A") ->
                    compileWithPrefixOrSuffix(src, loc) {
                        compileAsmNum("sta", memDest.index, loc)
                    }
                else -> errorWrongExpression(src)
            }
        }
        src is NumberNode -> {
            when {
                isRegWithOptionalIncOrDec(memDest.index, "HL") -> {
                    compileWithPrefixOrSuffix(memDest.index, loc) {
                        compileAsmRegNum("mvi", RegisterNode(memDest.getLocation(), "M"), src, loc)
                    }
                }
            }
        }
        isRegWithOptionalIncOrDec(src) && isRegWithOptionalIncOrDec(memDest.index, "HL") ->
            compileWithPrefixOrSuffix(memDest.index, loc) {
                compileWithPrefixOrSuffix(src, loc) { reg: Node ->
                    if (reg is RegisterNode) {
                        compileAsmRegReg("mov", RegisterNode(memDest.getLocation(), "M"), reg, loc)
                    } else {
                        internalError()
                    }
                }
            }
    }
}

private fun ViperRatCompiler.isRegWithOptionalIncOrDec(node: Node, regName: String? = null): Boolean {
    val expr = when (node) {
        is PrefixOperNode -> {
            if (node.operator != Operator.INC && node.operator != Operator.DEC) {
                return false
            }
            node.expression
        }
        is SuffixOperNode -> {
            if (node.operator != Operator.DEC && node.operator != Operator.INC) {
                return false
            }
            node.expression
        }
        is RegisterNode, is RegisterPairNode -> node
        else -> return false
    }
    if (regName == null) {
        return expr is RegisterNode || expr is RegisterPairNode
    }
    val arg = resolveArg(expr)
    return arg is RegisterNode && arg.reg.equals(regName, true) || arg is RegisterPairNode && arg.pair.equals(regName, true)
}

private fun ViperRatCompiler.compilePrefixIncDec(node: Node, loc: SourceLocation): Node =
    when (node) {
        is PrefixOperNode -> {
            compileIncDec(node.expression, node.operator, loc)
            node.expression
        }
        is SuffixOperNode ->  {
            node.expression
        }
        is RegisterNode, is RegisterPairNode -> node
        else -> internalError()
    }

private fun ViperRatCompiler.compileSuffixIncDec(node: Node, loc: SourceLocation): Node =
    when (node) {
        is SuffixOperNode -> {
            compileIncDec(node.expression, node.operator, loc)
            node.expression
        }
        is PrefixOperNode -> node.expression
        is RegisterNode, is RegisterPairNode -> node
        else -> internalError()
    }

private fun ViperRatCompiler.compileWithPrefixOrSuffix(node: Node, loc: SourceLocation, operation: (node: Node) -> Unit) {
    val regOrPair = compilePrefixIncDec(node, loc)
    operation.invoke(resolveArg(regOrPair))
    compileSuffixIncDec(node, loc)
}

private fun ViperRatCompiler.compileIncDec(reg: Node, operator: Operator, loc: SourceLocation) {
    when (val arg = resolveArg(reg)) {
        is RegisterNode -> {
            when (operator) {
                Operator.INC -> compileAsmReg("inr", arg, loc)
                Operator.DEC -> compileAsmReg("dcr", arg, loc)
                else -> internalError()
            }
        }
        is RegisterPairNode -> {
            when (operator) {
                Operator.INC -> compileAsmPair("inx", arg, loc)
                Operator.DEC -> compileAsmPair("dcx", arg, loc)
                else -> internalError()
            }
        }
        else -> internalError()
    }
}

internal fun ViperRatCompiler.compileAssignRegisterTo(reg: RegisterNode, right: Node, loc: SourceLocation) {
    val src = if (right is VariableNode) right.toMemArrayItem() else resolveArg(right)
    when {
        src is NumberNode -> {
            if (src.value == 0 && reg.isLowerReg()) {
                compileAsmRegReg("xor", reg, reg, loc)
            } else if (src.value < 0 && src.value >= -128) {
                compileAsmRegNum("movi", reg, src.value and 0xff, loc)
            } else if (src.value in 0..0xff) {
                compileAsmRegNum("movi", reg, src, loc)
            } else {
                errorLimitedValueExpected("Byte", src.getLocation())
            }
        }
        src is RegisterNode -> {
            if (reg.reg != src.reg) {
                compileAsmRegReg("mov", reg, src, loc)
            } else {
                TODO()
            }
        }
        src is BinaryOperNode -> {
            compileAssignRegistersToBinaryOp(reg, src, loc)
        }
        src is MemArrayNode -> {
            TODO()
        }
//        reg.isA() && src is MemArrayNode -> {
//            val index = resolveArg(src.index)
//            when {
//                index is NumberNode -> {
//                    compileAsmNum("lda", index, loc)
//                }
//
//                isRegWithOptionalIncOrDec(index, "HL") -> {
//                    compileWithPrefixOrSuffix(index, loc) {
//                        compileAsmRegReg("mov", reg, RegisterNode(src.getLocation(), "M"), loc)
//                    }
//                }
//                (isRegWithOptionalIncOrDec(index, "BC") || isRegWithOptionalIncOrDec(index, "DE")) -> {
//                    compileWithPrefixOrSuffix(index, loc) { pair ->
//                        compileAsmPair("ldax", pair as RegisterPairNode, loc)
//                    }
//                }
//                else -> errorWrongExpression(reg)
//            }
//        }
//        src is MemArrayNode && isRegWithOptionalIncOrDec(src.index, "HL") ->
//            compileWithPrefixOrSuffix(src.index, loc) {
//                compileAsmRegReg("mov", reg, RegisterNode(src.getLocation(), "M"), loc)
//            }

//        src is SuffixOperNode && (src.operator == Operator.INC || src.operator == Operator.DEC) -> {
//            compileAssignRegisterTo(reg, resolveArg(src.expression), loc)
//            compileRegOp(reg, src.operator)
//        }
//        src is RegisterPairNode -> errorRegisterExpectedButPairFound(src)
//        src is PortArrayNode -> {
//            if (!reg.isA()) {
//                errorPortsCanOnlyReadWriteToA(reg)
//            }
//            if (src.index !is NumberNode) {
//                errorNumberExpected(src.index)
//            }
//            if (src.index.value < 0 || src.index.value > 0xff) {
//                errorWrongPortOffset(src.index)
//            }
//            compileAsmNum("in", src.index, loc)
//        }
//        reg.isA() && src is PrefixOperNode && src.operator == Operator.BINARY_NOT -> {
//            if (!src.expression.isA()) {
//                compileAssignRegisterTo(reg, resolveArg(src.expression), loc)
//            }
//            compileAsm("cma", loc)
//        }
        isRegWithOptionalIncOrDec(src) ->
            compileWithPrefixOrSuffix(src, loc) {
                val srcReg = it as RegisterNode
                if (reg.reg != srcReg.reg) {
                    compileAsmRegReg("mov", reg, srcReg, loc)
                }
            }
        else -> {
            errorWrongExpression(reg)
        }
    }
}


fun ViperRatCompiler.compileAssignPairTo(dest: RegisterPairNode, right: Node, loc: SourceLocation) {
    val src = if (right is VariableNode) right.toMemArrayItem() else resolveArg(right)
    when {
//        src is NumberNode -> compileAsmPairNum("lxi", dest, src, loc)
//        src is IdentifierNode -> compileAsmPairLabel("lxi", dest, src, loc)
//        src is BinaryOperNode -> compileAssignRegistersToBinaryOp(dest, src, loc)
//        dest.isSP() && src.isHL() -> compileAsm("sphl", loc)
//        dest.isHL() && src is MemArrayNode && src.index is NumberNode -> {
//            compileAsmNum("lhld", src.index, loc)
//        }
//        src is RegisterPairNode && src.pair == dest.pair -> {
//            // игнорируем, это может быть аргумент функции, выполняющий роль комментария
//        }
//        src is RegisterNode -> errorPairExpectedButRegisterFound(src)
//        (dest.isHL() || dest.isDE()) && src is VariableNode && src.variable.arraySize != null -> {
//            compileAsmPairNum("lxi", dest, NumberNode(src.getLocation(), src.address, 16), loc)
//        }

        else -> {
            errorWrongExpression(loc)
        }
    }
}

private fun ViperRatCompiler.compileAssignRegistersToBinaryOp(reg: Node, binop: BinaryOperNode, loc: SourceLocation) {
    if (reg !is RegisterNode && reg !is RegisterPairNode) {
        errorWrongExpression(binop)
    }
    if (binop.operator == Operator.ASSIGN) {
        compileAssignOp(binop)
        compileAssignOp(reg, resolveArg(binop.left), loc)
        return
    }
    val simplified = simplifyBinaryOp(binop)
    if (simplified is BinaryOperNode) {
        val parts = decomposeAssignOperation(simplified, reg)
        for (op in parts) {
            compileBinaryOp(op)
        }
    } else {
        compileAssignOp(reg, simplified, loc)
    }
}


internal fun ViperRatCompiler.compileAssignNodeToRegister(left: Node, reg: RegisterNode, loc: SourceLocation) {
    val dest = if (left is VariableNode) left.toMemArrayItem() else left
    if (dest is MemArrayNode) {
        val index = dest.index
        if (index is NumberNode) {
            compileAsmRegNum("storei", reg, index, reg.getLocation())
        } else {
            errorUnsupportedOperation(dest)
        }

        when {

//            isRegWithOptionalIncOrDec(dest.index, "HL") -> {
//                compileWithPrefixOrSuffix(dest.index, loc) {
//                    compileAssignRegisterTo(RegisterNode(dest.getLocation(), "M"), reg, loc)
//                }
//            }
//
//            reg.isA() && (isRegWithOptionalIncOrDec(dest.index, "BC") || isRegWithOptionalIncOrDec(
//                dest.index,
//                "DE"
//            )) -> {
//                compileWithPrefixOrSuffix(dest.index, loc) { pair ->
//                    compileAsmPair("stax", pair as RegisterPairNode, loc)
//                }
//            }
//
//            dest.index is NumberNode -> {
//                compileAsmNum("sta", dest.index, loc)
//            }
        }
    } else if (dest is PortArrayNode) {
        if (dest.index !is NumberNode) {
            errorNumberExpected(dest.index)
        }
        if (dest.index.value < 0 || dest.index.value > 0xff) {
            errorWrongPortOffset(dest.index)
        }
        compileAsmNum("out", dest.index, loc)

    } else {
        errorWrongExpression(dest)
    }
}

private fun VariableNode.toMemArrayItem() =
    if (variable.arraySize == null && variable.structName == null && variable.dataType != DataType.PTR && variable.dataType != DataType.STRUCTURED && variable.dataType != DataType.PRGPTR) {
        MemArrayNode(getLocation(), NumberNode(getLocation(), address, 16))
    } else {
        this
    }

fun ViperRatCompiler.compilePlusAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    val loc = binop.getLocation()
    val minus = binop.operator == Operator.MINUS_ASSIGN
    compilePlusMinusAssignOp(left, right, loc, minus)
}

fun ViperRatCompiler.compilePlusMinusAssignOp(left: Node, right: Node, loc: SourceLocation, minus: Boolean) {
    if (left is RegisterNode && (right is NumberNode && right.value.absoluteValue == 1)) {
        when (right) {
            is NumberNode -> {
                val num = right.value
                val signed = if (minus) -num else num
                when {
                    num == 0 -> warning("zero argument", right)
                    signed == 1 -> compileAsmReg("inc", left, loc)
                    signed == -1 -> compileAsmReg("dec", left, loc)
                    else -> {
//                        if (!left.isR0()) {
//                            error
//                        }
                        if (minus) {
                            compileAsmNum("subi", num, loc)
                        } else {
                            compileAsmNum("addi", num, loc)
                        }
                    }
                }
            }
            is RegisterNode -> {
                compileAsmReg(if (minus) "sub" else "add", right, loc)
            }
            is MemArrayNode -> {
                if (isRegWithOptionalIncOrDec(right.index, "HL")) {
                    compileWithPrefixOrSuffix(right.index, loc) {
                        compilePlusMinusAssignOp(left, RegisterNode(right.getLocation(), "M"), loc, minus)
                    }
                } else {
                    errorWrongExpression(right)
                }
            }
            else -> {
                errorWrongExpression(left)
            }
        }
//    } else if (left is RegisterPairNode && left.isHL() && right is RegisterPairNode && !minus) {
//        compileAsmPair("dad", right, loc)
    } else if (left is RegisterNode && right is BinaryOperNode) {
        val simplified = simplifyBinaryOp(right)
        if (simplified is BinaryOperNode) {
            when (simplified.operator) {
                Operator.PLUS -> {
                    compilePlusMinusAssignOp(left, simplified.left, simplified.getLocation(), minus)
                    compilePlusMinusAssignOp(left, simplified.right, simplified.getLocation(), minus)
                }
                Operator.MINUS -> {
                    compilePlusMinusAssignOp(left, simplified.left, simplified.getLocation(), minus)
                    compilePlusMinusAssignOp(left, simplified.right, simplified.getLocation(), !minus)
                }
                else -> errorWrongExpression(simplified)
            }
        }
    } else {
        errorWrongExpression(left)
    }
}

fun ViperRatCompiler.compileOrAndXorAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)

    when (right) {
        is NumberNode -> {
            if (!left.isR0()) {
                errorWrongExpression(binop)
            }
            when (binop.operator) {
                Operator.AND_ASSIGN -> compileAsmNum("andi", right, binop.getLocation())
                Operator.OR_ASSIGN -> compileAsmNum("ori", right, binop.getLocation())
                Operator.XOR_ASSIGN -> compileAsmNum("xori", right, binop.getLocation())
                else -> errorWrongExpression(binop)
            }
        }

        is RegisterNode -> {
            if (left !is RegisterNode || !left.isLowerReg()) {
                errorWrongExpression(binop)
            }
            if (!right.isR0()) {
                errorWrongExpression(binop)
            }
            when (binop.operator) {
                Operator.AND_ASSIGN -> compileAsmRegReg("and", left, right, binop.getLocation())
                Operator.OR_ASSIGN -> compileAsmRegReg("or", left, right, binop.getLocation())
                Operator.XOR_ASSIGN -> compileAsmRegReg("xor", left, right, binop.getLocation())
                else -> errorWrongExpression(binop)
            }
        }

        else -> {
            errorWrongExpression(binop)
        }
    }
}

fun ViperRatCompiler.compileShiftAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    if (right !is NumberNode) {
        errorWrongExpression(binop)
    }
    if (left.isR0()) {
        for (i in 1..right.value) {
            when (binop.operator) {
                Operator.SHL_ASSIGN -> compileAsm("rol", binop.getLocation())
                Operator.SHR_ASSIGN -> compileAsm("ror", binop.getLocation())
                else -> internalError()
            }
        }
//    } else if (left.isHL() && binop.operator == Operator.SHL_ASSIGN) {
//        for (i in 1..right.value) {
//            compileAsmPair("dad", RegisterPairNode(left.getLocation(), "HL"), binop.getLocation())
//        }
    } else {
        errorWrongExpression(binop)
    }
}

fun ViperRatCompiler.compileMulAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    if (right !is NumberNode || left !is RegisterNode || !left.isLowerReg()) {
        errorWrongExpression(binop)
    }
    when (right.value) {
        2 -> {
            compileAsmReg("add", left, binop.getLocation())
        }
        4 -> {
            compileAsmReg("add", left, binop.getLocation())
            compileAsmReg("add", left, binop.getLocation())
        }
        8 -> {
            compileAsmReg("add", left, binop.getLocation())
            compileAsmReg("add", left, binop.getLocation())
            compileAsmReg("add", left, binop.getLocation())
        }
        else -> {
            errorWrongExpression(binop)
        }
    }
}

internal fun errorWrongPortOffset(node: NumberNode): Nothing {
    throw CompilerException("Wrong port number (0..0xFF expected).", node.getLocation())
}

private val lowerRegsSet = setOf("R0", "R1", "R2", "R3")
internal fun Node.isR0() = this is RegisterNode && this.reg == "R0"
internal fun Node.isLowerReg() = this is RegisterNode && this.reg in lowerRegsSet