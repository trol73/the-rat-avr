package ru.trolsoft.therat.avr.compiler

import ru.trolsoft.compiler.compiler.*
import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.arch.avr.ArgRegPair
import ru.trolsoft.therat.arch.avr.AvrCmd
import ru.trolsoft.therat.avr.*

internal fun AvrRatCompiler.compileBinaryOp(binop: BinaryOperNode) {
    when (binop.operator) {
        Operator.ASSIGN -> compileAssignOp(binop)
        Operator.PLUS_ASSIGN -> compilePlusAssignOp(binop)
        Operator.MINUS_ASSIGN -> compilePlusAssignOp(binop)
        Operator.OR_ASSIGN -> compileOrAndAssignOp(binop)
        Operator.XOR_ASSIGN -> compileXorAssignOp(binop)
        Operator.AND_ASSIGN -> compileOrAndAssignOp(binop)
        Operator.SHL_ASSIGN -> compileShiftAssignOp(binop)
        Operator.SHR_ASSIGN -> compileShiftAssignOp(binop)
        Operator.ARROW_LEFT -> compileArrowLeftOp(binop)
        Operator.ARROW_RIGHT -> compileArrowRightOp(binop)
        else -> errorWrongExpression(binop)
    }
}


internal fun AvrRatCompiler.compileAssignOp(assign: BinaryOperNode) {
    compileAssignOp(assign.left, assign.right, assign.getLocation())
}

internal fun AvrRatCompiler.compileAssignOp(leftArg: Node, rightArg: Node, loc: SourceLocation) {
    val left = resolveArg(leftArg)
    val right = resolveArg(rightArg)
    when {
        right is BinaryOperNode && right.operator == Operator.ASSIGN -> {
            compileAssignOp(right)
            compileAssignOp(left, right.left, loc)
        }
        left is RegisterNode -> {
            compileAssignRegisterTo(left, right, loc)
        }
        right is RegisterNode -> {
            compileAssignNodeToRegister(left, right, loc)
        }
        left is RegisterGroupNode -> {
            compileAssignToRegGroup(left, right, loc)
    //    } else if (right is BinaryOperNode && right.operator == Operator.ASSIGN) {
    //        compileAssignOp(right)
    //        compileAssignOp(left, right.left, loc)
        }
        left is VariableNode -> {
            compileAssignVariableTo(left, right)
        }
        left is ResolvedExternVar -> {
            compileAssignExternVariableTo(left, right)
        }
        right is ResolvedExternProc -> {
            compileAssignToExternProc(left, right)
        }
        left is IoPortBitNode && right is NumberNode -> {
            val value = getBitValue(right)
            if (left.port.name == "SREG") {
                val mnemonic = if (value == 1) "bset" else "bclr"
                val asmArgs = listOf(
                    NumberNode(loc, left.bitIndex, 10)
                )
                val cmdNode = AssemblerInstrNode(loc, mnemonic, asmArgs)
                compileAssembler(cmdNode)
            } else {
                val mnemonic = if (value == 1) "sbi" else "cbi"
                compileAsmLowerIoBit(mnemonic, left)
            }
        }
        left is RegisterBitNode && right is IoPortBitNode && right.port.isLowerPort() -> {
            compileAsmRegNum("cbr", left.reg, 1 shl left.bit, loc)
            compileAsmLowerIoBit("sbic", right)
            compileAsmRegNum("sbr", left.reg, 1 shl left.bit, loc)
        }
        left is RegisterBitNode && right is IoPortBitNode && right.port.name != "SREG" -> {
            errorCantReadHighPortBitDirectly(right.port)
        }
        left is IoPortNode && right is RegisterGroupNode -> {
            val ioSize = left.size
            if (ioSize != right.size()) {
                errorSizeMismatch(left, right)
            }
            val portName = left.name
            compileAssignOp(
                IdentifierNode(left.getLocation(), portName + "H"),
                RegisterNode(right.getLocation(), right.regs[0]), loc
            )
            compileAssignOp(
                IdentifierNode(left.getLocation(), portName + "L"),
                RegisterNode(right.getLocation(), right.regs[1]), loc
            )
        }
        left is RegisterBitNode && right is NumberNode -> {
            when (right.value) {
                0 -> compileAsmRegNum("cbr", left.reg, 1 shl left.bit, loc)
                1 -> compileAsmRegNum("sbr", left.reg, 1 shl left.bit, loc)
                else -> internalError()
            }
        }
        left is IoPortBitNode && right is RegisterBitNode -> {
            compileAssignIoBitToRegBit(left, right)
        }
        left is IoPortBitNode && right is PrefixOperNode && right.operator == Operator.LOGICAL_NOT && right.expression is RegisterBitNode -> {
            compileAssignIoBitToInvertedRegBit(left, right.expression)
        }
        left is RegisterBitNode && right is IoPortBitNode && right.port.name == "SREG" && right.bitName == "T" -> {
            compileAsmRegNum("bld", left.reg, left.bit, left.getLocation())
        }
        left is IdentifierNode -> {
            errorUndefinedIdentifier(left)
        }
        right is IdentifierNode -> {
            errorUndefinedIdentifier(right)
        }
        left is IoPortNode && right is NumberNode -> {
            errorDirectPortWrite(left, right)
        }
        left is RegisterBitNode && right is RegisterBitNode -> {
            errorDirectRegBitsWrite(left, right)
        }
        left is IoPortBitNode && right is IoPortBitNode -> {
            if (left.port.name == "SREG" && right.port.isLowerPort()) {
                compileAssignSregBitFromLowerIoBit(left.bitIndex, right)
            } else {
                errorUnsupportedOperation(loc)
            }
        }
        left is IoPortBitNode && right is PrefixOperNode && right.operator == Operator.LOGICAL_NOT && right.expression is IoPortBitNode -> {
            if (left.port.name == "SREG" && right.expression.port.isLowerPort()) {
                compileAssignSregBitFromInvertedLowerIoBit(left.bitIndex, right.expression)
            } else {
                errorUnsupportedOperation(loc)
            }
        }
        right is PrefixOperNode && (right.operator == Operator.DEC || right.operator == Operator.INC) && right.expression is RegisterNode -> {
            if (right.operator == Operator.INC) {
                compileAsmReg("inc", right.expression, right.expression.getLocation())
            } else {
                compileAsmReg("dec", right.expression, right.expression.getLocation())
            }
            compileAssignOp(left, right.expression, loc)
        }
        right is SuffixOperNode && (right.operator == Operator.DEC || right.operator == Operator.INC) && right.expression is RegisterNode -> {
            compileAssignOp(left, right.expression, loc)
            if (right.operator == Operator.INC) {
                compileAsmReg("inc", right.expression, right.expression.getLocation())
            } else {
                compileAsmReg("dec", right.expression, right.expression.getLocation())
            }
        }
        else -> {
            errorWrongExpression(loc)
        }
    }
}


