package ru.trolsoft.therat.avr.compile

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.compiler.errorUndefinedIdentifier
import ru.trolsoft.compiler.compiler.errorWrongExpression
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.arch.avr.ArgRegPair
import ru.trolsoft.therat.arch.avr.AvrCmd
import ru.trolsoft.therat.avr.*

internal fun compileBinaryOp(compiler: AvrRatCompiler, binop: BinaryOperNode) {
    when (binop.operator) {
        Operator.ASSIGN -> compileAssignOp(compiler, binop)
        Operator.PLUS_ASSIGN -> compilePlusAssignOp(compiler, binop)
        Operator.MINUS_ASSIGN -> compilePlusAssignOp(compiler, binop)
        Operator.OR_ASSIGN -> compileOrAndAssignOp(compiler, binop)
        Operator.XOR_ASSIGN -> compileXorAssignOp(compiler, binop)
        Operator.AND_ASSIGN -> compileOrAndAssignOp(compiler, binop)
        Operator.SHL_ASSIGN -> compileShiftAssignOp(compiler, binop)
        Operator.SHR_ASSIGN -> compileShiftAssignOp(compiler, binop)

        else -> TODO("BINOP: $binop ${binop.getLocation()}\n")
    }

}


internal fun compileAssignOp(compiler: AvrRatCompiler, assign: BinaryOperNode) {
    compileAssignOp(compiler, assign.left, assign.right, assign.getLocation())
}

internal fun compileAssignOp(compiler: AvrRatCompiler, leftArg: Node, rightArg: Node, loc: SourceLocation) {
    val left = compiler.resolveArg(leftArg)
    val right = compiler.resolveArg(rightArg)
    if (left is RegisterNode) {
        compileAssignRegisterTo(compiler, left, right, loc)
    } else if (right is RegisterNode) {
        compileAssignRegisterToNode(compiler, left, right, loc)
    } else if (left is RegisterGroupNode) {
        compileAssignRegGroupTo(compiler, left, right, loc)
    } else if (right is BinaryOperNode && right.operator == Operator.ASSIGN) {
        compileAssignOp(compiler, right)
        compileAssignOp(compiler, left, right.left, loc)
    } else if (left is VariableNode) {
        compileAssignVariableTo(compiler, left, right)
    } else if (left is ResolvedExternVar) {
        compileAssignExternVariableTo(compiler, left, right)
    } else if (right is ResolvedExternProc) {
        compileAssignToExternProc(compiler, left, right)
    } else if (left is IoPortBitNode && right is NumberNode) {
        val value = compiler.getBitValue(right)
        if (left.port.name == "SREG") {
            val mnemonic = if (value == 1) "bset" else "bclr"
            val asmArgs = listOf(
                    NumberNode(loc, left.bitIndex, 2)
            )
            val cmdNode = AssemblerInstrNode(loc, mnemonic, asmArgs)
            compiler.compileAssembler(cmdNode)
        } else {
            val mnemonic = if (value == 1) "sbi" else "cbi"
            compiler.compileAsmLowerIoBit(mnemonic, left)
        }
    } else if (left is IoPortNode && right is RegisterGroupNode) {
        val ioSize = left.size
        if (ioSize != right.size()) {
            errorSizeMismatch(left.getLocation(), ioSize, right.size())
        }
        val portName = left.name
        compileAssignOp(compiler,
                IdentifierNode(left.getLocation(), portName + "H"),
                RegisterNode(right.getLocation(), right.regs[0]), loc)
        compileAssignOp(compiler,
                IdentifierNode(left.getLocation(), portName + "L"),
                RegisterNode(right.getLocation(), right.regs[1]), loc)
    } else if (left is RegisterBitNode && right is NumberNode) {
        when (right.value) {
            0 -> compiler.compileAsmRegNum("cbr", left.reg, 1 shl left.bit, loc)
            1 -> compiler.compileAsmRegNum("sbr", left.reg, 1 shl left.bit, loc)
            else -> internalError()
        }
    } else if (left is IoPortBitNode && right is RegisterBitNode) {
        compileAssignIoBitToRegBit(compiler, left, right)
    } else if (left is IoPortBitNode && right is PrefixOperNode && right.operator == Operator.LOGICAL_NOT && right.expression is RegisterBitNode) {
        compileAssignIoBitToInvertedRegBit(compiler, left, right.expression)
    } else if (left is IdentifierNode) {
        errorUndefinedIdentifier(left)
    } else if (right is IdentifierNode) {
        errorUndefinedIdentifier(right)
    } else {
        TODO("${left.javaClass} ${right.javaClass} ; $left $right")
    }
//    when (left) {
//        is BinaryOperNode -> {
//            if (left is BinaryOperNode && left.operator == Operator.ARROW_RIGHT) {
//
//            }
//            when (left.operator) {
//                Operator.ARROW_RIGHT -> left = resolveArrowRight(left)
//            }
//        }
//    }
}



