package ru.trolsoft.therat.arch

import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.Node

interface AsmArg

class ArgNumber(
        val value: Int
) : AsmArg {

    operator fun compareTo(i: Int): Int = value - i
    override fun toString(): String = value.toString()
}

class ArgLabel(
        val name: String,
        val localName: String,
        val location: SourceLocation,
        val resolver: (currentOffset: Int, labelOffset: Int) -> Int,
        var resolved: Int? = null
) : AsmArg {
    override fun toString(): String {
        return if (name == localName) {
            name
        } else {
            "$name ($localName)"
        }
    }
}

class DeferredNumberArg(
        val node: Node,
        val resolver: (arg: DeferredNumberArg, labelResolver: LabelResolver) -> Int,
        var resolved: Int? = null
) : AsmArg

class ArgExtern(
        val value: String
) : AsmArg {
    override fun toString(): String = value
}

interface LabelResolver {
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
        if (javaClass != other?.javaClass) return false

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

class AsmCompileException(message: String, cause: Throwable? = null): Exception(message, cause)

