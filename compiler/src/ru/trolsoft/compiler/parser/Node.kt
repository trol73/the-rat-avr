package ru.trolsoft.compiler.parser

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.therat.avr.DataType
import ru.trolsoft.therat.lang.Token

abstract class Node {
    abstract fun getLocation(): SourceLocation
}

open class AstNode(val nodes: List<Node>) : Node() {
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

data class CommentNode(val comments: List<Token>) : Node() {
    override fun getLocation(): SourceLocation = comments.first().location
}


data class CompoundStmt(val nodes: List<Node>) : Node() {
    override fun getLocation(): SourceLocation = nodes.first().getLocation()
    override fun toString(): String = nodes.toString()

    operator fun get(offset: Int) = nodes[offset]
    operator fun iterator(): Iterator<Node> = nodes.iterator()
    fun size(): Int = nodes.size
}

data class RegisterNode(private val loc: SourceLocation, val reg: String) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "reg#$reg"
    fun getNumber(): Int {
        return when {
            reg[0] == 'r' || reg[0] == 'R' -> reg.substring(1).toInt()
            reg == "XL" -> 26
            reg == "XH" -> 27
            reg == "YL" -> 28
            reg == "YH" -> 29
            reg == "ZL" -> 30
            reg == "ZH" -> 31
            else -> throw CompilerException("Internal error, wrong register $reg", loc)
        }
//        assert(reg[0] == 'r' || reg[0] == 'R')
//        if (reg[0] != 'r' && reg[0] != 'R') {
////            return when (reg) {
////                "XL" -> 26,
////                "XH" -> 27
//////                    X = XH.XL = r27.r26
//////                Y = YH.YL = r29.r28
//////                        Z = ZH.ZL = r31.r30
////            }
//            throw CompilerException("Internal error, wrong register", loc)
        }
//        return reg.substring(1).toInt()
//    }
}

data class RegisterGroupNode(private val loc: SourceLocation, val regs: List<String>) : Node() {
    override fun getLocation(): SourceLocation = loc
    fun size() = regs.size
    operator fun get(i: Int) = regs[i]
    fun getReg(index: Int) = RegisterNode(loc, regs[index])
    fun highReg() = getReg(0)
    fun lowReg() = getReg(size()-1)

    override fun toString(): String {
        val sb = java.lang.StringBuilder()
        for (r in regs) {
            if (sb.isNotEmpty()) {
                sb.append('.')
            }
            sb.append(r)
        }
        return sb.toString()
    }
}

data class DotGroupNode(val items: List<Node>) : Node() {
    override fun getLocation(): SourceLocation = items.first().getLocation()
}

data class NumberNode(private val loc: SourceLocation, val value: Int, val radix: Int = 10) : Node() {
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

data class CharLiteralNode(private val loc: SourceLocation, val char: Char) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "'$char'"
}

data class StringLiteralNode(private val loc: SourceLocation, val str: String) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = "\"$str\""
}

data class IdentifierNode(private val loc: SourceLocation, val name: String) : Node() {
    override fun getLocation(): SourceLocation = loc
    override fun toString(): String = name
}

data class PrefixOperNode(private val loc: SourceLocation, val operator: Operator, val expression: Node) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class SuffixOperNode(val operator: Operator, val expression: Node) : Node() {
    override fun getLocation(): SourceLocation = expression.getLocation()
}

data class BinaryOperNode(private val loc: SourceLocation, val operator: Operator, val left: Node, val right: Node) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class LabelNode(private val loc: SourceLocation, val name: String, val global: Boolean = false) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class LoopNode(private val loc: SourceLocation, val cond: Node?, val body: Node) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class IfNode(private val loc: SourceLocation, val cond: Node, val thenBlock: Node, val elseBlock: Node?) : Node() {
    override fun getLocation(): SourceLocation = loc

    fun hasThenBlock(): Boolean {
        if (thenBlock is CompoundStmt && thenBlock.nodes.isEmpty()) {
            return false
        }
        return true
    }

    fun hasElseBlock(): Boolean {
        if (elseBlock == null) {
            return false
        }
        if (elseBlock is CompoundStmt && elseBlock.nodes.isEmpty()) {
            return false
        }
        return true
    }
}

data class GotoNode(private val loc: SourceLocation, val label: String) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class BreakNode(private val loc: SourceLocation) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class ContinueNode(private val loc: SourceLocation) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class IndexedNode(val name: IdentifierNode, val index: Node) : Node() {
    override fun getLocation(): SourceLocation = name.getLocation()
}

data class IndexedRegNode(val reg: RegisterNode, val index: Node) : Node() {
    override fun getLocation(): SourceLocation = reg.getLocation()
}

data class FunctionCallNode(private val loc: SourceLocation, val name: String, val args: List<Node>): Node() {
    override fun getLocation(): SourceLocation = loc
}

data class AssemblerInstrNode(private val loc: SourceLocation, val instr: String, val args: List<Node>?): Node() {
    override fun getLocation(): SourceLocation = loc
}

data class DoWhileNode(
    private val loc: SourceLocation,
    val body: CompoundStmt,
    val cond: Node
) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class DataBlockNode(
        private val loc: SourceLocation,
        val type: DataType,
        val items: List<Node>
) : Node() {
    override fun getLocation(): SourceLocation = loc

    operator fun get(i: Int) = items[i]
    operator fun iterator(): Iterator<Node> = items.iterator()
    fun size() = items.size

//    override fun toString(): String = "$reg[$bit]"
}

data class SignedCastNode(
        private val loc: SourceLocation,
        val expr: Node
) : Node() {
    override fun getLocation(): SourceLocation = loc
}

data class SaveregsNode(private val loc: SourceLocation, val args: List<Node>, val body: Node): Node() {
    override fun getLocation(): SourceLocation = loc
}