fun compileAssignRegisterTo(compiler: AvrRatCompiler, reg: RegisterNode, right: Node, loc: SourceLocation) {
    if (right is NumberNode) {
        if (right.value == 0) {
            compiler.compileAsmReg("clr", reg, loc)
        } else {
            compiler.compileAsmRegNum("ldi", reg, right, loc)
        }
    } else if (right is RegisterNode) {
        if (reg.reg != right.reg) {
            compiler.compileAsmRegReg("mov", reg, right, loc)
        }
    } else if (right is IoPortNode) {
        // TODO !!! check sizes
        val offset = NumberNode(right.getLocation(), right.address, 16)
        if (right.address <= 0x3f) {
            compiler.compileAsmRegNum("in", reg, offset, loc)
        } else {
            compiler.compileAsmRegNum("lds", reg, offset, loc)
        }
    } else if (right is MemArrayNode) {
        // X, X++, --X
        // Y, Y++, --Y, Y+q
        // Z, Z++, --Z, Z+q
        val index = right.index
        if (index is RegisterGroupNode || index is SuffixOperNode || index is PrefixOperNode) {
            compiler.compileAsmRegPair("ld", reg, index, loc)
        } else if (index is BinaryOperNode) {
            compiler.compileAsmRegPair("ldd", reg, index, loc)
        } else {
            errorUnsupportedOperation(loc)
        }
    } else if (right is PrgArrayNode) {
        // LPM Z
        // LPM Rd, Z
        // LPM Rd, Z+
        val index = right.index
        if (index is RegisterGroupNode && index.isZ()) {
            if (reg.reg == "r0") {
                val cmdNode = AssemblerInstrNode(loc, "lpm", null)
                compiler.compileAssembler(cmdNode)
            } else {
                compiler.compileAsmRegPair("lpm", reg, index, loc)
            }
        } else if (index is SuffixOperNode && index.expression is RegisterGroupNode && index.expression.isZ() && index.operator == Operator.INC) {
            compiler.compileAsmRegPair("lpm", reg, index, loc)
        } else {
            errorUnsupportedOperation(loc)
        }
    } else if (right is VariableNode) {
        val variable = right.variable
        if (variable.size != null || variable.dataType != DataType.BYTE) {
            errorByteVariableExpected(right)
        }
        compiler.compileAsmRegNum("lds", reg, right.address, loc)
    } else if (right is BinaryOperNode) {
        compileAssignRegisterToBinaryOp(compiler, reg, right, loc)
    } else if (right is PrefixOperNode && right.operator == Operator.MINUS && right.expression is RegisterNode) {
        val other = right.expression
        if (reg.reg != other.reg) {
            compiler.compileAsmRegReg("mov", reg, other, loc)
        }
        compiler.compileAsmReg("neg", reg, loc)
    } else if (right is ResolvedExternVar) {
        compileAssignFromExternVariable(compiler, reg, right)
    } else if (right is IdentifierNode) {
        errorUndefinedIdentifier(right);
    } else {
        TODO("${right.javaClass} ; $right $loc")
    }

}

private fun compileAssignRegisterToBinaryOp(compiler: AvrRatCompiler, reg: RegisterNode, binop: BinaryOperNode, loc: SourceLocation) {
    if (binop.operator == Operator.ASSIGN) {
        val left = compiler.resolveArg(binop.left)
        if (left is RegisterNode) {
            compileBinaryOp(compiler, binop)
            compileAssignRegisterTo(compiler, reg, left, loc)
            return
        }
    }
    val simplified = simplifyBinaryOp(compiler, binop)
    if (simplified is BinaryOperNode) {
        val expanded = mutableListOf<Node>()
        val base = expandBinaryOp(simplified, expanded)
        when {
            base is RegisterNode -> {
                if (base.reg != reg.reg) {
                    compileAssignRegisterTo(compiler, reg, base, loc)
                }
                for (n in expanded) {
                    if (n is BinaryOperNode) {
                        val l = n.getLocation()
                        when (base) {
                            n.left -> when (n.operator) {
                                Operator.PLUS -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.PLUS_ASSIGN, reg, n.right))
                                Operator.MINUS -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.MINUS_ASSIGN, reg, n.right))
                                Operator.OR -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.OR_ASSIGN, reg, n.right))
                                Operator.AND -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.AND_ASSIGN, reg, n.right))
                                Operator.SHL -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.SHL_ASSIGN, reg, n.right))
                                Operator.SHR -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.SHR_ASSIGN, reg, n.right))
                                Operator.ASSIGN -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.ASSIGN, reg, n.right))
                                else -> errorUnsupportedOperation(n)
                            }
                            n.right -> when (n.operator) {
                                Operator.PLUS -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.PLUS_ASSIGN, reg, n.left))
                                Operator.MINUS -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.MINUS_ASSIGN, reg, n.left))
                                //                                Operator.OR -> compileBinaryOp(compiler, BinaryOperNode(Operator.OR_ASSIGN, reg, n.binop))
                                //                                Operator.AND -> compileBinaryOp(compiler, BinaryOperNode(Operator.AND_ASSIGN, reg, n.binop))
                                //                                Operator.SHL -> compileBinaryOp(compiler, BinaryOperNode(Operator.SHL_ASSIGN, reg, n.binop))
                                //                                Operator.SHR -> compileBinaryOp(compiler, BinaryOperNode(Operator.SHR_ASSIGN, reg, n.binop))
                                Operator.ASSIGN -> compileBinaryOp(compiler, BinaryOperNode(l, Operator.ASSIGN, reg, n.left))
                                else -> errorUnsupportedOperation(n)
                            }
                            else -> errorUnsupportedOperation(binop)
                        }
                    } else {
                        errorUnsupportedOperation(binop)
                    }

                }
            }
            base is PrgArrayNode || base is MemArrayNode || base is IoPortNode  -> {
                compileAssignRegisterTo(compiler, reg, base, loc)
                if (expanded.size == 1) {
                    compileBinaryOp(compiler, BinaryOperNode(loc,
                            selfAssignOperator(simplified.operator),
                            reg,
                            simplified.right
                    ))
                }
                return
            }
            expanded.size == 1 -> {
                compileAssignRegisterTo(compiler, reg, simplified.left, loc)
                compileBinaryOp(compiler, BinaryOperNode(loc,
                        selfAssignOperator(simplified.operator),
                        reg,
                        simplified.right
                ))
            }
            else -> errorUnsupportedOperation(binop)
        }
    } else {
        compileAssignRegisterTo(compiler, reg, simplified, loc)
    }

//        val r1 = compiler.resolveArg(binop.left)
//        val r2 = compiler.resolveArg(binop.binop)

