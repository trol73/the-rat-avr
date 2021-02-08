package ru.trolsoft.therat.avr

import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.AstNode
import ru.trolsoft.compiler.parser.CompoundStmt
import ru.trolsoft.compiler.parser.Node

class AvrAstNode(
        val vectors: VectorTableNode?,
        nodes: List<Node>
) : AstNode(nodes)

data class VectorTableNode(private val loc: SourceLocation, val table: Map<String, Node>) : Node() {
    override fun getLocation(): SourceLocation = loc

    fun getVectorNode(name: String): Node? {
        for (v in table) {
            if (v.key == name) {
                return v.value
            }
        }
        return null
    }
}

data class VarDeclarationNode(private val loc: SourceLocation, val name: String, val dataType: DataType) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class PinDeclarationNode(private val loc: SourceLocation, val name: String, val devicePin: String) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class DataArrayDeclarationNode(private val loc: SourceLocation, val name: String, val dataType: DataType, val size: Node) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class ExternVarNode(private val loc: SourceLocation, val vars: List<Node>) : Node() {
    override fun getLocation(): SourceLocation = loc

    operator fun get(index: Int) = vars[index]
    operator fun iterator(): Iterator<Node> = vars.iterator()
    fun size(): Int = vars.size

    fun getVarName(): String {
        assert(vars.size == 1)
        val single = vars.first()
        return when (single) {
            is VariableNode -> {
                single.variable.name
            }
            is DataArrayDeclarationNode -> {
                single.name
            }
            else -> {
                internalError()
            }
        }
    }
}

data class ResolvedExternVar(
        private val loc: SourceLocation,
        val name: String,
        val size: Int?,
        val dataType: DataType
) : Node() {
    override fun getLocation(): SourceLocation = loc

    fun modifyName(newName: String): ResolvedExternVar = ResolvedExternVar(loc, newName, size, dataType)
    fun modifyName(offset: Int): ResolvedExternVar = ResolvedExternVar(loc, "$name+$offset", size, dataType)
}

data class ResolvedExternProc(
        private val loc: SourceLocation,
        val name: String,
        val args: List<ProcArgumentNode>
        ) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class ExternProcNode(private val loc: SourceLocation, val proc: ProcNode) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class ProcArgumentNode(private val loc: SourceLocation, val name: String, val expr: Node) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "$name: $expr"
}

data class ProcNode(private val loc: SourceLocation,
                    val name: String,
                    val args: List<ProcArgumentNode>,
                    val body: CompoundStmt,
                    val inline: Boolean
) : Node() {
    override fun getLocation(): SourceLocation = loc

    fun getArg(name: String): Node? {
        for (arg in args) {
            if (arg.name == name) {
                return arg.expr
            }
        }
        return null
    }
}

data class CallArgumentNode(private val loc: SourceLocation, val name: String?, val expr: Node) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = if (name != null) "$name: $expr" else expr.toString()
}

data class DirectiveNode(private val loc: SourceLocation, val name: String, val args: List<Node>?) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = if (args != null) "$$name($args)" else "$$name"
}