fun AvrRatCompiler.compileAssignRegisterTo(reg: RegisterNode, right: Node, loc: SourceLocation) {
    when (right) {
        is NumberNode -> {
            if (right.value == 0) {
                compileAsmReg("clr", reg, loc)
            } else if (reg.getNumber() >= 16) {
                compileAsmRegNum("ldi", reg, right, loc)
            } else {
                errorHighRegisterExpected(reg)
            }
        }

        is RegisterNode -> {
            if (reg.reg != right.reg) {
                compileAsmRegReg("mov", reg, right, loc)
            }
        }

        is IoPortNode -> {
            if (right.size != 1) {
                errorSizeMismatch(reg, right)
            }
            val offset = NumberNode(right.getLocation(), right.address, 16)
            if (right.address <= 0x3f) {
                compileAsmRegNum("in", reg, offset, loc)
            } else {
                compileAsmRegNum("lds", reg, offset, loc)
            }
        }

        is MemArrayNode -> {
            // X, X++, --X
            // Y, Y++, --Y, Y+q
            // Z, Z++, --Z, Z+q
            when (val index = right.index) {
                is RegisterGroupNode, is SuffixOperNode, is PrefixOperNode -> {
                    compileAsmRegPair("ld", reg, index, loc)
                }

                is BinaryOperNode -> {
                    compileAsmRegPair("ldd", reg, index, loc)
                }

                else -> {
                    errorUnsupportedOperation(loc)
                }
            }
        }

        is PrgArrayNode -> {
            // LPM Z
            // LPM Rd, Z
            // LPM Rd, Z+
            val index = right.index
            if (index is RegisterGroupNode && index.isZ()) {
                if (reg.reg == "r0") {
                    val cmdNode = AssemblerInstrNode(loc, "lpm", null)
                    compileAssembler(cmdNode)
                } else {
                    compileAsmRegPair("lpm", reg, index, loc)
                }
            } else if (index is SuffixOperNode && index.expression is RegisterGroupNode && index.expression.isZ() && index.operator == Operator.INC) {
                compileAsmRegPair("lpm", reg, index, loc)
            } else {
                errorUnsupportedOperation(loc)
            }
        }

        is VariableNode -> {
            val variable = right.variable
            if (variable.arraySize != null || variable.dataType != DataType.BYTE) {
                errorByteVariableExpected(right)
            }
            compileAsmRegNum("lds", reg, right.address, loc)
        }

        is BinaryOperNode if right.operator != Operator.ARROW_RIGHT -> {
            compileAssignRegisterToBinaryOp(reg, right, loc)
        }

        is PrefixOperNode if right.expression is RegisterNode -> {
            when (right.operator) {
                Operator.MINUS -> {
                    val other = right.expression
                    if (reg.reg != other.reg) {
                        compileAsmRegReg("mov", reg, other, loc)
                    }
                    compileAsmReg("neg", reg, loc)
                }

                Operator.BINARY_NOT -> {
                    val other = right.expression
                    if (reg.reg != other.reg) {
                        compileAsmRegReg("mov", reg, other, loc)
                    }
                    compileAsmReg("com", reg, loc)
                }

                Operator.INC -> {
                    compileAsmReg("int", right.expression, loc)
                    compileAsmRegReg("mov", reg, right.expression, loc)
                }

                Operator.DEC -> {
                    compileAsmReg("dec", right.expression, loc)
                    compileAsmRegReg("mov", reg, right.expression, loc)
                }

                else -> {
                    errorWrongExpression(loc)
                }
            }
        }

        is PrefixOperNode if (right.expression is PrgArrayNode || right.expression is MemArrayNode || right.expression is VariableNode)
                && (right.operator == Operator.MINUS || right.operator == Operator.BINARY_NOT) -> {
            compileAssignRegisterTo(reg, right.expression, loc)
            when (right.operator) {
                Operator.MINUS -> compileAsmReg("neg", reg, loc)
                Operator.BINARY_NOT -> compileAsmReg("com", reg, loc)
                else -> errorWrongExpression(loc)
            }
        }

        is ResolvedExternVar -> {
            compileAssignFromExternVariable(reg, right)
        }

        is IdentifierNode -> {
            errorUndefinedIdentifier(right)
        }

        is PrefixOperNode if (right.operator == Operator.INC || right.operator == Operator.DEC) -> {
            if (right.expression is MemArrayNode && right.expression.index is SuffixOperNode) {
                val noSuffixRight = MemArrayNode(right.expression.getLocation(), right.expression.index.expression)
                compileAssignRegisterTo(reg, noSuffixRight, loc)
                compileRegPrefix(PrefixOperNode(loc, right.operator, reg))
                compileAssignNodeToRegister(right.expression, reg, loc)
            } else if (right.expression is MemArrayNode && right.expression.index is PrefixOperNode) {
                compileAssignRegisterTo(reg, right.expression, loc)
                compileRegPrefix(PrefixOperNode(loc, right.operator, reg))
                val noPrefixRight = MemArrayNode(right.expression.getLocation(), right.expression.index.expression)
                compileAssignNodeToRegister(noPrefixRight, reg, loc)
            } else {
                compileAssignRegisterTo(reg, right.expression, loc)
                compileRegPrefix(PrefixOperNode(loc, right.operator, reg))
                compileAssignNodeToRegister(right.expression, reg, loc)
            }
        }

        is SuffixOperNode if (right.operator == Operator.INC || right.operator == Operator.DEC) -> {
            compileAssignRegisterTo(reg, right.expression, loc)
//        if (right.expression is MemArrayNode) {
//            compileRegIncDec(reg, right.operator)
//            compileAssignOp(right.expression, reg, right.expression.getLocation())
//        } else {
            compileRegSuffix(right)
//        }
        }

        else -> {
            errorWrongExpression(loc)
        }
    }
}

private fun AvrRatCompiler.compileAssignRegisterToBinaryOp(reg: RegisterNode, binop: BinaryOperNode, loc: SourceLocation) {
    if (binop.operator == Operator.ASSIGN) {
        val left = resolveArg(binop.left)
        if (left is RegisterNode) {
            compileBinaryOp(binop)
            compileAssignRegisterTo(reg, left, loc)
            return
        }
    }
    val simplified = simplifyBinaryOp(binop)
    if (simplified is BinaryOperNode) {
        val parts = decomposeAssignOperation(simplified, reg)
        for (op in parts) {
            compileBinaryOp(op)
        }
    } else {
        compileAssignRegisterTo(reg, simplified, loc)
    }
}