//        print("L: $r1\n")
//        print("R: $r2\n")
    /* (ASSIGN, r1, (PLUS, (MINUS, (MINUS, (PLUS, (SHL, r2, 3), r3), r4), 5), (MUL, 2, 2)))
     * (ASSIGN, r1, (PLUS, (MINUS, (MINUS, (PLUS, (SHL, r2, 3), r3), r4), 5), 4))
     * (ASSIGN, r1, (MINUS, (MINUS, (PLUS, (SHL, r2, 3), r3), r4), 1))
     * r1 = r2
     * (MINUS, (MINUS, (PLUS, (SHL_ASSIGN, r1, 3), r3), r4), 1)
     *
     * r1 = r2
     * (SHL_ASSIGN, r1, 3)
     * (PLUS_ASSIGN, r1, r3)
     * (MINUS_ASSIGN, r1, r4)
     * (MINUS_ASSIGN, r1, 1)
     *
     *
     * binop(const, const) -> const
     * binop(+, binop(+, X, const1), const2) -> binop(+, X, const1+const2)
     *
     * r1 = binop(OP, r2, x) -> r1 = r2, binop(OP=, r1, x)
     */



}


fun compileAssignRegisterToNode(compiler: AvrRatCompiler, dest: Node, reg: RegisterNode, loc: SourceLocation) {
    if (dest is MemArrayNode) {
        // X, X++, --X
        // Y, Y++, --Y, Y+q
        // Z, Z++, --Z, Z+q
        val index = dest.index
        if (index is RegisterGroupNode || index is SuffixOperNode || index is PrefixOperNode) {
            compiler.compileAsmPairReg("st", index, reg, loc)
        } else if (index is BinaryOperNode) {
            compiler.compileAsmPairReg("std", index, reg, loc)
        } else {
            errorUnsupportedOperation(loc)
        }
    } else if (dest is IoPortNode) {
        // TODO !!! check sizes
        val offset = NumberNode(dest.getLocation(), dest.address, 16)
        if (dest.address <= 0x3f) {
            compiler.compileAsmNumReg("out", offset, reg, loc)
        } else {
            compiler.compileAsmNumReg("sts", offset, reg, loc)
        }
    } else if (dest is VariableNode) {
        val variable = dest.variable
        if (variable.size != null || variable.dataType != DataType.BYTE) {
            errorByteVariableExpected(dest)
        }
        compiler.compileAsmNumReg("sts", dest.address, reg, loc)
    } else if (dest is ResolvedExternVar) {
        compileAssignExternVariableTo(compiler, dest, reg)
    } else {
        TODO("${dest.javaClass} ; $dest ; $loc")
    }
}

fun compileAssignRegGroupTo(compiler: AvrRatCompiler, group: RegisterGroupNode, right: Node, loc: SourceLocation) {
    when (right) {
        is NumberNode -> {
            var index = group.regs.size - 1
            for (reg in group.regs) {
                val outReg = RegisterNode(group.getLocation(), reg)
                val outVal = getNumberByte(right, index--)
                compiler.compileAsmRegNum("ldi", outReg, outVal, loc)
            }
        }
        is VariableNode -> {
            // TODO проверка типа и размера, другие варианты
            if (right.variable.size != null && group.size() == 2) {
                assignPairToValue(compiler, group, right.address, loc)
            } else if (right.variable.size == null && group.size() == 2 && right.variable.dataType == DataType.WORD) {
                compiler.compileAsmRegNum("lds", group.highReg(), right.address+1, loc)
                compiler.compileAsmRegNum("lds", group.lowReg(), right.address, loc)
            } else {
                errorUnsupportedOperation(loc)
            }
        }
        is IdentifierNode -> {
            val label = compiler.findLabel(right.name)
            if (label != null && group.size() == 2) {
                val labelNode = ProgramMemNode(loc, label)//, true)
                val lowNode = BinaryOperNode(loc, Operator.AND, labelNode, NumberNode(loc,0xff))
                val highNode = BinaryOperNode(loc, Operator.SHR, labelNode, NumberNode(loc,8))
                compiler.compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(0), highNode, loc)
                compiler.compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(1), lowNode, loc)
            } else {
                errorUndefinedIdentifier(right)
            }
        }
        is BinaryOperNode -> {
            val opLeft = compiler.resolveArg(right.left)
            val opRight = compiler.resolveArg(right.right)
            if (opLeft is VariableNode && opRight is NumberNode) {
                if (opLeft.variable.size == null && opLeft.variable.dataType.ramSize == group.size()) {
                    compileAssignRegGroupTo(compiler, group, opLeft, loc)
                    when {
                        right.operator == Operator.PLUS -> compilePlusAssignOp(compiler, group, opRight, loc, false)
                        right.operator == Operator.MINUS -> compilePlusAssignOp(compiler, group, opRight, loc, true)
                        else -> errorUnsupportedOperation(loc)
                    }
                } else if (opLeft.variable.size != null) {
                    assignPairToValue(compiler, group,opLeft.address + opRight.value, loc)
                } else {
                    errorUnsupportedOperation(loc)
                }
            } else if (opRight is VariableNode && opLeft is NumberNode) {
                if (opRight.variable.size == null) {
                    errorUnsupportedOperation(loc)
                }
                assignPairToValue(compiler, group, opRight.address + opLeft.value, loc)
            } else if (opLeft is IdentifierNode && opRight is NumberNode) {// && right.operator == Operator.DIV) {
                val label = compiler.findLabel(opLeft.name)
                if (label != null && group.size() == 2) {
                    val labelNode = ProgramMemNode(loc, label)
                    val rightNode = BinaryOperNode(right.getLocation(), right.operator, labelNode, opRight)
                    val lowNode = BinaryOperNode(rightNode.getLocation(), Operator.AND, rightNode, NumberNode(loc, 0xff))
                    val highNode = BinaryOperNode(rightNode.getLocation(), Operator.SHR, rightNode, NumberNode(loc, 8))
                    compiler.compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(0), highNode, loc)
                    compiler.compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(1), lowNode, loc)
                    return
                } else {
                    errorUnsupportedOperation(loc)
                }
            } else if (opLeft is ResolvedExternVar && opLeft.dataType == DataType.PTR && group.size() == 2 && opRight is NumberNode) {
                val newName = when (right.operator) {
                    Operator.PLUS -> "${opLeft.name}+${opRight.value}"
                    Operator.MINUS -> "${opLeft.name}-${opRight.value}"
                    else -> errorWrongExpression(right)
                }
                compiler.compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(0), opLeft.modifyName("hi8($newName)"), loc)
                compiler.compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(1), opLeft.modifyName("lo8($newName)"), loc)
            } else if (right.operator == Operator.PLUS) {
                compileAssignRegGroupTo(compiler, group, opLeft, opLeft.getLocation())
                compilePlusAssignOp(compiler, group, opRight, opRight.getLocation(), false)
            } else if (right.operator == Operator.MINUS) {
                compileAssignRegGroupTo(compiler, group, opLeft, right.getLocation())
                compilePlusAssignOp(compiler, group, opRight, opRight.getLocation(), true)
            } else {
                //TODO("${opLeft.javaClass} $opRight")
                errorUnsupportedOperation(loc)
            }
        }
        is RegisterGroupNode -> {
            assignGroups(compiler, group, right, loc)
//            if (group.size() != right.size()) {
//                errorSizeMismatch(loc, group.size(), right.size())
//            }
//
//            for (index in 0 until group.size()) {
//
//                !!!
//                compiler.compileAsmRegReg("mov", group.getReg(index), right.getReg(index), loc)
//            }
        }
        is IoPortNode -> {
            if (group.size() != right.size) {
                errorSizeMismatch(loc, group.size(), right.size)
            }
            if (right.size != 2) {
                errorUnsupportedOperation(right)
            }

            for (index in 0 until group.size()) {
                val reg = group.getReg(index)
                val addr = right.address + right.size - index - 1
                if (addr <= 0x3f) {
                    compiler.compileAsmRegNum("in", reg, addr, loc)
                } else {
                    compiler.compileAsmRegNum("lds", reg, addr, loc)
                }
             //   compiler.compileAsmRegReg("sts", group.getReg(index), right.address, loc)
            }
        }
        is ResolvedExternVar -> {
            compileAssignFromExternVariable(compiler, group, right)
        }
        is ResolvedExternProc -> {
            compileAssignToExternProc(compiler, group, right)
        }
        else -> TODO("${right.javaClass} $loc")
    }
}

