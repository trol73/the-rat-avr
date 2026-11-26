package ru.trolsoft.therat.avr

import ru.trolsoft.compiler.general.StructuredData
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.AstNode
import ru.trolsoft.compiler.parser.IdentifierNode
import ru.trolsoft.compiler.parser.Node

class AvrAstNode(
    val vectors: VectorTableNode?,
    val structures: Map<String, StructuredData>,
    nodes: List<Node>
) : AstNode(nodes)

data class VectorTableNode(private val loc: SourceLocation, val table: Map<String, Node>) : Node {
    override fun getLocation(): SourceLocation = loc

    fun getVectorNode(name: String): Node? {
        for ((key, value) in table) {
            if (key == name) {
                return value
            }
        }
        return null
    }
}


data class PinDeclarationNode(private val loc: SourceLocation, val name: String, val devicePin: String) : Node {
    override fun getLocation(): SourceLocation = loc
}

data class BusRangeNode(val from: IdentifierNode, val to: IdentifierNode) : Node {
    override fun getLocation(): SourceLocation = from.getLocation()
}

data class BusDeclarationNode(private val loc: SourceLocation, val name: String, val pins: List<Node>, val size: Int) : Node {
    override fun getLocation(): SourceLocation = loc
}

