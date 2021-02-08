package ru.trolsoft.therat.avr

import ru.trolsoft.therat.arch.avr.RegisterBit
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.Node
import ru.trolsoft.compiler.parser.RegisterNode

data class IoPortNode(
        private val loc: SourceLocation,
        val name: String,
        val address: Int,
        val size: Int,
        val bits: List<RegisterBit>
) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "\"$name\""
    fun isLowerPort(): Boolean = address <= 31

    fun getBit(name: String): Int? {
        for (b in bits) {
            if (b.name == name) {
                return b.number
            }
        }
        return null
    }
}

data class IoPortBitNode(
        private val loc: SourceLocation,
        val port: IoPortNode,
        val bitName: String,
        val bitIndex: Int
) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = if (bitName.isEmpty()) "\"${port.name}->$bitIndex\"" else "\"${port.name}->$bitName\""
}

data class UseNode(
        private val loc: SourceLocation,
        val reg: Node,
        val name: String,
        val capture: Boolean
) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "use $reg as $name"
}

data class MemArrayNode(
        private val loc: SourceLocation,
        val index: Node
) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "mem[$index]"
}

data class PrgArrayNode(
        private val loc: SourceLocation,
        val index: Node
) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "prg[$index]"
}

data class RegisterBitNode(
        val reg: RegisterNode,
        val bit: Int
) : Node() {
    override fun getLocation(): SourceLocation = reg.getLocation()
    override fun toString(): String = "$reg[$bit]"
}

data class VariableNode(
        private val loc: SourceLocation,
        val variable: Var,
        val address: Int
) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "var(${variable.name})"

}

data class ProgramMemNode(
        private val loc: SourceLocation,
        val label: String
//        val inWords: Boolean = false
) : Node() {
    override fun getLocation(): SourceLocation = loc
    //override fun toString(): String = if (inWords) "pgmem($label)/2" else "pgmem($label)"
    override fun toString(): String = "pgmem($label)"

}