private fun compileAssignVariableTo(compiler: AvrRatCompiler, variable: VariableNode, right: Node) {
    if (variable.variable.size != null) {
        errorWrongExpression(variable)
    }
    val dt = variable.variable.dataType
    val loc = variable.getLocation()
    when {
        dt == DataType.BYTE && right is RegisterNode -> {
            compiler.compileAsmNumReg("sts", variable.address, right, loc)
        }
        dt == DataType.WORD && right is RegisterGroupNode && right.size() == 2 -> {
            compiler.compileAsmNumReg("sts", variable.address+1, right.highReg(), loc)
            compiler.compileAsmNumReg("sts", variable.address, right.lowReg(), loc)
        }
        dt == DataType.DWORD && right is RegisterGroupNode && right.size() == 4 -> {
            compiler.compileAsmNumReg("sts", variable.address+3, right.getReg(3), loc)
            compiler.compileAsmNumReg("sts", variable.address+2, right.getReg(2), loc)
            compiler.compileAsmNumReg("sts", variable.address+1, right.getReg(1), loc)
            compiler.compileAsmNumReg("sts", variable.address+0, right.getReg(0), loc)
        }
        right is NumberNode || right is IoPortNode || right is IoPortBitNode -> {
            errorWrongExpression(right)
        }
        right is PrefixOperNode && right.expression is RegisterNode -> {
            compileRegPrefix(compiler, right)
            compiler.compileAsmNumReg("sts", variable.address, right.expression, loc)
        }
        right is SuffixOperNode && right.expression is RegisterNode -> {
            compiler.compileAsmNumReg("sts", variable.address, right.expression, loc)
            compileRegSuffix(compiler, right)
        }
        dt == DataType.BYTE && right is BinaryOperNode && right.left is RegisterNode && right.operator.isSelfAssign() -> {
            compileBinaryOp(compiler, right)
            compiler.compileAsmNumReg("sts", variable.address, right.left, loc)
            //TODO("${right.left} ${right.right} ${right.operator}")
        }
        else -> {
            TODO("$right")
        }
    }
}

private fun compileRegPrefix(compiler: AvrRatCompiler, node: PrefixOperNode) {
    if (node.expression !is RegisterNode) {
        errorWrongExpression(node)
    }
    when (node.operator) {
        Operator.INC -> {
            compiler.compileAsmReg("inc", node.expression, node.getLocation())
        }
        Operator.DEC -> {
            compiler.compileAsmReg("dec", node.expression, node.getLocation())
        }
        else -> errorWrongExpression(node)
    }
}

private fun compileRegSuffix(compiler: AvrRatCompiler, node: SuffixOperNode) {
    if (node.expression !is RegisterNode) {
        errorWrongExpression(node)
    }
    when (node.operator) {
        Operator.INC -> {
            compiler.compileAsmReg("inc", node.expression, node.getLocation())
        }
        Operator.DEC -> {
            compiler.compileAsmReg("dec", node.expression, node.getLocation())
        }
        else -> errorWrongExpression(node)
    }
}

