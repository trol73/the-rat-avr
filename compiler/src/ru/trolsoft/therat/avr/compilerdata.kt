package ru.trolsoft.therat.avr

import ru.trolsoft.compiler.parser.IdentifierNode
import ru.trolsoft.compiler.parser.Node
import ru.trolsoft.compiler.parser.RegisterGroupNode
import ru.trolsoft.compiler.parser.RegisterNode

data class Pin (
    val port: Char,
    val pin: Int
)

data class Var (
    val name: String,
    val dataType: DataType,
    val size: Int?,
    val offset: Int
) {
    fun ramSize(): Int = dataType.ramSize * (size ?: 1)
}

data class Use (
    val name: String,
    val reg: Node,
    val capture: Boolean
)

data class Procedure (
    val name: String,
    val args: List<ProcArgumentNode>,
    val node: ProcNode
) {
    private val uses: MutableMap<String, Use> = mutableMapOf()

    fun hasUse(name: String) = uses.containsKey(name)
    fun getUse(name: String) = uses[name]
    fun hasArg(name: IdentifierNode) = getArg(name) != null

    fun getArg(name: IdentifierNode): Node? {
        val argName = name.name
        for (arg in args) {
            if (arg.name == argName) {
                val expr = arg.expr
                return when (expr) {
                    is RegisterNode -> RegisterNode(name.getLocation(), expr.reg)
                    is RegisterGroupNode -> RegisterGroupNode(name.getLocation(), expr.regs)
                    else -> expr
                }
            }
        }
        return null
    }

    fun addUse(use: Use) {
        uses[use.name] = use
    }
}

data class CycleBlock (
        val startLabel: String,
        val endLabel: String
)