fun AvrRatCompiler.compileAssignNodeToRegister(dest: Node, reg: RegisterNode, loc: SourceLocation) {
    when (dest) {
        is MemArrayNode -> {
            // X, X++, --X
            // Y, Y++, --Y, Y+q
            // Z, Z++, --Z, Z+q
            when (val index = dest.index) {
                is RegisterGroupNode, is SuffixOperNode, is PrefixOperNode -> {
                    compileAsmPairReg("st", index, reg, loc)
                }

                is BinaryOperNode -> {
                    compileAsmPairReg("std", index, reg, loc)
                }

                else -> {
                    errorUnsupportedOperation(loc)
                }
            }
        }

        is IoPortNode -> {
            if (dest.size != 1) {
                errorSizeMismatch(dest, reg)
            }
            val offset = NumberNode(dest.getLocation(), dest.address, 16)
            if (dest.address <= 0x3f) {
                compileAsmNumReg("out", offset, reg, loc)
            } else {
                compileAsmNumReg("sts", offset, reg, loc)
            }
        }

        is VariableNode -> {
            val variable = dest.variable
            if (variable.arraySize != null || variable.dataType != DataType.BYTE) {
                errorByteVariableExpected(dest)
            }
            compileAsmNumReg("sts", dest.address, reg, loc)
        }

        is ResolvedExternVar -> {
            compileAssignExternVariableTo(dest, reg)
        }

        else -> {
            errorWrongExpression(loc)
        }
    }
}

fun AvrRatCompiler.compileAssignToRegGroup(group: RegisterGroupNode, right: Node, loc: SourceLocation) {
    when (right) {
        is NumberNode -> {
            var index = group.regs.size - 1
            for (reg in group.regs) {
                val outReg = RegisterNode(group.getLocation(), reg)
                val outVal = getNumberByte(right, index--)
                if (outReg.getNumber() < 16 && outVal.value == 0) {
                    compileAsmReg("clr", outReg, loc)
                } else if (outReg.getNumber() >= 16) {
                    compileAsmRegNum("ldi", outReg, outVal, loc)
                } else {
                    errorHighRegisterExpected(outReg)
                }
            }
        }

        is VariableNode -> {
            val variable = right.variable
            when {
                (variable.arraySize != null || variable.dataType == DataType.STRUCTURED) && group.size() == 2 -> {
                    assignPairToValue(group, right.address, loc)
                }
                variable.arraySize == null && group.size() == 2 && variable.dataType == DataType.WORD -> {
                    compileAsmRegNum("lds", group.highReg(), right.address + 1, loc)
                    compileAsmRegNum("lds", group.lowReg(), right.address, loc)
                }
                variable.arraySize == null && group.size() == 4 && variable.dataType == DataType.DWORD -> {
                    compileAsmRegNum("lds", group.getReg(0), right.address + 3, loc)
                    compileAsmRegNum("lds", group.getReg(1), right.address + 2, loc)
                    compileAsmRegNum("lds", group.getReg(2), right.address + 1, loc)
                    compileAsmRegNum("lds", group.getReg(3), right.address + 0, loc)
                }
                else -> {
                    errorUnsupportedOperation(loc)
                }
            }
        }

        is IdentifierNode -> {
            val label = findLabel(right.name)
            if (label != null && group.size() == 2) {
                val labelNode = ProgramMemNode(loc, label)//, true)
                val lowNode = BinaryOperNode(loc, Operator.AND, labelNode, NumberNode(loc, 0xff))
                val highNode = BinaryOperNode(loc, Operator.SHR, labelNode, NumberNode(loc, 8))
                compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(0), highNode, loc)
                compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(1), lowNode, loc)
            } else {
                errorUndefinedIdentifier(right)
            }
        }

        is BinaryOperNode -> {
            assignBinaryOperToRegGroup(right, group, loc)
        }

        is RegisterGroupNode -> {
            assignGroups(group, right, loc)
        }

        is IoPortNode -> {
            if (group.size() != right.size) {
                errorSizeMismatch(group, right)
            }
            if (right.size != 2) {
                errorUnsupportedOperation(right)
            }

            for (index in group.size()-1 downTo 0) {
                val reg = group.getReg(index)
                val address = right.address + right.size - index - 1
                if (address <= 0x3f) {
                    compileAsmRegNum("in", reg, address, loc)
                } else {
                    compileAsmRegNum("lds", reg, address, loc)
                }
            }
        }

        is ResolvedExternVar -> {
            compileAssignFromExternVariable(group, right)
        }

        is ResolvedExternProc -> {
            compileAssignToExternProc(group, right)
        }

        is PrefixOperNode -> {
            if (right.operator.isDecOrInc()) {
                val expr = right.expression
                if (expr is RegisterGroupNode && expr.isHighPair()) {
                    val v = if (right.operator == Operator.INC) 1 else -1
                    compileAddConstToHighPair(expr, v, loc)
                    assignGroups(group, expr, loc)
                } else {
                    errorUnsupportedOperation(loc)
                }
            }
        }

        is SuffixOperNode -> {
            if (right.operator.isDecOrInc()) {
                val expr = right.expression
                if (expr is RegisterGroupNode && expr.isHighPair()) {
                    val v = if (right.operator == Operator.INC) 1 else -1
                    assignGroups(group, expr, loc)
                    compileAddConstToHighPair(expr, v, loc)
                } else {
                    errorUnsupportedOperation(loc)
                }
            }
        }

        else -> errorWrongExpression(loc)
    }
}