fun compileAssignExternVariableTo(compiler: AvrRatCompiler, v: ResolvedExternVar, right: Node) {
    if (v.size != null) {
        errorWrongExpression(v)
    }
    val dt = v.dataType
    val loc = v.getLocation()
    //val name = v.name
    when {
        (dt == DataType.BYTE || dt == DataType.PTR) && right is RegisterNode -> {
            compiler.compileAsmExtReg("sts", v, right, loc)
        }
        dt == DataType.BYTE && right is PrefixOperNode && right.expression is RegisterNode -> {
            compileRegPrefix(compiler, right)
            compiler.compileAsmExtReg("sts", v, right.expression, loc)
        }
        dt == DataType.BYTE && right is SuffixOperNode && right.expression is RegisterNode -> {
            compiler.compileAsmExtReg("sts", v, right.expression, loc)
            compileRegSuffix(compiler, right)
        }
        dt == DataType.WORD && right is RegisterGroupNode && right.size() == 2 -> {
            compiler.compileAsmExtReg("sts", v.modifyName(1), right.highReg(), loc)
            compiler.compileAsmExtReg("sts", v, right.lowReg(), loc)
        }
        dt == DataType.DWORD && right is RegisterGroupNode && right.size() == 4 -> {
            compiler.compileAsmExtReg("sts", v.modifyName(3), right.getReg(3), loc)
            compiler.compileAsmExtReg("sts", v.modifyName(2), right.getReg(2), loc)
            compiler.compileAsmExtReg("sts", v.modifyName(1), right.getReg(1), loc)
            compiler.compileAsmExtReg("sts", v, right.getReg(0), loc)
        }
        right is NumberNode || right is IoPortNode || right is IoPortBitNode -> {
            errorWrongExpression(right)
        }
        else -> {
            TODO()
        }
    }
}

fun compileAssignFromExternVariable(compiler: AvrRatCompiler, dest: Node, v: ResolvedExternVar) {
    if (v.size != null) {
        errorWrongExpression(v)
    }
    val dt = v.dataType
    val loc = v.getLocation()
    //val name = v.name
    when {
        (dt == DataType.BYTE || dt == DataType.PTR) && dest is RegisterNode -> {
            compiler.compileAsmRegExt("lds", dest, v, loc)
        }
        dt == DataType.WORD && dest is RegisterGroupNode && dest.size() == 2 -> {
            compiler.compileAsmRegExt("lds", dest.highReg(), v.modifyName(1), loc)
            compiler.compileAsmRegExt("lds", dest.lowReg(), v, loc)
        }
        dt == DataType.DWORD && dest is RegisterGroupNode && dest.size() == 4 -> {
            compiler.compileAsmRegExt("lds", dest.getReg(3), v.modifyName(3), loc)
            compiler.compileAsmRegExt("lds", dest.getReg(2), v.modifyName(2), loc)
            compiler.compileAsmRegExt("lds", dest.getReg(1), v.modifyName(1), loc)
            compiler.compileAsmRegExt("lds", dest.getReg(0), v, loc)
        }
        dt == DataType.PTR && dest is RegisterGroupNode && dest.size() == 2 -> {
            compiler.compileAsmRegExt("ldi", dest.highReg(), v.modifyName("hi8(${v.name})"), loc)
            compiler.compileAsmRegExt("ldi", dest.lowReg(), v.modifyName("lo8(${v.name})"), loc)
        }
        else -> {
            TODO()
        }
    }
}

fun compileAssignToExternProc(compiler: AvrRatCompiler, dest: Node, proc: ResolvedExternProc) {
    if (dest is RegisterGroupNode && dest.size() == 2) {
//        compiler.compileAsmExtReg("lds", v.modifyName(1), right.highReg(), loc)
//        compiler.compileAsmExtReg("lds", v, right.lowReg(), loc)

TODO("$dest = $proc")
    } else {
        errorUnsupportedOperation(dest)
    }
}



private fun assignPairToValue(compiler: AvrRatCompiler, group: RegisterGroupNode, addr: Int, loc: SourceLocation) {
    if (group.size() != 2) {
        errorUnsupportedOperation(loc)
    }
    val high = NumberNode(loc, addr shr 8 and 0xff)
    val low = NumberNode(loc, addr and 0xff)
    compiler.compileAsmRegNum("ldi", group.buildRegisterNode(0), high, loc)
    compiler.compileAsmRegNum("ldi", group.buildRegisterNode(1), low, loc)
}


fun compilePlusAssignOp(compiler: AvrRatCompiler, binop: BinaryOperNode) {
    val left = compiler.resolveArg(binop.left)
    val right = compiler.resolveArg(binop.right)
    val loc = binop.getLocation()
    val minus = binop.operator == Operator.MINUS_ASSIGN
    compilePlusAssignOp(compiler, left, right, loc, minus)
}

