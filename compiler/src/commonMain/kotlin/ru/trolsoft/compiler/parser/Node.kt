package ru.trolsoft.compiler.parser

import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.general.StructuredData
import ru.trolsoft.compiler.general.Var
import ru.trolsoft.therat.arch.avr.RegisterBit
import ru.trolsoft.therat.lang.Token

interface Node {
    fun getLocation(): SourceLocation
}

open class AstNode(val nodes: List<Node>) : Node {
    operator fun get(offset: Int) = nodes[offset]
    operator fun iterator(): Iterator<Node> = nodes.iterator()
    fun size(): Int = nodes.size


    override fun getLocation(): SourceLocation {
        if (nodes.isEmpty()) {
            return SourceLocation("", 0, 0)
        }
        return nodes.first().getLocation()
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (s in nodes) {
            sb.append(s).append('\n')
        }
        return sb.toString()
    }

}

data class CommentNode(val comments: List<Token>) : Node {
    override fun getLocation(): SourceLocation = comments.first().location
}


data class CompoundStmt(val nodes: List<Node>) : Node {
    override fun getLocation(): SourceLocation = nodes.first().getLocation()
    override fun toString(): String = nodes.toString()

    operator fun get(offset: Int) = nodes[offset]
    operator fun iterator(): Iterator<Node> = nodes.iterator()
    fun size(): Int = nodes.size
    fun isEmpty(): Boolean = size() == 0
    fun first(): Node = nodes.first()
    fun last(): Node = nodes.last()
    fun last(filter: (Node) -> Boolean): Node = nodes.last { filter(it) }
}

data class RegisterNode(private val loc: SourceLocation, val reg: String) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "reg#$reg"
}


data class RegisterGroupNode(private val loc: SourceLocation, val regs: List<String>) : Node {
    override fun getLocation(): SourceLocation = loc
    fun size() = regs.size
    operator fun get(i: Int) = regs[i]

    fun getReg(index: Int) = RegisterNode(loc, regs[index])
    fun highReg() = getReg(0)
    fun lowReg() = getReg(size() - 1)

    override fun toString(): String =
        StringBuilder().apply {
            for (r in regs) {
                if (isNotEmpty()) {
                    append('.')
                }
                append(r)
            }
        }.toString()

    override fun equals(other: Any?): Boolean =
        other is RegisterGroupNode && regs == other.regs

    override fun hashCode(): Int = regs.hashCode()
}

data class RegisterBitNode(
    val reg: RegisterNode,
    val bit: Int
) : Node {
    override fun getLocation(): SourceLocation = reg.getLocation()
    override fun toString(): String = "$reg[$bit]"
}


data class DotGroupNode(val items: List<Node>) : Node {
    override fun getLocation(): SourceLocation = items.first().getLocation()
}

data class NumberNode(private val loc: SourceLocation, val value: Int, val radix: Int = 10) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String {
        return when (radix) {
            2 -> "0b${value.toString(radix)}"
            8 -> "0${value.toString(radix)}"
            10 -> value.toString(radix)
            16 -> "0x${value.toString(radix)}"
            else -> value.toString(radix)
        }
    }
}

data class CharLiteralNode(private val loc: SourceLocation, val char: Char) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "'$char'"
}

data class StringLiteralNode(private val loc: SourceLocation, val str: String) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "\"$str\""
}

data class IdentifierNode(private val loc: SourceLocation, val name: String) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = name
}

data class PrefixOperNode(private val loc: SourceLocation, val operator: Operator, val expression: Node) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class SuffixOperNode(val operator: Operator, val expression: Node) : Node {
    override fun getLocation(): SourceLocation = expression.getLocation()
}

data class BinaryOperNode(private val loc: SourceLocation, val operator: Operator, val left: Node, val right: Node) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class LabelNode(private val loc: SourceLocation, val name: String, val global: Boolean = false) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class LoopNode(private val loc: SourceLocation, val cond: Node?, val body: Node) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class IfNode(private val loc: SourceLocation, val cond: Node, val thenBlock: Node, val elseBlock: Node?) : Node {
    override fun getLocation(): SourceLocation = loc

    fun hasEmptyThenBlock(): Boolean =
        thenBlock is CompoundStmt && thenBlock.nodes.isEmpty()

    fun hasNotEmptyThenBlock(): Boolean =
        !hasEmptyThenBlock()

    fun hasEmptyElseBlock(): Boolean =
        elseBlock == null || (elseBlock is CompoundStmt && elseBlock.nodes.isEmpty())

    fun hasNotEmptyElseBlock(): Boolean = !hasEmptyElseBlock()
}

data class GotoNode(private val loc: SourceLocation, val label: String) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class BreakNode(private val loc: SourceLocation) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class ContinueNode(private val loc: SourceLocation) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class IndexedNode(val name: IdentifierNode, val index: Node) : Node {
    override fun getLocation(): SourceLocation = name.getLocation()
}

data class IndexedRegNode(val reg: RegisterNode, val index: Node) : Node {
    override fun getLocation(): SourceLocation = reg.getLocation()
}

data class IndexedNumNode(val num: NumberNode, val index: Node) : Node {
    override fun getLocation(): SourceLocation = num.getLocation()
}