private fun AvrRatCompiler.assignBinaryOperToRegGroup(
    right: BinaryOperNode,
    group: RegisterGroupNode,
    loc: SourceLocation
) {
    val opLeft = resolveArg(right.left)
    val opRight = resolveArg(right.right)
    when {
        opLeft is VariableNode && opRight is NumberNode -> {
            if (opLeft.variable.arraySize == null && opLeft.variable.dataType.ramSize == group.size()) {
                compileAssignToRegGroup(group, opLeft, loc)
                when (right.operator) {
                    Operator.PLUS -> compilePlusAssignOp(group, opRight, loc, false)
                    Operator.MINUS -> compilePlusAssignOp(group, opRight, loc, true)
                    else -> errorUnsupportedOperation(loc)
                }
            } else if (opLeft.variable.arraySize != null) {
                assignPairToValue(group, opLeft.address + opRight.value, loc)
            } else {
                errorUnsupportedOperation(loc)
            }
        }

        opRight is VariableNode && opLeft is NumberNode -> {
            if (opRight.variable.arraySize == null) {
                errorUnsupportedOperation(loc)
            }
            assignPairToValue(group, opRight.address + opLeft.value, loc)
        }

        opLeft is IdentifierNode && opRight is NumberNode -> {// && right.operator == Operator.DIV) {
            val label = findLabel(opLeft.name)
            if (label != null && group.size() == 2) {
                val labelNode = ProgramMemNode(loc, label)
                val rightNode = BinaryOperNode(right.getLocation(), right.operator, labelNode, opRight)
                val lowNode =
                    BinaryOperNode(rightNode.getLocation(), Operator.AND, rightNode, NumberNode(loc, 0xff))
                val highNode =
                    BinaryOperNode(rightNode.getLocation(), Operator.SHR, rightNode, NumberNode(loc, 8))
                compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(0), highNode, loc)
                compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(1), lowNode, loc)
                return
            } else {
                errorUnsupportedOperation(loc)
            }
        }

        opLeft is ResolvedExternVar && opLeft.dataType == DataType.PTR && group.size() == 2 && opRight is NumberNode -> {
            val newName = when (right.operator) {
                Operator.PLUS -> "${opLeft.name}+${opRight.value}"
                Operator.MINUS -> "${opLeft.name}-${opRight.value}"
                else -> errorWrongExpression(right)
            }
            compileAsmRegDeferredNum(
                AvrCmd.LDI,
                group.buildRegisterNode(0),
                opLeft.modifyName("hi8($newName)"),
                loc
            )
            compileAsmRegDeferredNum(
                AvrCmd.LDI,
                group.buildRegisterNode(1),
                opLeft.modifyName("lo8($newName)"),
                loc
            )
        }

        right.operator == Operator.PLUS -> {
            compileAssignToRegGroup(group, opLeft, opLeft.getLocation())
            compilePlusAssignOp(group, opRight, opRight.getLocation(), false)
        }

        right.operator == Operator.MINUS -> {
            compileAssignToRegGroup(group, opLeft, right.getLocation())
            compilePlusAssignOp(group, opRight, opRight.getLocation(), true)
        }

        else -> {
            errorUnsupportedOperation(loc)
        }
    }
}

private fun AvrRatCompiler.compileAssignVariableTo(variable: VariableNode, right: Node) {
    if (variable.variable.arraySize != null) {
        errorWrongExpression(variable)
    }
    val dt = variable.variable.dataType
    val loc = variable.getLocation()
    when {
        dt == DataType.BYTE && right is RegisterNode -> {
            compileAsmNumReg("sts", variable.address, right, loc)
        }

        dt == DataType.WORD && right is RegisterGroupNode && right.size() == 2 -> {
            compileAsmNumReg("sts", variable.address + 1, right.highReg(), loc)
            compileAsmNumReg("sts", variable.address, right.lowReg(), loc)
        }

        dt == DataType.DWORD && right is RegisterGroupNode && right.size() == 4 -> {
            compileAsmNumReg("sts", variable.address + 3, right.getReg(0), loc)
            compileAsmNumReg("sts", variable.address + 2, right.getReg(1), loc)
            compileAsmNumReg("sts", variable.address + 1, right.getReg(2), loc)
            compileAsmNumReg("sts", variable.address + 0, right.getReg(3), loc)
        }

        dt == DataType.QWORD && right is RegisterGroupNode && right.size() == 8 -> {
            compileAsmNumReg("sts", variable.address + 7, right.getReg(0), loc)
            compileAsmNumReg("sts", variable.address + 6, right.getReg(1), loc)
            compileAsmNumReg("sts", variable.address + 5, right.getReg(2), loc)
            compileAsmNumReg("sts", variable.address + 4, right.getReg(3), loc)
            compileAsmNumReg("sts", variable.address + 3, right.getReg(4), loc)
            compileAsmNumReg("sts", variable.address + 2, right.getReg(5), loc)
            compileAsmNumReg("sts", variable.address + 1, right.getReg(6), loc)
            compileAsmNumReg("sts", variable.address + 0, right.getReg(7), loc)
        }

        right is NumberNode || right is IoPortNode || right is IoPortBitNode -> {
            errorDirectMemeWrite(variable, right)
        }

        right is PrefixOperNode && right.expression is RegisterNode -> {
            compileRegPrefix(right)
            compileAsmNumReg("sts", variable.address, right.expression, loc)
        }

        right is SuffixOperNode && right.expression is RegisterNode -> {
            compileAsmNumReg("sts", variable.address, right.expression, loc)
            compileRegSuffix(right)
        }

        dt == DataType.BYTE && right is BinaryOperNode && right.left is RegisterNode && right.operator.isSelfAssign() -> {
            compileBinaryOp(right)
            compileAsmNumReg("sts", variable.address, right.left, loc)
        }

        else -> {
            errorWrongExpression(loc)
        }
    }
}

private fun AvrRatCompiler.compileRegPrefix(node: PrefixOperNode) {
    if (node.expression !is RegisterNode) {
        errorWrongExpression(node)
    }
    compileRegIncDec(node.expression, node.operator)
}

private fun AvrRatCompiler.compileRegSuffix(node: SuffixOperNode) {
    if (node.expression !is RegisterNode) {
        errorWrongExpression(node)
    }
    compileRegIncDec(node.expression, node.operator)
}

fun AvrRatCompiler.compileRegIncDec(reg: RegisterNode, op: Operator) {
    when (op) {
        Operator.INC -> {
            compileAsmReg("inc", reg, reg.getLocation())
        }

        Operator.DEC -> {
            compileAsmReg("dec", reg, reg.getLocation())
        }

        else -> errorWrongExpression(reg)
    }
}