fun compilePlusAssignOp(compiler: AvrRatCompiler, left: Node, right: Node, loc: SourceLocation, minus: Boolean) {
    //val right = compiler.resolveArg(rightArg)
    if (left is RegisterNode && right is NumberNode) {
        val num = right.value
        val signed = if (minus) -num else num
        if (num == 0) {
            compiler.warning("zero argument")
        } else if (signed == 1) {
            compiler.compileAsmReg("inc", left, loc)
        } else if (signed == -1) {
            compiler.compileAsmReg("dec", left, loc)
        } else {
            if (minus) {
                compiler.compileAsmRegNum("subi", left, num, loc)
            } else {
                compiler.compileAsmRegNum("subi", left, 0x100 - num, loc)
            }
        }
    } else if (left is RegisterGroupNode && right is NumberNode) {
        val num = right.value
        val signed = if (minus) -num else num

        // ADIW Rd+1 : Rd, K	d ∈ {24, 26, 28, 30}, 0 ≤ K ≤ 63
        // SBIW Rd+1 : Rd, K	d ∈ {24, 26, 28, 30}, 0 ≤ K ≤ 63
        if (left.isHighPair() && signed >= -63 && signed <= 63) {
            if (signed < 0) {
                val cmdNode = AssemblerInstrNode(loc, "sbiw", listOf(left, NumberNode(loc, -signed)))
                compiler.compileAssembler(cmdNode)
            } else {
                val cmdNode = AssemblerInstrNode(loc, "adiw", listOf(left, NumberNode(loc, signed)))
                compiler.compileAssembler(cmdNode)
            }
        } else {
            val numArg = if (signed < 0) -signed else ((1 shl (left.size() * 8)) - signed)
            val abs = if (signed < 0) -signed else signed
            if (signed > 0) {
                compiler.compileAsmRegNum("subi", left.lowReg(), 0x100 - (abs and 0xff), loc)
            } else {
                compiler.compileAsmRegNum("subi", left.lowReg(), numArg and 0xff, loc)
            }
            for (i in left.size() - 2 downTo 0) {
                val byteNum = left.size() - i - 1
                val byte = abs shr 8 * byteNum
                if (signed > 0) {
                    val a = 0x100 - (byte and 0xff)
                    compiler.compileAsmRegNum("sbci", left.getReg(i), a and 0xff, loc)
                } else {
                    compiler.compileAsmRegNum("sbci", left.getReg(i), byte and 0xff, loc)
                }
//                val byteNum = left.size() - i - 1
//                val byte = numArg shr 8 * byteNum
//                compiler.compileAsmRegNum("sbci", left.getReg(i), byte and 0xff, loc)
            }
        }
    } else if (left is RegisterGroupNode && right is ResolvedExternVar) {
        if (right.dataType == DataType.PTR && left.size() == 2) {
            val name = if (minus) right.name else "-(${right.name})"
            compiler.compileAsmRegDeferredNum(AvrCmd.SUBI, left.buildRegisterNode(1), right.modifyName("lo8(${name})"), loc)
            compiler.compileAsmRegDeferredNum(AvrCmd.SBCI, left.buildRegisterNode(0), right.modifyName("hi8(${name})"), loc)
        } else {
            TODO("$left $right, $loc")
        }
    } else if (left is RegisterNode && right is RegisterNode) {
        if (minus) {
            compiler.compileAsmRegReg("sub", left, right, loc)
        } else {
            compiler.compileAsmRegReg("add", left, right, loc)
        }
    } else if (left is RegisterGroupNode && right is RegisterGroupNode) {
        if (left.size() != right.size()) {
            errorSizeMismatch(loc, left.size(), right.size())
        }
        val size = left.size()
        if (minus) {
            for (i in size - 1 downTo 0) {
                val cmd = if (i == size - 1) "sub" else "sbc"
                compiler.compileAsmRegReg(cmd, left.getReg(i), right.getReg(i), loc)
            }
        } else {
            for (i in size - 1 downTo 0) {
                val cmd = if (i == size - 1) "add" else "adc"
                compiler.compileAsmRegReg(cmd, left.getReg(i), right.getReg(i), loc)
            }
        }
    } else if (left is RegisterNode && right is VariableNode) {
        if (right.variable.size != null) {
            errorWrongExpression(loc)
        } else if (right.variable.dataType != DataType.BYTE) {
            errorSizeMismatch(loc, 1, right.variable.dataType.ramSize)
        }
        compiler.compileAsmRegNum("lds", left, right.address, loc)
    } else if (left is RegisterGroupNode && right is VariableNode) {
        if (left.size() == 2 && right.variable.size != null) {
            val arg = 0x10000 - right.address
            compiler.compileAsmRegNum("subi", left.lowReg(), arg and 0xff, loc)
            compiler.compileAsmRegNum("sbci", left.highReg(), (arg shr 8) and 0xff, loc)
        } else {
            TODO("$right")
        }
    } else if (left is RegisterGroupNode && right is IdentifierNode) {
        val label = compiler.findLabel(right.name)
        if (label != null && left.size() == 2) {
            val labelNode = ProgramMemNode(loc, label)//, true)
            val lowNode = BinaryOperNode(left.getLocation(), Operator.AND, labelNode, NumberNode(loc, 0xff))
            val highNode = BinaryOperNode(left.getLocation(), Operator.SHR, labelNode, NumberNode(loc, 8))
            if (minus) {
                compiler.compileAsmRegDeferredNum(AvrCmd.SUBI, left.buildRegisterNode(1), lowNode, loc)
                compiler.compileAsmRegDeferredNum(AvrCmd.SBCI, left.buildRegisterNode(0), highNode, loc)
            } else {
                val low = BinaryOperNode(left.getLocation(), Operator.MINUS, NumberNode(loc, 0x100), lowNode)
                val high = BinaryOperNode(left.getLocation(), Operator.MINUS, NumberNode(loc, 0x100), highNode)

                compiler.compileAsmRegDeferredNum(AvrCmd.SUBI, left.buildRegisterNode(1), low, loc)
                compiler.compileAsmRegDeferredNum(AvrCmd.SBCI, left.buildRegisterNode(0), high, loc)
            }
        } else {
            errorUndefinedIdentifier(right)
        }
    } else if (left is RegisterNode && right is BinaryOperNode) {
        val simplified = simplifyBinaryOp(compiler, right)
        if (simplified is BinaryOperNode) {
            when {
                simplified.operator == Operator.PLUS -> {
                    compilePlusAssignOp(compiler, left, simplified.left, simplified.getLocation(), minus)
                    compilePlusAssignOp(compiler, left, simplified.right, simplified.getLocation(), minus)
                }
                simplified.operator == Operator.MINUS -> {
                    compilePlusAssignOp(compiler, left, simplified.left, simplified.getLocation(), minus)
                    compilePlusAssignOp(compiler, left, simplified.right, simplified.getLocation(), !minus)
                }
                else -> errorWrongExpression(simplified)
            }
        }
    } else {
        TODO("$left $right, ${loc}")
    }
}