data class FunctionCallNode(private val loc: SourceLocation, val name: String, val args: List<Node>) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class AssemblerInstrNode(private val loc: SourceLocation, val instr: String, val args: List<Node>?) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class DoWhileNode(
    private val loc: SourceLocation,
    val body: CompoundStmt,
    val cond: Node
) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class DataBlockNode(
    private val loc: SourceLocation,
    val type: DataType,
    val items: List<Node>,
    val structName: String? = null,
    val declaredSize: Node? = null
) : Node {
    override fun getLocation(): SourceLocation = loc

    operator fun get(i: Int) = items[i]
    operator fun iterator(): Iterator<Node> = items.iterator()
    fun size() = items.size
}


data class MapNode(
    private val loc: SourceLocation,
    val items: Map<String, Node>,
    val struct: StructuredData
) : Node {
    override fun getLocation(): SourceLocation = loc
    operator fun get(name: String) = items[name]
    operator fun iterator(): Iterator<Map.Entry<String, Node>> = items.iterator()
    fun size() = items.size
}

data class SignedCastNode(
    private val loc: SourceLocation,
    val expr: Node
) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class SaveregsNode(private val loc: SourceLocation, val args: List<Node>, val body: Node) : Node {
    override fun getLocation(): SourceLocation = loc
}


data class VarDeclarationNode(
    private val loc: SourceLocation,
    val name: String,
    val dataType: DataType,
    val structName: String? = null,
    val absolute: Int? = null
) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class VariableNode(
    private val loc: SourceLocation,
    val variable: Var,
    val address: Int
) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "var(${variable.name})"
}

data class DataArrayDeclarationNode(
    private val loc: SourceLocation,
    val name: String,
    val dataType: DataType,
    val size: Node,
    val structName: String? = null,
    val absolute: Int? = null
) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class IoPortNode(
    private val loc: SourceLocation,
    val name: String,
    val address: Int,
    val size: Int,
    val bits: List<RegisterBit>
) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "\"$name\""
    fun isLowerPort(): Boolean = address <= 31

    fun getBit(name: String): Int? {
        for ((name1, number) in bits) {
            if (name1 == name) {
                return number
            }
        }
        return null
    }
}

data class ExternVarNode(private val loc: SourceLocation, val vars: List<Node>) : Node {
    override fun getLocation(): SourceLocation = loc

    operator fun get(index: Int) = vars[index]
    operator fun iterator(): Iterator<Node> = vars.iterator()
    fun size(): Int = vars.size

    fun getVarName(): String =
        when (val single = vars.first()) {
            is VariableNode -> single.variable.name
            is DataArrayDeclarationNode -> single.name
            else -> internalError()
        }
}

data class ResolvedExternVar(
    private val loc: SourceLocation,
    val name: String,
    val size: Int?,
    val dataType: DataType,
    val struct: StructuredData?
) : Node {
    override fun getLocation(): SourceLocation = loc

    fun modifyName(newName: String) = ResolvedExternVar(loc, newName, size, dataType, struct)
    fun modifyName(offset: Int): ResolvedExternVar =
        when {
            offset < 0 -> ResolvedExternVar(loc, "$name$offset", size, dataType, struct)
            offset == 0 -> this
            else -> ResolvedExternVar(loc, "$name+$offset", size, dataType, struct)
        }

}

data class ResolvedExternProc(
    private val loc: SourceLocation,
    val name: String,
    val args: List<ProcArgumentNode>
) : Node {
    override fun getLocation(): SourceLocation = loc

    fun modifyName(newName: String) = ResolvedExternProc(loc, newName, args)
}

data class ExternProcNode(private val loc: SourceLocation, val proc: ProcNode, val address: Int? = null) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class ProcArgumentNode(private val loc: SourceLocation, val name: String, val expr: Node) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "$name: $expr"
}

data class ProcNode(
    private val loc: SourceLocation,
    val name: String,
    val args: List<ProcArgumentNode>,
    val results: List<ProcArgumentNode>,
    val body: CompoundStmt,
    val inline: Boolean
) : Node {
    override fun getLocation(): SourceLocation = loc

    fun getArg(name: String): Node? =
        args.firstOrNull { it.name == name } ?.expr
}

data class CallArgumentNode(private val loc: SourceLocation, val name: String?, val expr: Node) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = if (name != null) "$name: $expr" else expr.toString()
}

data class DirectiveNode(private val loc: SourceLocation, val name: String, val args: List<Node>?) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = if (args != null) "$$name($args)" else "$$name"
}

data class MemArrayNode(
    private val loc: SourceLocation,
    val index: Node
) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "mem[$index]"
}

data class PortArrayNode(
    private val loc: SourceLocation,
    val index: Node
) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "port[$index]"
}

data class PrgArrayNode(
    private val loc: SourceLocation,
    val index: Node
) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "prg[$index]"
}

data class ConstValueNode(
    private val loc: SourceLocation,
    val name: String,
    val expr: Node
) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "const $name = $expr"
}


fun tryUnpackCompoundStmt(node: Node) =
    if (node is CompoundStmt && node.size() == 1) node.first() else node