fun AvrRatCompiler.compileAssignExternVariableTo(v: ResolvedExternVar, right: Node) {
    val dt = v.dataType
    val loc = v.getLocation()
    when {
        (dt == DataType.BYTE || dt == DataType.PTR) && right is RegisterNode -> {
            compileAsmExtReg("sts", v, right, loc)
        }

        dt == DataType.BYTE && right is PrefixOperNode && right.expression is RegisterNode -> {
            compileRegPrefix(right)
            compileAsmExtReg("sts", v, right.expression, loc)
        }

        dt == DataType.BYTE && right is SuffixOperNode && right.expression is RegisterNode -> {
            compileAsmExtReg("sts", v, right.expression, loc)
            compileRegSuffix(right)
        }

        dt == DataType.WORD && right is RegisterGroupNode && right.size() == 2 -> {
            compileAsmExtReg("sts", v.modifyName(1), right.highReg(), loc)
            compileAsmExtReg("sts", v, right.lowReg(), loc)
        }

        dt == DataType.DWORD && right is RegisterGroupNode && right.size() == 4 -> {
            compileAsmExtReg("sts", v.modifyName(3), right.getReg(3), loc)
            compileAsmExtReg("sts", v.modifyName(2), right.getReg(2), loc)
            compileAsmExtReg("sts", v.modifyName(1), right.getReg(1), loc)
            compileAsmExtReg("sts", v, right.getReg(0), loc)
        }

        dt == DataType.QWORD && right is RegisterGroupNode && right.size() == 8 -> {
            compileAsmExtReg("sts", v.modifyName(7), right.getReg(7), loc)
            compileAsmExtReg("sts", v.modifyName(6), right.getReg(6), loc)
            compileAsmExtReg("sts", v.modifyName(5), right.getReg(5), loc)
            compileAsmExtReg("sts", v.modifyName(4), right.getReg(4), loc)
            compileAsmExtReg("sts", v.modifyName(3), right.getReg(3), loc)
            compileAsmExtReg("sts", v.modifyName(2), right.getReg(2), loc)
            compileAsmExtReg("sts", v.modifyName(1), right.getReg(1), loc)
            compileAsmExtReg("sts", v, right.getReg(0), loc)
        }

        right is NumberNode || right is IoPortNode || right is IoPortBitNode -> {
            errorWrongExpression(right)
        }

        else -> {
            errorWrongExpression(loc)
        }
    }
}

fun AvrRatCompiler.compileAssignFromExternVariable(dest: Node, v: ResolvedExternVar) {
    val dt = v.dataType
    val loc = v.getLocation()
    when {
        (dt == DataType.BYTE || dt == DataType.PTR) && dest is RegisterNode -> {
            compileAsmRegExt("lds", dest, v, loc)
        }

        dt == DataType.WORD && dest is RegisterGroupNode && dest.size() == 2 -> {
            compileAsmRegExt("lds", dest.highReg(), v.modifyName(1), loc)
            compileAsmRegExt("lds", dest.lowReg(), v, loc)
        }

        dt == DataType.DWORD && dest is RegisterGroupNode && dest.size() == 4 -> {
            compileAsmRegExt("lds", dest.getReg(3), v.modifyName(3), loc)
            compileAsmRegExt("lds", dest.getReg(2), v.modifyName(2), loc)
            compileAsmRegExt("lds", dest.getReg(1), v.modifyName(1), loc)
            compileAsmRegExt("lds", dest.getReg(0), v, loc)
        }

        dt == DataType.QWORD && dest is RegisterGroupNode && dest.size() == 8 -> {
            compileAsmRegExt("lds", dest.getReg(7), v.modifyName(7), loc)
            compileAsmRegExt("lds", dest.getReg(6), v.modifyName(6), loc)
            compileAsmRegExt("lds", dest.getReg(5), v.modifyName(5), loc)
            compileAsmRegExt("lds", dest.getReg(4), v.modifyName(4), loc)
            compileAsmRegExt("lds", dest.getReg(3), v.modifyName(3), loc)
            compileAsmRegExt("lds", dest.getReg(2), v.modifyName(2), loc)
            compileAsmRegExt("lds", dest.getReg(1), v.modifyName(1), loc)
            compileAsmRegExt("lds", dest.getReg(0), v, loc)
        }

        (dt == DataType.PTR || dt == DataType.STRUCTURED) && dest is RegisterGroupNode && dest.size() == 2 -> {
            compileAsmRegExt("ldi", dest.highReg(), v.modifyName("hi8(${v.name})"), loc)
            compileAsmRegExt("ldi", dest.lowReg(), v.modifyName("lo8(${v.name})"), loc)
        }

        else -> {
            errorWrongExpression(loc)
        }
    }
}

fun AvrRatCompiler.compileAssignToExternProc(dest: Node, proc: ResolvedExternProc) {
    if (dest is RegisterGroupNode && dest.size() == 2) {
        compileAsmRegDeferredNum(
            AvrCmd.LDI,
            dest.buildRegisterNode(0),
            proc.modifyName("hi8(${proc.name})"),
            proc.getLocation()
        )
        compileAsmRegDeferredNum(
            AvrCmd.LDI,
            dest.buildRegisterNode(1),
            proc.modifyName("lo8(${proc.name})"),
            proc.getLocation()
        )
    } else {
        errorUnsupportedOperation(dest)
    }
}


private fun AvrRatCompiler.assignPairToValue(group: RegisterGroupNode, addr: Int, loc: SourceLocation) {
    if (group.size() != 2) {
        errorUnsupportedOperation(loc)
    }
    val high = NumberNode(loc, addr shr 8 and 0xff)
    val low = NumberNode(loc, addr and 0xff)
    compileAsmRegNum("ldi", group.buildRegisterNode(0), high, loc)
    compileAsmRegNum("ldi", group.buildRegisterNode(1), low, loc)
}


fun AvrRatCompiler.compilePlusAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    val loc = binop.getLocation()
    val minus = binop.operator == Operator.MINUS_ASSIGN
    compilePlusAssignOp(left, right, loc, minus)
}

fun AvrRatCompiler.compilePlusAssignOp(left: Node, right: Node, loc: SourceLocation, minus: Boolean) {
    when (left) {
        is RegisterNode -> compileRegPlusAssignOp(left, right, minus, loc)
        is RegisterGroupNode -> compileRegGroupPlusAssignOp(left, right, minus, loc)
        else -> errorWrongExpression(loc)
    }
}