fun compileOrAndAssignOp(compiler: AvrRatCompiler, binop: BinaryOperNode) {
    val left = compiler.resolveArg(binop.left)
    val right = compiler.resolveArg(binop.right)
    val op = binop.operator
    if (left is RegisterNode) {
        if (right is RegisterNode) {
            when (op) {
                Operator.OR_ASSIGN ->
                    compiler.compileAsmRegReg("or", left, right, binop.getLocation())
                Operator.AND_ASSIGN ->
                    compiler.compileAsmRegReg("and", left, right, binop.getLocation())
                else -> internalError()
            }
        } else {
            try {
                val constRight = compiler.calculateConst(right)
                val arg = if (constRight < 0) constRight and 0xff else constRight
                when (op) {
                    Operator.OR_ASSIGN ->
                        compiler.compileAsmRegNum("ori", left, arg, binop.getLocation())
                    Operator.AND_ASSIGN ->
                        compiler.compileAsmRegNum("andi", left, arg, binop.getLocation())
                    else -> internalError()
                }
                return
            } catch (e: CompilerException) {
                e.printStackTrace()
                TODO("$right, ${binop.getLocation()}")
            }
        }
    } else {
        TODO("$left |= $right")
    }
}

fun compileAssignIoBitToRegBit(compiler: AvrRatCompiler, dstIoBit: IoPortBitNode, srcReg: RegisterBitNode) {
    if (!dstIoBit.port.isLowerPort()) {
        errorLowIoExpected(dstIoBit)
    }
    compiler.compileAsmRegNum("sbrs", srcReg.reg, srcReg.bit, srcReg.getLocation())
    compiler.compileAsmLowerIoBit("cbi", dstIoBit)
    compiler.compileAsmRegNum("sbrc", srcReg.reg, srcReg.bit, srcReg.getLocation())
    compiler.compileAsmLowerIoBit("sbi", dstIoBit)
}

fun compileAssignIoBitToInvertedRegBit(compiler: AvrRatCompiler, dstIoBit: IoPortBitNode, srcReg: RegisterBitNode) {
    if (!dstIoBit.port.isLowerPort()) {
        errorLowIoExpected(dstIoBit)
    }
    compiler.compileAsmRegNum("sbrc", srcReg.reg, srcReg.bit, srcReg.getLocation())
    compiler.compileAsmLowerIoBit("cbi", dstIoBit)
    compiler.compileAsmRegNum("sbrs", srcReg.reg, srcReg.bit, srcReg.getLocation())
    compiler.compileAsmLowerIoBit("sbi", dstIoBit)
}


fun compileXorAssignOp(compiler: AvrRatCompiler, binop: BinaryOperNode) {
    val left = compiler.resolveArg(binop.left)
    val right = compiler.resolveArg(binop.right)
    if (left is RegisterNode && right is RegisterNode) {
        compiler.compileAsmRegReg("eor", left, right, binop.getLocation())
    } else {
        errorWrongExpression(binop)
    }
}

fun compileShiftAssignOp(compiler: AvrRatCompiler, binop: BinaryOperNode) {
    val left = compiler.resolveArg(binop.left)
    val right = compiler.resolveArg(binop.right)
    val cmdShift = if (binop.operator == Operator.SHL_ASSIGN) "lsl" else "lsr"
    if (left is RegisterNode && right is NumberNode) {
        val num = right.value
        if (num <= 0 || num >= 8) {
            errorInvalidArgument(right)
        }
        for (n in 1..num) {
            compiler.compileAsmReg(cmdShift, left, binop.getLocation())
        }
    } else if (left is RegisterGroupNode && right is NumberNode) {
        val num = right.value
        val size = left.size()
        if (num <= 0 || num >= 8*size) {
            errorInvalidArgument(right)
        }
        val cmdNext = if (binop.operator == Operator.SHL_ASSIGN) "rol" else "ror"
        for (n in 1..num) {
            for (ri in size - 1 downTo 0) {
                val cmd = if (ri == size - 1) cmdShift else cmdNext
                compiler.compileAsmReg(cmd, left.getReg(ri), binop.getLocation())
            }
        }
    } else {
        TODO("${binop.getLocation()}")
        //errorUnsupportedOperation(binop)
    }
}


fun simplifyBinaryOp(compiler: AvrRatCompiler, node: BinaryOperNode): Node {
    val left = compiler.resolveArg(node.left)
    val right = compiler.resolveArg(node.right)
    if (left is BinaryOperNode && right is NumberNode ) {
        val leftRight = compiler.resolveArg(left.right)
        if (leftRight is NumberNode) {
            if (node.operator == Operator.PLUS && left.operator == Operator.PLUS) {
                // binop(+, binop(+, X, const1), const2) -> binop(+, X, const1+const2)
                val leftLeft = compiler.resolveArg(left.left)
                val c = leftRight.value + right.value
                return simplifyNodePlusConst(leftLeft, c)
            } else if (node.operator == Operator.PLUS && left.operator == Operator.MINUS) {
                // binop(+, binop(-, X, const1), const2) -> binop(+, X, -const1+const2)
                val leftLeft = compiler.resolveArg(left.left)
                val c =  -leftRight.value + right.value
                return simplifyNodePlusConst(leftLeft, c)
            } else if (node.operator == Operator.MINUS && left.operator == Operator.PLUS) {
                // binop(-, binop(+, X, const1), const2) -> binop(+, X, const1-const2)
                val leftLeft = compiler.resolveArg(left.left)
                val c =  leftRight.value - right.value
                return simplifyNodePlusConst(leftLeft, c)
            } else if (node.operator == Operator.MINUS && left.operator == Operator.MINUS) {
                // binop(-, binop(-, X, const1), const2) -> binop(+, X, -const1-const2)
                val leftLeft = compiler.resolveArg(left.left)
                val c =  -leftRight.value - right.value
                return simplifyNodePlusConst(leftLeft, c)
            }
        }
        return BinaryOperNode(node.getLocation(), node.operator, left, right)
    } else if (left is BinaryOperNode) {
        return BinaryOperNode(node.getLocation(), node.operator, simplifyBinaryOp(compiler, left), right)
    }

//    * binop(const, const) -> const
//    * binop(+, binop(+, X, const1), const2) -> binop(+, X, const1+const2)

    return BinaryOperNode(node.getLocation(), node.operator, left, right)
}

