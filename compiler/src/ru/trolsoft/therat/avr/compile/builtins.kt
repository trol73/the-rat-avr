package ru.trolsoft.therat.avr.compile

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.parser.FunctionCallNode
import ru.trolsoft.compiler.parser.IdentifierNode
import ru.trolsoft.compiler.parser.Node
import ru.trolsoft.compiler.parser.NumberNode
import ru.trolsoft.therat.arch.avr.IoRegister
import ru.trolsoft.therat.avr.AvrRatCompiler
import ru.trolsoft.therat.avr.CallArgumentNode
import ru.trolsoft.therat.avr.VariableNode
import ru.trolsoft.therat.avr.errorInvalidBitNumber

private val builtins = setOf("high", "low", "sizeof", "bitmask")

fun FunctionCallNode.isBuiltin(): Boolean = builtins.contains(name)

fun FunctionCallNode.compileBuiltin(compiler: AvrRatCompiler): NumberNode {
    when (name) {
        "high" -> return compileHigh(compiler, this)
        "low" -> return compileLow(compiler, this)
        "bitmask" -> return compileBitmask(compiler, this)
        "sizeof" -> return compileSizeOf(compiler, this)
    }
    TODO()
}



private fun compileHigh(compiler: AvrRatCompiler, func: FunctionCallNode): NumberNode {
    if (func.args.size != 1) {
        errorOneArgumentExpected(func)
    }
    val srcArg = compiler.resolveArg((func.args[0] as CallArgumentNode).expr)
//    val arg = if (srcArg is VariableNode) {
//        val addr = compiler.getVariableAddress(srcArg.variable)
//        compiler.calculateConst(NumberNode(srcArg.getLocation(), addr))
//    } else {
//        compiler.calculateConst(srcArg)
//    }
    val arg = compiler.calculateConst(srcArg)
    //val arg = compiler.calculateConst(scrArg)
    val res = (arg shr 8) and 0xff
    return NumberNode(func.getLocation(), res, 16)
}

private fun compileLow(compiler: AvrRatCompiler, func: FunctionCallNode): NumberNode {
    if (func.args.size != 1) {
        errorOneArgumentExpected(func)
    }
    val arg = compiler.calculateConst((func.args[0] as CallArgumentNode).expr)
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
                    // TODO проверять порт, если возможно
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
    if (bit < 0 || bit > 7) {
        errorInvalidBitNumber(bit, bitNode)
    }
    val bitMask = 1 shl bit
    if (mask and bitMask != 0) {
        compiler.warning("Duplicate bit in bitmask: $bitNode")
    }
    return mask or bitMask
}

fun compileSizeOf(compiler: AvrRatCompiler, func: FunctionCallNode): NumberNode {
    if (func.args.size != 1) {
        errorOneArgumentExpected(func)
    }
    val arg = compiler.resolveArg((func.args.first() as CallArgumentNode).expr)
    when (arg) {
        is VariableNode -> {
            return NumberNode(arg.getLocation(), arg.variable.ramSize())
        }
        else -> {
            TODO()
        }
    }
}



private fun errorOneArgumentExpected(node: FunctionCallNode): Nothing {
    throw CompilerException("One argument expected but ${node.args.size} found", node.getLocation())
}

private fun errorUnknownBit(node: IdentifierNode): Nothing {
    throw CompilerException("Unknown bit: ${node.name}", node.getLocation())
}