private fun AvrRatCompiler.compileRegPlusAssignOp(dest: RegisterNode, right: Node, minus: Boolean, loc: SourceLocation) {
    when (right) {
        is NumberNode -> {
            val num = right.value
            val signed = if (minus) -num else num
            when {
                num == 0 -> warning("zero argument", right)
                signed == 1 -> compileAsmReg("inc", dest, loc)
                signed == -1 -> compileAsmReg("dec", dest, loc)
                else -> {
                    if (minus) {
                        compileAsmRegNum("subi", dest, num, loc)
                    } else {
                        compileAsmRegNum("subi", dest, 0x100 - num, loc)
                    }
                }
            }
        }

        is RegisterNode -> {
            if (minus) {
                compileAsmRegReg("sub", dest, right, loc)
            } else {
                compileAsmRegReg("add", dest, right, loc)
            }
        }

        is VariableNode -> {
            if (right.variable.arraySize != null) {
                errorWrongExpression(loc)
            } else if (right.variable.dataType != DataType.BYTE) {
                errorSizeMismatch(dest, right)
            }
            errorUnsupportedOperation(loc)
        }

        is ResolvedExternVar -> {
            if (right.size != null) {
                errorWrongExpression(loc)
            } else if (right.dataType != DataType.BYTE) {
                errorSizeMismatch(dest, right)
            }
            compileAsmRegExt("lds", dest, right, loc)
        }

        is BinaryOperNode -> {
            val simplified = simplifyBinaryOp(right)
            if (simplified is BinaryOperNode) {
                when (simplified.operator) {
                    Operator.PLUS -> {
                        compilePlusAssignOp(dest, simplified.left, simplified.getLocation(), minus)
                        compilePlusAssignOp(dest, simplified.right, simplified.getLocation(), minus)
                    }

                    Operator.MINUS -> {
                        compilePlusAssignOp(dest, simplified.left, simplified.getLocation(), minus)
                        compilePlusAssignOp(dest, simplified.right, simplified.getLocation(), !minus)
                    }

                    else -> errorWrongExpression(simplified)
                }
            }
        }

        else -> {
            errorWrongExpression(loc)
        }
    }
}

fun AvrRatCompiler.compileRegGroupPlusAssignOp(
    dest: RegisterGroupNode,
    right: Node,
    minus: Boolean,
    loc: SourceLocation
) {
    when (right) {
        is NumberNode -> {
            val num = right.value
            val signedNum = if (minus) -num else num

            // ADIW Rd+1 : Rd, K	d ∈ {24, 26, 28, 30}, 0 ≤ K ≤ 63
            // SBIW Rd+1 : Rd, K	d ∈ {24, 26, 28, 30}, 0 ≤ K ≤ 63
            if (dest.isHighPair() && signedNum >= -63 && signedNum <= 63) {
                compileAddConstToHighPair(dest, signedNum, loc)
            } else {
                val numArg = if (minus) -signedNum else ((1L shl (dest.size() * 8)) - signedNum).toInt()
                if (minus) {
                    compileAsmRegNum("subi", dest.lowReg(), numArg and 0xff, loc)
                } else {
                    compileAsmRegNum("subi", dest.lowReg(), 0x100 - (num and 0xff), loc)
                }
                for (i in dest.size() - 2 downTo 0) {
                    val byteNum = dest.size() - i - 1
                    val byte = (numArg shr (8 * byteNum))
                    compileAsmRegNum("sbci", dest.getReg(i), byte and 0xff, loc)
                }
            }
        }

        is ResolvedExternVar -> {
            if ((right.dataType == DataType.PTR || right.dataType == DataType.STRUCTURED) && dest.size() == 2) {
                val name = if (minus) right.name else "-(${right.name})"
                compileAsmRegDeferredNum(AvrCmd.SUBI, dest.buildRegisterNode(1), right.modifyName("lo8(${name})"), loc)
                compileAsmRegDeferredNum(AvrCmd.SBCI, dest.buildRegisterNode(0), right.modifyName("hi8(${name})"), loc)
            } else {
                errorWrongExpression(loc)
            }
        }
        is BinaryOperNode -> {
            if (right.operator == Operator.DIV && right.right is NumberNode && right.right.value == 2 && right.left is IdentifierNode) {
                val label = findLabel(right.left.name)
                if (label != null && dest.size() == 2) {
                    val labelNode = ProgramMemNode(loc, label)
                    val div2 =
                        BinaryOperNode(right.getLocation(), Operator.DIV, labelNode, NumberNode(right.getLocation(), 2))
                    if (minus) {
                        val lowNode = BinaryOperNode(dest.getLocation(), Operator.AND, div2, NumberNode(loc, 0xff))
                        val highNode = BinaryOperNode(dest.getLocation(), Operator.SHR, div2, NumberNode(loc, 8))
                        compileAsmRegDeferredNum(AvrCmd.SUBI, dest.buildRegisterNode(1), lowNode, loc)
                        compileAsmRegDeferredNum(AvrCmd.SBCI, dest.buildRegisterNode(0), highNode, loc)
                    } else {
                        val addrNode =
                            BinaryOperNode(dest.getLocation(), Operator.MINUS, NumberNode(loc, 0x20000), div2)
                        val low = BinaryOperNode(dest.getLocation(), Operator.AND, addrNode, NumberNode(loc, 0xff))
                        val high = BinaryOperNode(dest.getLocation(), Operator.SHR, addrNode, NumberNode(loc, 8))

                        compileAsmRegDeferredNum(AvrCmd.SUBI, dest.buildRegisterNode(1), low, loc)
                        compileAsmRegDeferredNum(AvrCmd.SBCI, dest.buildRegisterNode(0), high, loc)
                    }
                } else {
                    errorWrongExpression(loc)
                }
            } else if (right.operator == Operator.PLUS || right.operator == Operator.MINUS) {
                compileRegGroupPlusAssignOp(dest, resolveArg(right.left), minus, loc)
                compileRegGroupPlusAssignOp(dest, resolveArg(right.right), minus.xor(right.operator == Operator.MINUS), loc)
            } else {
                errorWrongExpression(loc)
            }
        }

        is RegisterGroupNode -> {
            if (dest.size() != right.size()) {
                errorSizeMismatch(dest, right)
            }
            val size = dest.size()
            if (minus) {
                for (i in size - 1 downTo 0) {
                    val cmd = if (i == size - 1) "sub" else "sbc"
                    compileAsmRegReg(cmd, dest.getReg(i), right.getReg(i), loc)
                }
            } else {
                for (i in size - 1 downTo 0) {
                    val cmd = if (i == size - 1) "add" else "adc"
                    compileAsmRegReg(cmd, dest.getReg(i), right.getReg(i), loc)
                }
            }
        }

        is VariableNode -> {
            if (dest.size() == 2 && (right.variable.arraySize != null || right.variable.dataType == DataType.STRUCTURED)) {
                val arg = 0x10000 - right.address
                compileAsmRegNum("subi", dest.lowReg(), arg and 0xff, loc)
                compileAsmRegNum("sbci", dest.highReg(), (arg shr 8) and 0xff, loc)
            } else {
                errorWrongExpression(loc)
            }
        }

        is IdentifierNode -> {
            val label = findLabel(right.name)

            if (label != null && dest.size() == 2) {
                val labelNode = ProgramMemNode(loc, label)//, true)
                if (minus) {
                    val lowNode = BinaryOperNode(dest.getLocation(), Operator.AND, labelNode, NumberNode(loc, 0xff))
                    val highNode = BinaryOperNode(dest.getLocation(), Operator.SHR, labelNode, NumberNode(loc, 8))

                    compileAsmRegDeferredNum(AvrCmd.SUBI, dest.buildRegisterNode(1), lowNode, loc)
                    compileAsmRegDeferredNum(AvrCmd.SBCI, dest.buildRegisterNode(0), highNode, loc)
                } else {
                    //            (0x10000 - 0x0CE8) & 0xff = 0x18
                    //            (0x10000 - 0x0CE8) >> 8 =   0xF3
                    val addrNode =
                        BinaryOperNode(dest.getLocation(), Operator.MINUS, NumberNode(loc, 0x10000), labelNode)
                    val low = BinaryOperNode(dest.getLocation(), Operator.AND, addrNode, NumberNode(loc, 0xff))
                    val high = BinaryOperNode(dest.getLocation(), Operator.SHR, addrNode, NumberNode(loc, 8))

                    compileAsmRegDeferredNum(AvrCmd.SUBI, dest.buildRegisterNode(1), low, loc)
                    compileAsmRegDeferredNum(AvrCmd.SBCI, dest.buildRegisterNode(0), high, loc)
                }
            } else {
                errorUndefinedIdentifier(right)
            }
        }

        is RegisterNode -> {
            errorAddRegisterToGroup(dest, right)
        }

        is ResolvedExternProc -> {
//            r[4] shouldBe "subi R30, lo8(-(ext_p))"
//            r[5] shouldBe "sbci R31, hi8(-(ext_p))"
            if (dest.size() == 2) {
                val name = if (minus) right.name else "-(${right.name})"
                compileAsmRegDeferredNum(AvrCmd.SUBI, dest.buildRegisterNode(1), right.modifyName("lo8(${name})"), loc)
                compileAsmRegDeferredNum(AvrCmd.SBCI, dest.buildRegisterNode(0), right.modifyName("hi8(${name})"), loc)
            } else {
                errorWrongExpression(loc)
            }
        }
        else -> errorWrongExpression(loc)
    }
}