private fun simplifyNodePlusConst(node: Node, c: Int): Node {
    return when {
        c == 0 -> node
        c > 0 -> {
            val cn = NumberNode(node.getLocation(), c)
            BinaryOperNode(node.getLocation(), Operator.PLUS, node, cn)
        }
        else -> {
            val cn = NumberNode(node.getLocation(), -c)
            BinaryOperNode(node.getLocation(), Operator.MINUS, node, cn)
        }
    }
}

private fun expandBinaryOp(node: BinaryOperNode, out: MutableList<Node>): Node? {
    return if (node.left is BinaryOperNode) {
        val base = expandBinaryOp(node.left, out)
        if (base != null) {
            val ex = BinaryOperNode(node.getLocation(), node.operator, base, node.right)
            out.add(ex)
        }
        base
    } else if (node.left is PrgArrayNode || node.left is MemArrayNode || node.left is IoPortNode) {
        val base = node.left
        val ex = BinaryOperNode(node.getLocation(), node.operator, base, node.right)
        out.add(ex)
        base
    } else {
        out.add(node)
        when {
            node.left is VariableNode || node.right is VariableNode -> null
            node.left is RegisterNode -> node.left
            node.right is RegisterNode && node.operator == Operator.PLUS -> node.right
            else -> null
        }
    }

//    * (ASSIGN, r1, (MINUS, (MINUS, (PLUS, (SHL, r2, 3), r3), r4), 1))
//    * r1 = r2
//    * (MINUS, (MINUS, (PLUS, (SHL_ASSIGN, r1, 3), r3), r4), 1)
//    *
//    * r1 = r2
//    * (SHL_ASSIGN, r1, 3)
//    * (PLUS_ASSIGN, r1, r3)
//    * (MINUS_ASSIGN, r1, r4)
//    * (MINUS_ASSIGN, r1, 1)

//   r26 = prg[Z++] & r29
//
//
}

private fun assignGroups(compiler: AvrRatCompiler, left: RegisterGroupNode, right: RegisterGroupNode, loc: SourceLocation) {
    if (left.size() != right.size()) {
        errorSizeMismatch(loc, left.size(), right.size())
    }
//    val leftNums = left.buildRegNumbers()
//    val rightNums = right.buildRegNumbers()
    val pairs = mutableListOf<Pair<RegisterNode, RegisterNode>>()
    for (i in 0 until left.size()) {
        val l = left.getReg(i)
        val r = right.getReg(i)
        if (l.reg != r.reg) {
            pairs.add(Pair(l, r))
        }
//        val pair = Pair(l, r)
////        val l = leftNums[i]
////        val r = rightNums[i]
//        if (l != r) {
//            merged.add(l * 1000 + r)
//        }
    }
    val sorted = pairs.sortedWith(compareBy { it.first.getNumber() })
    var skipNext = false
    for (i in 0 until sorted.size) {
        if (skipNext) {
            skipNext = false
            continue
        }
        val pair = sorted[i]
        val dest = pair.first
        val src = pair.second
        if (i+1 < pairs.size && src.getNumber() % 2 == 0 && dest.getNumber() % 2 == 0) {
            val nextPair = sorted[i+1]
            val nextDest = nextPair.first
            val nextSrc = nextPair.second
            if (nextDest.getNumber() == dest.getNumber()+1 && nextSrc.getNumber() == src.getNumber()+1) {
                val arg1 = ArgRegPair.getPairWithHighReg(nextDest.getNumber())
                val arg2 = ArgRegPair.getPairWithHighReg(nextSrc.getNumber())
                compiler.compileAssembler(loc, AvrCmd.MOVW, arg1, arg2)
                skipNext = true
                continue
            }

        }
        if (dest.reg != src.reg) {
            compiler.compileAsmRegReg("mov", dest, src, loc)
        }
    }
}

private fun getNumberByte(num: NumberNode, byteNum: Int): NumberNode {
    val res = num.value shr (8 * byteNum)
    return NumberNode(num.getLocation(), res and 0xff, num.radix)
}

private fun selfAssignOperator(operator: Operator): Operator {
    return when (operator) {
        Operator.PLUS -> Operator.PLUS_ASSIGN
        Operator.MINUS -> Operator.MINUS_ASSIGN
        Operator.AND -> Operator.AND_ASSIGN
        Operator.OR -> Operator.OR_ASSIGN
        Operator.SHL -> Operator.SHL_ASSIGN
        Operator.SHR -> Operator.SHR_ASSIGN
        Operator.MUL -> Operator.MUL_ASSIGN
        Operator.DIV -> Operator.DIV_ASSIGN
        Operator.MOD -> Operator.MOD_ASSIGN
        Operator.XOR -> Operator.XOR_ASSIGN
        else -> internalError()
    }
}


fun errorByteVariableExpected(node: VariableNode): Nothing {
    throw CompilerException("Byte variable expected: ${node.variable.name}", node.getLocation())
}

private fun errorWrongDividerExpected2(divider: NumberNode): Nothing {
    throw CompilerException("Unsupported divider, '2' expected: ${divider.value}", divider.getLocation())
}
