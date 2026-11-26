package ru.trolsoft.therat.viper

import ru.trolsoft.compiler.general.StructuredData
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.AstNode
import ru.trolsoft.compiler.parser.Node

class ViperAstNode(
    val structures: Map<String, StructuredData>,
    nodes: List<Node>
) : AstNode(nodes)

data class RegisterPairNode(private val loc: SourceLocation, val pair: String) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "pair#$pair"
}