private fun AvrRatCompiler.compileAddConstToHighPair(dest: RegisterGroupNode, signedNum: Int, loc: SourceLocation) {
    if (signedNum < 0) {
        val cmdNode = AssemblerInstrNode(loc, "sbiw", listOf(dest, NumberNode(loc, -signedNum)))
        compileAssembler(cmdNode)
    } else {
        val cmdNode = AssemblerInstrNode(loc, "adiw", listOf(dest, NumberNode(loc, signedNum)))
        compileAssembler(cmdNode)
    }
}


fun AvrRatCompiler.compileOrAndAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    val op = binop.operator
    var wrapError = true
    if (left is RegisterNode) {
        if (right is RegisterNode) {
            when (op) {
                Operator.OR_ASSIGN -> compileAsmRegReg("or", left, right, binop.getLocation())
                Operator.AND_ASSIGN -> compileAsmRegReg("and", left, right, binop.getLocation())
                else -> internalError()
            }
        } else {
            try {
                val constRight = calculateConst(right)
                val radix = if (right is NumberNode) right.radix else 10
                val arg = if (constRight < 0) constRight and 0xff else constRight
                when (op) {
                    Operator.OR_ASSIGN ->
                        if (left.getNumber() >= 16) {
                            compileAsmRegNum("ori", left, arg, binop.getLocation(), null, radix)
                        } else {
                            wrapError = false
                            errorHighRegisterExpected(left)
                        }

                    Operator.AND_ASSIGN ->
                        if (left.getNumber() >= 16) {
                            compileAsmRegNum("andi", left, arg, binop.getLocation(), null, radix)
                        } else {
                            wrapError = false
                            errorHighRegisterExpected(left)
                        }

                    else -> internalError()
                }
                return
            } catch (e: CompilerException) {
                if (!wrapError) {
                    throw e
                }
                errorWrongExpression(binop)
            }
        }
    } else {
        errorWrongExpression(binop)
    }
}

fun AvrRatCompiler.compileAssignIoBitToRegBit(dstIoBit: IoPortBitNode, srcReg: RegisterBitNode) {
    if (dstIoBit.port.name == "SREG" && dstIoBit.bitName == "T") {
        compileAsmRegNum("bst", srcReg.reg, srcReg.bit, srcReg.getLocation())
    } else if (!dstIoBit.port.isLowerPort()) {
        errorLowIoExpected(dstIoBit)
    } else {
        compileAsmRegNum("sbrs", srcReg.reg, srcReg.bit, srcReg.getLocation())
        compileAsmLowerIoBit("cbi", dstIoBit)
        compileAsmRegNum("sbrc", srcReg.reg, srcReg.bit, srcReg.getLocation())
        compileAsmLowerIoBit("sbi", dstIoBit)
    }
}

fun AvrRatCompiler.compileAssignIoBitToInvertedRegBit(dstIoBit: IoPortBitNode, srcReg: RegisterBitNode) {
    if (!dstIoBit.port.isLowerPort()) {
        errorLowIoExpected(dstIoBit)
    }
    compileAsmRegNum("sbrc", srcReg.reg, srcReg.bit, srcReg.getLocation())
    compileAsmLowerIoBit("cbi", dstIoBit)
    compileAsmRegNum("sbrs", srcReg.reg, srcReg.bit, srcReg.getLocation())
    compileAsmLowerIoBit("sbi", dstIoBit)
}

fun AvrRatCompiler.compileAssignSregBitFromLowerIoBit(sregBit: Int, right: IoPortBitNode) {
    compileAsmNum("bclr", sregBit, right.getLocation())
    compileAsmLowerIoBit("sbic", right)
    compileAsmNum("bset", sregBit, right.getLocation())
}

fun AvrRatCompiler.compileAssignSregBitFromInvertedLowerIoBit(sregBit: Int, right: IoPortBitNode) {
    compileAsmNum("bclr", sregBit, right.getLocation())
    compileAsmLowerIoBit("sbis", right)
    compileAsmNum("bset", sregBit, right.getLocation())
}

fun AvrRatCompiler.compileXorAssignOp(binop: BinaryOperNode) {
    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    if (left is RegisterNode && right is RegisterNode) {
        compileAsmRegReg("eor", left, right, binop.getLocation())
    } else {
        errorWrongExpression(binop)
    }
}

