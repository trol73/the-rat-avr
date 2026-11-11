package ru.trolsoft.therat.i8085.compile

import ru.trolsoft.compiler.compiler.*
import ru.trolsoft.compiler.compiler.errorLimitedValueExpected
import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.i8085.*
import ru.trolsoft.therat.i8085.errorPairExpectedButRegisterFound
import ru.trolsoft.therat.i8085.errorRegisterExpectedButPairFound
import kotlin.math.absoluteValue


internal fun I8085RatCompiler.compileBinaryOp(binop: BinaryOperNode) {
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

internal fun I8085RatCompiler.compileAssignOp(assign: BinaryOperNode) {
    compileAssignOp(assign.left, assign.right, assign.getLocation())
}

internal fun I8085RatCompiler.compileAssignOp(leftArg: Node, rightArg: Node, loc: SourceLocation) {
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

fun I8085RatCompiler.compileAssignMemTo(memDest: MemArrayNode, src: Node, loc: SourceLocation) {
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

private fun I8085RatCompiler.isRegWithOptionalIncOrDec(node: Node, regName: String? = null): Boolean {
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

private fun I8085RatCompiler.compilePrefixIncDec(node: Node, loc: SourceLocation): Node =
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

private fun I8085RatCompiler.compileSuffixIncDec(node: Node, loc: SourceLocation): Node =
    when (node) {
        is SuffixOperNode -> {
            compileIncDec(node.expression, node.operator, loc)
            node.expression
        }
        is PrefixOperNode -> node.expression
        is RegisterNode, is RegisterPairNode -> node
        else -> internalError()
    }

private fun I8085RatCompiler.compileWithPrefixOrSuffix(node: Node, loc: SourceLocation, operation: (node: Node) -> Unit) {
    val regOrPair = compilePrefixIncDec(node, loc)
    operation.invoke(resolveArg(regOrPair))
    compileSuffixIncDec(node, loc)
}

private fun I8085RatCompiler.compileIncDec(reg: Node, operator: Operator, loc: SourceLocation) {
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

internal fun I8085RatCompiler.compileAssignRegisterTo(reg: RegisterNode, right: Node, loc: SourceLocation) {
    val src = if (right is VariableNode) right.toMemArrayItem() else resolveArg(right)
    when {
        src is NumberNode -> {
            if (src.value == 0 && reg.isA()) {
                compileAsmReg("xra", reg, loc)
            } else {
                if (src.value < 0 && src.value >= -128) {
                    compileAsmRegNum("mvi", reg, src.value and 0xff, loc)
                } else if (src.value in 0..0xff) {
                    compileAsmRegNum("mvi", reg, src, loc)
                } else {
                    errorLimitedValueExpected("Byte", src.getLocation())
                }
            }
        }
        src is RegisterNode -> {
            if (reg.reg != src.reg) {
                compileAsmRegReg("mov", reg, src, loc)
            }
        }
        src is BinaryOperNode -> {
            compileAssignRegistersToBinaryOp(reg, src, loc)
        }
        reg.isA() && src is MemArrayNode -> {
            val index = resolveArg(src.index)
            when {
                index is NumberNode -> {
                    compileAsmNum("lda", index, loc)
                }

                isRegWithOptionalIncOrDec(index, "HL") -> {
                    compileWithPrefixOrSuffix(index, loc) {
                        compileAsmRegReg("mov", reg, RegisterNode(src.getLocation(), "M"), loc)
                    }
                }
                (isRegWithOptionalIncOrDec(index, "BC") || isRegWithOptionalIncOrDec(index, "DE")) -> {
                    compileWithPrefixOrSuffix(index, loc) { pair ->
                        compileAsmPair("ldax", pair as RegisterPairNode, loc)
                    }
                }
                else -> errorWrongExpression(reg)
            }
        }
        src is MemArrayNode && isRegWithOptionalIncOrDec(src.index, "HL") ->
            compileWithPrefixOrSuffix(src.index, loc) {
                compileAsmRegReg("mov", reg, RegisterNode(src.getLocation(), "M"), loc)
            }

        src is SuffixOperNode && (src.operator == Operator.INC || src.operator == Operator.DEC) -> {
            compileAssignRegisterTo(reg, resolveArg(src.expression), loc)
            compileRegOp(reg, src.operator)
        }
        src is RegisterPairNode -> errorRegisterExpectedButPairFound(src)
        src is PortArrayNode -> {
            if (!reg.isA()) {
                errorPortsCanOnlyReadWriteToA(reg)
            }
            if (src.index !is NumberNode) {
                errorNumberExpected(src.index)
            }
            if (src.index.value < 0 || src.index.value > 0xff) {
                errorWrongPortOffset(src.index)
            }
            compileAsmNum("in", src.index, loc)
        }
        reg.isA() && src is PrefixOperNode && src.operator == Operator.BINARY_NOT -> {
            if (!src.expression.isA()) {
                compileAssignRegisterTo(reg, resolveArg(src.expression), loc)
            }
            compileAsm("cma", loc)
        }
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


fun I8085RatCompiler.compileAssignPairTo(dest: RegisterPairNode, right: Node, loc: SourceLocation) {
    val src = if (right is VariableNode) right.toMemArrayItem() else resolveArg(right)
    when {
        src is NumberNode -> compileAsmPairNum("lxi", dest, src, loc)
        src is IdentifierNode -> compileAsmPairLabel("lxi", dest, src, loc)
        src is BinaryOperNode -> compileAssignRegistersToBinaryOp(dest, src, loc)
        dest.isSP() && src.isHL() -> compileAsm("sphl", loc)
        dest.isHL() && src is MemArrayNode && src.index is NumberNode -> {
            compileAsmNum("lhld", src.index, loc)
        }
        src is RegisterPairNode && src.pair == dest.pair -> {
            // игнорируем, это может быть аргумент функции, выполняющий роль комментария
        }
        src is RegisterNode -> errorPairExpectedButRegisterFound(src)
        (dest.isHL() || dest.isDE()) && src is VariableNode && src.variable.arraySize != null -> {
            compileAsmPairNum("lxi", dest, NumberNode(src.getLocation(), src.address, 16), loc)
        }

        else -> {
            errorWrongExpression(loc)
        }
    }
}

private fun I8085RatCompiler.compileAssignRegistersToBinaryOp(reg: Node, binop: BinaryOperNode, loc: SourceLocation) {
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
//        return
//        val expanded = mutableListOf<Node>()
//        val base = expandBinaryOp(simplified, expanded, reg)
//        when {
//            base is RegisterNode || base is RegisterPairNode -> {
//                if (base is RegisterNode && reg is RegisterNode && base.reg != reg.reg) {
//                    compileAssignOp(reg, base, loc)
//                } else if (base is RegisterPairNode && reg is RegisterPairNode && base.pair != reg.pair) {
//                    compileAssignOp(reg, base, loc)
//                }
//                for (n in expanded) {
//                    if (n is BinaryOperNode) {
//                        val l = n.getLocation()
//                        when (base) {
//                            n.left -> when (n.operator) {
//                                Operator.PLUS -> compileBinaryOp(BinaryOperNode(l, Operator.PLUS_ASSIGN, reg, n.right))
//                                Operator.MINUS -> compileBinaryOp(BinaryOperNode(l, Operator.MINUS_ASSIGN, reg, n.right))
//                                Operator.OR -> compileBinaryOp(BinaryOperNode(l, Operator.OR_ASSIGN, reg, n.right))
//                                Operator.AND -> compileBinaryOp(BinaryOperNode(l, Operator.AND_ASSIGN, reg, n.right))
//                                Operator.XOR -> compileBinaryOp(BinaryOperNode(l, Operator.XOR_ASSIGN, reg, n.right))
//                                Operator.SHL -> compileBinaryOp(BinaryOperNode(l, Operator.SHL_ASSIGN, reg, n.right))
//                                Operator.SHR -> compileBinaryOp(BinaryOperNode(l, Operator.SHR_ASSIGN, reg, n.right))
//                                Operator.ASSIGN -> compileBinaryOp(BinaryOperNode(l, Operator.ASSIGN, reg, n.right))
//                                Operator.MUL -> compileBinaryOp(BinaryOperNode(l, Operator.MUL_ASSIGN, reg, n.right))
//                                else -> errorWrongOperation(n)
//                            }
//
//                            n.right -> when (n.operator) {
//                                Operator.PLUS -> compileBinaryOp(BinaryOperNode(l, Operator.PLUS_ASSIGN, reg, n.left))
//                                Operator.MINUS -> compileBinaryOp(BinaryOperNode(l, Operator.MINUS_ASSIGN, reg, n.left))
//                                // Operator.OR -> compileBinaryOp(compiler, BinaryOperNode(Operator.OR_ASSIGN, reg, n.binop))
//                                // Operator.AND -> compileBinaryOp(compiler, BinaryOperNode(Operator.AND_ASSIGN, reg, n.binop))
//                                // Operator.SHL -> compileBinaryOp(compiler, BinaryOperNode(Operator.SHL_ASSIGN, reg, n.binop))
//                                // Operator.SHR -> compileBinaryOp(compiler, BinaryOperNode(Operator.SHR_ASSIGN, reg, n.binop))
//                                Operator.ASSIGN -> compileBinaryOp(BinaryOperNode(l, Operator.ASSIGN, reg, n.left))
//                                else -> errorWrongOperation(n)
//                            }
//
//                            else -> errorWrongOperation(binop)
//                        }
//                    } else {
//                        errorWrongOperation(binop)
//                    }
//                }
//            }
//
////            base is PrgArrayNode || base is MemArrayNode || base is IoPortNode -> {
////                compileAssignRegisterTo(reg, base, loc)
////                if (expanded.size == 1) {
////                    compileBinaryOp(
////                        BinaryOperNode(loc, simplified.operator.toSelfAssign(), reg, simplified.right)
////                    )
////                }
////                return
////            }
//
//            expanded.size == 1 -> {
//                compileAssignOp(reg, simplified.left, loc)
//                compileBinaryOp(
//                    BinaryOperNode(loc, simplified.operator.toSelfAssign(), reg, simplified.right)
//                )
//            }
//            else -> errorWrongOperation(binop)
//        }
    } else {
        compileAssignOp(reg, simplified, loc)
    }
}


internal fun I8085RatCompiler.compileAssignNodeToRegister(left: Node, reg: RegisterNode, loc: SourceLocation) {
    val dest = if (left is VariableNode) left.toMemArrayItem() else left
    if (dest is MemArrayNode) {
        when {
            isRegWithOptionalIncOrDec(dest.index, "HL") -> {
                compileWithPrefixOrSuffix(dest.index, loc) {
                    compileAssignRegisterTo(RegisterNode(dest.getLocation(), "M"), reg, loc)
                }
            }

            reg.isA() && (isRegWithOptionalIncOrDec(dest.index, "BC") || isRegWithOptionalIncOrDec(
                dest.index,
                "DE"
            )) -> {
                compileWithPrefixOrSuffix(dest.index, loc) { pair ->
                    compileAsmPair("stax", pair as RegisterPairNode, loc)
                }
            }

            dest.index is NumberNode -> {
                compileAsmNum("sta", dest.index, loc)
            }
        }
    } else if (dest is PortArrayNode) {
        if (!reg.isA()) {
            errorPortsCanOnlyReadWriteToA(reg)
        }
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

fun I8085RatCompiler.compilePlusAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    val loc = binop.getLocation()
    val minus = binop.operator == Operator.MINUS_ASSIGN
    compilePlusMinusAssignOp(left, right, loc, minus)
}

fun I8085RatCompiler.compilePlusMinusAssignOp(left: Node, right: Node, loc: SourceLocation, minus: Boolean) {
    if (left is RegisterNode && (left.isA() || right is NumberNode && right.value.absoluteValue == 1) && right !is BinaryOperNode) {
        when (right) {
            is NumberNode -> {
                val num = right.value
                val signed = if (minus) -num else num
                when {
                    num == 0 -> warning("zero argument", right)
                    signed == 1 -> compileAsmReg("inr", left, loc)
                    signed == -1 -> compileAsmReg("dcr", left, loc)
                    else -> {
                        if (minus) {
                            compileAsmNum("sui", num, loc)
                        } else {
                            compileAsmNum("adi", num, loc)
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
    } else if (left is RegisterPairNode && left.isHL() && right is RegisterPairNode && !minus) {
        compileAsmPair("dad", right, loc)
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

fun I8085RatCompiler.compileOrAndXorAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    if (!left.isA()) {
        errorWrongExpression(binop)
    }
    when (right) {
        is NumberNode -> {
            when (binop.operator) {
                Operator.AND_ASSIGN -> compileAsmNum("ani", right, binop.getLocation())
                Operator.OR_ASSIGN -> compileAsmNum("ori", right, binop.getLocation())
                Operator.XOR_ASSIGN -> compileAsmNum("xri", right, binop.getLocation())
                else -> errorWrongExpression(binop)
            }
        }

        is RegisterNode -> {
            when (binop.operator) {
                Operator.AND_ASSIGN -> compileAsmReg("ana", right, binop.getLocation())
                Operator.OR_ASSIGN -> compileAsmReg("ora", right, binop.getLocation())
                Operator.XOR_ASSIGN -> compileAsmReg("xra", right, binop.getLocation())
                else -> errorWrongExpression(binop)
            }
        }

        else -> {
            errorWrongExpression(binop)
        }
    }
}

fun I8085RatCompiler.compileShiftAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    if (right !is NumberNode) {
        errorWrongExpression(binop)
    }
    if (left.isA()) {
        for (i in 1..right.value) {
            when (binop.operator) {
                Operator.SHL_ASSIGN -> compileAsm("rlc", binop.getLocation())
                Operator.SHR_ASSIGN -> compileAsm("rrc", binop.getLocation())
                else -> internalError()
            }
        }
    } else if (left.isHL() && binop.operator == Operator.SHL_ASSIGN) {
        for (i in 1..right.value) {
            compileAsmPair("dad", RegisterPairNode(left.getLocation(), "HL"), binop.getLocation())
        }
    } else {
        errorWrongExpression(binop)
    }
}

fun I8085RatCompiler.compileMulAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    if (right !is NumberNode || left !is RegisterNode || !left.isA()) {
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

fun RegisterNode.isA() = reg.uppercase() == "A"

fun RegisterPairNode.isSP() = pair.uppercase() == "SP"
fun RegisterPairNode.isHL() = pair.uppercase() == "HL"
fun RegisterPairNode.isBC() = pair.uppercase() == "BC"
fun RegisterPairNode.isDE() = pair.uppercase() == "DE"
fun Node.isA() = this is RegisterNode && this.isA()
fun Node.isHL() = this is RegisterPairNode && this.isHL() || this is RegisterNode && this.reg.uppercase() == "HL"

fun Node.isSP() = this is RegisterPairNode && this.isSP() || this is RegisterNode && this.reg.uppercase() == "SP"
fun Node.isBC() = this is RegisterPairNode && this.isBC()
fun Node.isDE() = this is RegisterPairNode && this.isDE()