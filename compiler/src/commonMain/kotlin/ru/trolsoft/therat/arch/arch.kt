package ru.trolsoft.therat.arch

import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.Node
import ru.trolsoft.compiler.parser.ResolvedExternProc

interface AsmArg

open class ArgNumber(
    val value: Int
) : AsmArg {

    operator fun compareTo(i: Int): Int = value - i
    override fun toString(): String = value.toString()
}

class ArgNumberWithRadix(value: Int, private val radix: Int) : ArgNumber(value) {
    override fun toString(): String {
        if (value == 0) {
            return "0"
        }
        return when (radix) {
            16 -> {
                "0x" + value.toString(16).uppercase()
            }

            2 -> {
                "0b" + value.toString(2)
            }

            else -> {
                super.toString()
            }
        }
    }
}

class ArgLabel(
    val name: String,
    val localName: String,
    val location: SourceLocation,
    val resolver: (currentOffset: Int, labelOffset: Int) -> Int,
    var resolved: Int? = null
) : AsmArg {
    override fun toString(): String {
        return if (name == localName || (localName.endsWith(name) && name.startsWith(localName.removeSuffix(name)))) {
            name
        } else {
            "$localName ; $name"
        }
    }
}

class DeferredNumberArg(
    val node: Node,
    val resolver: (arg: DeferredNumberArg, labelResolver: LabelResolver) -> Int,
    var resolved: Int? = null,
    var resolvedStr: String? = null
) : AsmArg {
    override fun toString(): String =
        resolved?.toString(16) ?: resolvedStr ?:
            if (node is ResolvedExternProc) node.name else "<???>"
}

class ArgExtern(
    val value: String
) : AsmArg {
    override fun toString(): String = value
}

class ArgExternWithOffset(
    val value: String,
    val offset: Int
) : AsmArg {
    override fun toString(): String {
        return if (offset >= 0) {
            "$value+$offset"
        } else {
            "$value${offset}"
        }
    }
}

fun interface LabelResolver {
    fun resolve(name: String): Int?
}

interface CompilerOutput {
    fun addByte(v: Int)
    fun addWord(v: Int)
    fun addDword(v: Int)
}

interface AsmCmd {
    fun size(vararg args: AsmArg): Int
    fun compile(out: CompilerOutput, vararg args: AsmArg)
    fun str(vararg args: AsmArg): String
}

data class AsmCall(
    val cmd: AsmCmd,
    val args: Array<out AsmArg>,
    val location: SourceLocation
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AsmCall

        if (cmd != other.cmd) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cmd.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }

    fun size(): Int = cmd.size(*args)
}

class AsmCompileException(message: String, cause: Throwable? = null) : Exception(message, cause)

