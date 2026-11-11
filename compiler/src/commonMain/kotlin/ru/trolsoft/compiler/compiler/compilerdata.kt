package ru.trolsoft.compiler.compiler

import ru.trolsoft.compiler.general.Use
import ru.trolsoft.compiler.parser.*

data class Procedure (
    val name: String,
    val args: List<ProcArgumentNode>,
    val results: List<ProcArgumentNode>,
    val node: ProcNode
) {
    private val uses: MutableMap<String, Use> = mutableMapOf()
    private val constValues: MutableMap<String, Node> = mutableMapOf()

    fun hasUse(name: String) = uses.containsKey(name)
    fun getUse(name: String) = uses[name]
    fun hasArg(name: IdentifierNode) = getArg(name) != null

    fun getArg(name: IdentifierNode): Node? {
        val argName = name.name
        for (arg in args) {
            if (arg.name == argName) {
                return when (val expr = arg.expr) {
                    is RegisterNode -> RegisterNode(name.getLocation(), expr.reg)
                    is RegisterGroupNode -> RegisterGroupNode(name.getLocation(), expr.regs)
                    else -> expr
                }
            }
        }
        for (res in results) {
            if (res.name == argName) {
                return when (val expr = res.expr) {
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

    fun removeUse(name: String) {
        uses.remove(name)
    }

    fun getUses(): Map<String, Use> = uses

    private fun isSameRegister(a: Node, b: Node): Boolean {
        if (a === b) return true
        if (a is RegisterNode && b is RegisterNode) {
            return a.reg == b.reg
        }
        return false
    }

    fun addConst(name: String, expr: Node) {
        constValues[name] = expr
    }

    fun hasConst(name: String) = constValues.containsKey(name)

    fun getConst(name: String): Node? = constValues[name]
}
