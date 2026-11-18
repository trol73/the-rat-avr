package ru.trolsoft.therat.i8085

import ru.trolsoft.compiler.general.StructuredData
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.AstNode
import ru.trolsoft.compiler.parser.Node
import ru.trolsoft.compiler.parser.TemplateNode

class I8085AstNode(
    val structures: Map<String, StructuredData>,
    val templates: Map<String, TemplateNode>,
    nodes: List<Node>
) : AstNode(nodes)

data class RegisterPairNode(private val loc: SourceLocation, val pair: String) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "pair#$pair"
}
