package ru.trolsoft.therat.avr.compiler

import ru.trolsoft.compiler.compiler.*
import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.arch.avr.IoRegister
import ru.trolsoft.therat.avr.*
import ru.trolsoft.therat.i8085.errorPairExpected

private val builtins = setOf("high", "low", "sizeof", "bitmask")

fun FunctionCallNode.isBuiltin(): Boolean = builtins.contains(name)

fun FunctionCallNode.compileBuiltin(compiler: AvrRatCompiler): Node {
    when (name) {
        "high" -> return compileHigh(compiler, this)
        "low" -> return compileLow(compiler, this)
        "bitmask" -> return compileBitmask(compiler, this)
        "sizeof" -> return compileSizeOf(compiler, this)
    }
    internalError()
}



private fun compileHigh(compiler: AvrRatCompiler, func: FunctionCallNode): Node {
    if (func.args.size != 1) {
        errorOneArgumentExpected(func)
    }
    val srcArg = compiler.resolveArg((func.args[0] as CallArgumentNode).expr)
    if (srcArg is RegisterGroupNode) {
        if (srcArg.regs.size != 2) {
            errorPairExpected(srcArg)
        }
        return srcArg.highReg()
    }
    val arg = compiler.calculateConst(srcArg)
    val res = (arg shr 8) and 0xff
    return NumberNode(func.getLocation(), res, 16)
}

private fun compileLow(compiler: AvrRatCompiler, func: FunctionCallNode): Node {
    if (func.args.size != 1) {
        errorOneArgumentExpected(func)
    }
    val srcArg = compiler.resolveArg((func.args[0] as CallArgumentNode).expr)
    if (srcArg is RegisterGroupNode) {
        if (srcArg.regs.size != 2) {
            errorPairExpected(srcArg)
        }
        return srcArg.lowReg()
    }
    val arg = compiler.calculateConst(srcArg)
    val res = arg and 0xff
    return NumberNode(func.getLocation(), res, 16)
}

fun compileBitmask(compiler: AvrRatCompiler, func: FunctionCallNode): NumberNode {
    var res = 0
    var lastIoPort: IoRegister? = null
    var showWarning = false
    // TODO bitmask for multibyte registers
    for (arg in func.args) {
        val expr = (arg as CallArgumentNode).expr
        if (expr is IdentifierNode) {
            val resolved = compiler.resolveArg(expr)
            if (resolved is IdentifierNode) {
                val bitName = resolved.name
                if (lastIoPort != null) {
                    val num = lastIoPort.getBitNumber(bitName)
                    if (num < 0) {
                        showWarning = true
                    } else {
                        res = setBit(compiler, res, num, arg)
                        continue
                    }
                }
                lastIoPort = compiler.findIoWithBit(bitName)
                if (lastIoPort == null) {
                    val pin = compiler.pins[bitName] ?: errorUnknownBit(expr)
                    res = setBit(compiler, res, pin.pin, arg)
                    continue
                }
                val num2 = lastIoPort.getBitNumber(bitName)
                res = setBit(compiler, res, num2, arg)
                continue
            }
        }
        val value = compiler.calculateConst(expr)
        res = setBit(compiler, res, value, arg)
    }
    return NumberNode(func.getLocation(), res, 2)
}


private fun setBit(compiler: AvrRatCompiler, mask: Int, bit: Int, bitNode: Node): Int {
    if (bit !in 0..15) {
        errorInvalidBitNumber(bit, bitNode)
    }
    val bitMask = 1 shl bit
    if (mask and bitMask != 0) {
        compiler.warning("Duplicate bit in bitmask: $bitNode", bitNode)
    }
    return mask or bitMask
}

fun compileSizeOf(compiler: AvrRatCompiler, func: FunctionCallNode): NumberNode {
    if (func.args.size != 1) {
        errorOneArgumentExpected(func)
    }
    when (val arg = compiler.resolveArg((func.args.first() as CallArgumentNode).expr)) {
        is VariableNode -> {
            if (arg.variable.dataType == DataType.STRUCTURED) {
                val struct = compiler.getStruct(arg.variable.structName) ?: internalError()
                return arg.asNum(struct.getSize())
            }
            return arg.asNum(arg.variable.ramSize())
        }
        is IdentifierNode -> {
            val struct = compiler.getStruct(arg.name)
            if (struct != null) {
                return arg.asNum(struct.getSize())
            }
            errorUndefinedIdentifier(arg)
        }
        else -> {
            errorWrongExpression(func)
        }
    }
}

private fun Node.asNum(v: Int) = NumberNode(this.getLocation(), v)


private fun errorOneArgumentExpected(node: FunctionCallNode): Nothing {
    throw CompilerException("One argument expected but ${node.args.size} found", node.getLocation())
}

private fun errorUnknownBit(node: IdentifierNode): Nothing {
    throw CompilerException("Unknown bit: ${node.name}", node.getLocation())
}

private fun errorInvalidBitNumber(bit: Int, node: Node): Nothing {
    throw CompilerException("Invalid bit number: $bit (must be 0-15)", node.getLocation())
}
