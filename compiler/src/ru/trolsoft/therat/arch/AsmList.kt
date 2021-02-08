package ru.trolsoft.therat.arch

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.generator.BinaryOutput
import ru.trolsoft.compiler.lexer.SourceLocation

class AsmList {
    val blocks = mutableListOf<CodeBlock>()
    var offset = 0
        set(value) {
            field = value
            val block = CodeBlock(value)
            currentBlock = block
            blocks.add(block)
        }
    private var currentBlock: CodeBlock? = null

    fun addCmd(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg) {
        val block = currentBlock ?: CodeBlock(offset)
        if (currentBlock == null) {
            blocks.add(block)
            currentBlock = block
        }
        offset += block.add(location, cmd, *args)
    }

    fun addByte(b: Int, location: SourceLocation) = addCmd(location, RawByteCmd(b))
    fun addWord(b: Int, location: SourceLocation) = addCmd(location, RawWordCmd(b))
    fun addDword(b: Int, location: SourceLocation) = addCmd(location, RawDwordCmd(b))

    fun addByte(b: DeferredNumberArg, location: SourceLocation) = addCmd(location, DeferredRawByteCmd(), b)
    fun addWord(w: DeferredNumberArg, location: SourceLocation) = addCmd(location, DeferredRawWordCmd(), w)
    fun addDword(d: DeferredNumberArg, location: SourceLocation) = addCmd(location, DeferredRawDwordCmd(), d)
}

private class RawByteCmd(val b: Int): AsmCmd {
    override fun size(vararg args: AsmArg): Int = 1
    override fun compile(out: CompilerOutput, vararg args: AsmArg) = out.addByte(b)
    override fun str(vararg args: AsmArg): String = b.toString()
}

private class RawWordCmd(val w: Int): AsmCmd {
    override fun size(vararg args: AsmArg): Int = 2
    override fun compile(out: CompilerOutput, vararg args: AsmArg) = out.addWord(w)
    override fun str(vararg args: AsmArg): String = w.toString()
}

private class RawDwordCmd(val dw: Int): AsmCmd {
    override fun size(vararg args: AsmArg): Int = 4
    override fun compile(out: CompilerOutput, vararg args: AsmArg) = out.addDword(dw)
    override fun str(vararg args: AsmArg): String = dw.toString()
}


private class DeferredRawByteCmd: AsmCmd {
    override fun size(vararg args: AsmArg): Int = 1
    override fun compile(out: CompilerOutput, vararg args: AsmArg) {
        val arg = args[0] as DeferredNumberArg
        out.addByte(arg.resolved!!)
    }
    override fun str(vararg args: AsmArg): String {
        val arg = args[0] as DeferredNumberArg
        return arg.resolved!!.toString()
    }
}

private class DeferredRawWordCmd: AsmCmd {
    override fun size(vararg args: AsmArg): Int = 2
    override fun compile(out: CompilerOutput, vararg args: AsmArg) {
        val arg = args[0] as DeferredNumberArg
        out.addWord(arg.resolved!!)
    }
    override fun str(vararg args: AsmArg): String {
        val arg = args[0] as DeferredNumberArg
        return arg.resolved!!.toString()
    }}

private class DeferredRawDwordCmd: AsmCmd {
    override fun size(vararg args: AsmArg): Int = 4
    override fun compile(out: CompilerOutput, vararg args: AsmArg) {
        val arg = args[0] as DeferredNumberArg
        out.addDword(arg.resolved!!)
    }
    override fun str(vararg args: AsmArg): String {
        val arg = args[0] as DeferredNumberArg
        return arg.resolved!!.toString()
    }
}



class CodeBlock(val offset: Int) {
    val list = mutableListOf<AsmCall>()
    var bytesLength = 0

    fun size(): Int = list.size
    operator fun get(i: Int) = list[i]
    operator fun iterator() = list.iterator()

    fun add(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg): Int {
        val cmdSize = cmd.size(*args)
        bytesLength += cmdSize
        list.add(AsmCall(cmd, args, location))
        return cmdSize
    }

    fun compile(out: BinaryOutput) {
        out.gotoAddress(offset)
        for (instr in list) {
            try {
                instr.cmd.compile(out, *instr.args)
            } catch (e: AsmCompileException) {
                throw CompilerException(e.message!!, instr.location, e)
            }
        }

    }

}

