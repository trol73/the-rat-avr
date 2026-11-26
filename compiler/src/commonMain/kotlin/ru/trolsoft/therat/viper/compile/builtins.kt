package ru.trolsoft.therat.viper.compile

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.compiler.errorInvalidBitNumber
import ru.trolsoft.compiler.compiler.errorUndefinedIdentifier
import ru.trolsoft.compiler.compiler.errorWrongExpression
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.parser.CallArgumentNode
import ru.trolsoft.compiler.parser.FunctionCallNode
import ru.trolsoft.compiler.parser.IdentifierNode
import ru.trolsoft.compiler.parser.Node
import ru.trolsoft.compiler.parser.NumberNode
import ru.trolsoft.compiler.parser.VariableNode
import ru.trolsoft.therat.viper.ViperRatCompiler

private val builtins = setOf("high", "low", "sizeof", "bitmask")

fun FunctionCallNode.isBuiltin(): Boolean = builtins.contains(name)

fun FunctionCallNode.compileBuiltin(compiler: ViperRatCompiler): Node {
    when (name) {
        "high" -> return compiler.compileHigh(this)
        "low" -> return compiler.compileLow(this)
        "bitmask" -> return compiler.compileBitmask(this)
        "sizeof" -> return compiler.compileSizeOf(this)
    }
    internalError()
}

private fun ViperRatCompiler.compileHigh(func: FunctionCallNode): NumberNode {
    if (func.args.size != 1) {
        errorOneArgumentExpected(func)
    }
    val srcArg = resolveArg((func.args[0] as CallArgumentNode).expr)
    val arg = calculateConst(srcArg)
    val res = (arg shr 8) and 0xff
    return NumberNode(func.getLocation(), res, 16)
}

private fun ViperRatCompiler.compileLow(func: FunctionCallNode): NumberNode {
    if (func.args.size != 1) {
        errorOneArgumentExpected(func)
    }
    val arg = calculateConst((func.args[0] as CallArgumentNode).expr)
    val res = arg and 0xff
    return NumberNode(func.getLocation(), res, 16)
}

fun ViperRatCompiler.compileBitmask(func: FunctionCallNode): NumberNode {
    var res = 0
    for (arg in func.args) {
        val expr = (arg as CallArgumentNode).expr
        val value = calculateConst(expr)
        res = setBit(res, value, arg)
    }
    return NumberNode(func.getLocation(), res, 2)
}


private fun ViperRatCompiler.setBit(mask: Int, bit: Int, bitNode: Node): Int {
    if (bit < 0 || bit > 7) {
        errorInvalidBitNumber(bit, bitNode)
    }
    val bitMask = 1 shl bit
    if (mask and bitMask != 0) {
        warning("Duplicate bit in bitmask: $bitNode", bitNode)
    }
    return mask or bitMask
}

fun ViperRatCompiler.compileSizeOf(func: FunctionCallNode): NumberNode {
    if (func.args.size != 1) {
        errorOneArgumentExpected(func)
    }
    when (val arg = resolveArg((func.args.first() as CallArgumentNode).expr)) {
        is VariableNode -> {
//            if (arg.variable.dataType == DataType.STRUCTURED) {
//                val struct = getStruct(arg.variable.structName) ?: internalError()
//                return arg.asNum(struct.getSize())
//            }
            return arg.asNum(arg.variable.ramSize())
        }
        is IdentifierNode -> {
//            val struct = getStruct(arg.name)
//            if (struct != null) {
//                return arg.asNum(struct.getSize())
//            }
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
