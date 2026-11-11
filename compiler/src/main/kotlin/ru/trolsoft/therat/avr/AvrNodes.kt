package ru.trolsoft.therat.avr

import ru.trolsoft.compiler.general.StructuredData
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.IoPortNode
import ru.trolsoft.compiler.parser.Node


data class IoPortBitNode(
    private val loc: SourceLocation,
    val port: IoPortNode,
    val bitName: String,
    val bitIndex: Int
) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String =
        if (bitName.isEmpty()) "\"${port.name}->$bitIndex\"" else "\"${port.name}->$bitName\""
}

data class UseNode(
    private val loc: SourceLocation,
    val reg: Node,
    val name: String,
    val capture: Boolean,
    val isReuse: Boolean = false
) : Node {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "${if (isReuse) "reuse" else "use"} $reg as $name"
}

data class InternalStructVarNode(
    private val loc: SourceLocation,
    val address: Int,
    val struct: StructuredData
) : Node {
    override fun getLocation(): SourceLocation = loc

    override fun toString(): String = "var(${struct.name}:0x${address.toString(16)})"
}

data class ProgramMemNode(
    private val loc: SourceLocation,
    val label: String
) : Node {
    override fun getLocation(): SourceLocation = loc

    override fun toString(): String = "pgmem($label)"
}