fun AvrRatCompiler.compileShiftAssignOp(binop: BinaryOperNode) {
    // Сдвиг влево  r25.r24.r23: lsl r23, rol r24, rol r25
    // Сдвиг вправо r25.r24.r23: lsr r25, ror r24, ror r23

    val left = resolveArg(binop.left)
    val right = resolveArg(binop.right)
    val isLeftShift = binop.operator == Operator.SHL_ASSIGN
    val cmdFirstShift = if (isLeftShift) "lsl" else "lsr"
    if (left is RegisterNode && right is NumberNode) {
        val num = right.value
        if (num <= 0 || num >= 8) {
            errorInvalidArgument(right)
        }
        for (n in 1..num) {
            compileAsmReg(cmdFirstShift, left, binop.getLocation())
        }
    } else if (left is RegisterGroupNode && right is NumberNode) {
        val num = right.value
        val size = left.size()
        if (num <= 0 || num >= 8 * size) {
            errorInvalidArgument(right)
        }
        for (n in 1..num) {
            if (isLeftShift) {
                for (ri in size - 1 downTo 0) {
                    val cmd = if (ri == size - 1) cmdFirstShift else "rol"
                    compileAsmReg(cmd, left.getReg(ri), binop.getLocation())
                }
            } else {
                for (ri in 0..< size) {
                    val cmd = if (ri == 0) cmdFirstShift else "ror"
                    compileAsmReg(cmd, left.getReg(ri), binop.getLocation())
                }
            }
        }
    } else {
        errorWrongExpression(binop)
    }
}


internal fun decomposeAssignRegs(
    left: RegisterGroupNode,
    right: RegisterGroupNode
): List<Pair<RegisterNode, RegisterNode>> {
    val pairs = mutableListOf<Pair<RegisterNode, RegisterNode>>()
    for (i in 0 until left.size()) {
        val l = left.getReg(i)
        val r = right.getReg(i)
        if (l.reg != r.reg) {
            pairs.add(Pair(l, r))
        }
    }
    return pairs.sortedWith(compareBy { it.first.getNumber() })
}

private fun AvrRatCompiler.assignGroups(left: RegisterGroupNode, right: RegisterGroupNode, loc: SourceLocation) {
    if (left.size() != right.size()) {
        errorSizeMismatch(left, right)
    }
    val sorted = decomposeAssignRegs(left, right)
    var skipNext = false
    for (i in sorted.indices) {
        if (skipNext) {
            skipNext = false
            continue
        }
        val pair = sorted[i]
        val dest = pair.first
        val src = pair.second
        if (i + 1 < sorted.size && src.getNumber() % 2 == 0 && dest.getNumber() % 2 == 0) {
            val nextPair = sorted[i + 1]
            val nextDest = nextPair.first
            val nextSrc = nextPair.second
            if (nextDest.getNumber() == dest.getNumber() + 1 && nextSrc.getNumber() == src.getNumber() + 1) {
                val arg1 = ArgRegPair.getPairWithHighReg(nextDest.getNumber())
                val arg2 = ArgRegPair.getPairWithHighReg(nextSrc.getNumber())
                compileAssembler(loc, AvrCmd.MOVW, arg1, arg2)
                skipNext = true
                continue
            }

        }
        if (dest.reg != src.reg) {
            compileAsmRegReg("mov", dest, src, loc)
        }
    }
}

private fun getNumberByte(num: NumberNode, byteNum: Int): NumberNode {
    val res = num.value shr (8 * byteNum)
    return NumberNode(num.getLocation(), res and 0xff, num.radix)
}


fun AvrRatCompiler.compileArrowLeftOp(binop: BinaryOperNode) {
    val dest = resolveArg(binop.left)
    val src = resolveArg(binop.right)
    when (dest) {
        is RegisterBitNode if src is RegisterBitNode -> {
            compileAsmRegNum("bst", src.reg, src.bit, src.getLocation())
            compileAsmRegNum("bld", dest.reg, dest.bit, src.getLocation())
        }

        is RegisterBitNode if src is BinaryOperNode -> {
            compileArrowLeftOp(src)
            compileAsmRegNum("bld", dest.reg, dest.bit, src.getLocation())
        }

        is RegisterBitNode if src is NumberNode -> {
            when (src.value) {
                0 -> {
                    compileAsmNum("bclr", 6, src.getLocation())
                    compileAsmRegNum("bld", dest.reg, dest.bit, src.getLocation())
                }

                1 -> {
                    compileAsmNum("bset", 6, src.getLocation())
                    compileAsmRegNum("bld", dest.reg, dest.bit, src.getLocation())
                }

                else -> errorWrongBitValue(src)
            }
        }

        else -> {
            errorUnsupportedOperation(binop)
        }
    }
}

private fun AvrRatCompiler.compileArrowRightOp(binop: BinaryOperNode) {
    if (binop.left is FunctionCallNode) {
        val funcCall = binop.left
        val proc = findProc(funcCall)
        if (proc.results.isNotEmpty()) {
            val loc = binop.getLocation()
            val dest = binop.right
            
            // Handle named destinations (CallArgumentNode or CompoundStmt of CallArgumentNode)
            val namedDests = collectNamedDestinations(dest)
            
            if (namedDests.isNotEmpty()) {
                // Named destinations: map by name
                for (callArg in namedDests) {
                    val destName = callArg.name ?: errorWrongArgument(callArg)
                    val procResult = proc.results.find { it.name == destName }
                        ?: errorUnknownResultName(destName, callArg.getLocation())
                    // Use compileAssignOp to copy: dest = result
                    compileAssignOp(callArg.expr, procResult.expr, loc)
                }
            } else if (proc.results.size == 1) {
                // Single result
                val result = proc.results[0]
                compileAssignOp(dest, result.expr, loc)
            } else {
                // Multiple results with positional destination
                val destResolved = resolveArg(dest)
                if (destResolved is RegisterGroupNode) {
                    for ((i, result) in proc.results.withIndex()) {
                        if (i < destResolved.size()) {
                            compileAssignOp(destResolved.getReg(i), result.expr, loc)
                        }
                    }
                }
            }
        }
    } else {
        errorUnsupportedOperation(binop)
    }
}

private fun collectNamedDestinations(dest: Node): List<CallArgumentNode> {
    return when (dest) {
        is CallArgumentNode -> listOf(dest)
        is CompoundStmt -> dest.nodes.filterIsInstance<CallArgumentNode>()
        else -> emptyList()
    }
}

private fun errorUnknownResultName(name: String, location: SourceLocation): Nothing {
    throw CompilerException("Unknown result name: $name", location)
}


fun errorByteVariableExpected(node: VariableNode): Nothing {
    throw CompilerException("Byte variable expected: ${node.variable.name}", node.getLocation())
}

private fun errorWrongDividerExpected2(divider: NumberNode): Nothing {
    throw CompilerException("Unsupported divider, '2' expected: ${divider.value}", divider.getLocation())
}
