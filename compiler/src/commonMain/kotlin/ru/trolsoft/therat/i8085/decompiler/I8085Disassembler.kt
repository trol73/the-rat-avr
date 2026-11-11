package ru.trolsoft.therat.i8085.decompiler

import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.EOFException
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.utils.UnexpectedEofException
import ru.trolsoft.therat.arch.ArgNumber
import ru.trolsoft.therat.arch.AsmCompileException
import ru.trolsoft.therat.arch.CompilerOutput
import ru.trolsoft.therat.arch.i8085.ArgReg
import ru.trolsoft.therat.arch.i8085.ArgRegPair
import ru.trolsoft.therat.arch.i8085.ArgType
import ru.trolsoft.therat.arch.i8085.I8085Cmd
import ru.trolsoft.compiler.utils.hexByte
import ru.trolsoft.compiler.utils.hexWord


data class Instr(
    val cmd: I8085Cmd?,
    val sizeInBytes: Int,
    val arg1: String? = null,
    val arg2: String? = null,
) {
    override fun toString(): String =
        when {
            cmd == null -> ".DB $arg1"
            arg2 != null -> "${cmd.name.lowercase()}\t$arg1, $arg2"
            arg1 != null -> "${cmd.name.lowercase()}\t$arg1"
            else -> cmd.name.lowercase()
        }

    internal fun withArg(v: Int) =
        if (arg1 == null) {
            Instr(cmd, sizeInBytes, numToStr(v))
        } else {
            Instr(cmd, sizeInBytes, arg1, numToStr(v))
        }

    private fun numToStr(v: Int) = if (v == 0) "0" else "0x${if (sizeInBytes == 3) hexWord(v) else hexByte(v)}"

    fun getArg1Int(): Int? =
        when {
            arg1 == null -> null
            arg1.startsWith("0x") -> arg1.substring(2).toInt(16)
            else -> arg1.toInt(16)
        }

    fun getArg2Int(): Int? =
        when {
            arg2 == null -> null
            arg2.startsWith("0x") -> arg2.substring(2).toInt(16)
            else -> arg2.toInt(16)
        }

}


private class GetFirstByteCompilerOutput : CompilerOutput {
    private var result: Int? = null
    override fun addByte(v: Int) {
        if (result == null) {
            result = v
        }
    }

    override fun addWord(v: Int) {}

    override fun addDword(v: Int) {}

    fun getByte(): Int? {
        val res = result
        result = null
        return res
    }

}

class I8085Disassembler(
    val i8085: Boolean = false
) {
    private val decodes = buildCodesMap()

    fun decompileNext(data: RawSource): Instr? {
        val source = data.buffered()
        return try {
            val b0 = source.readByte().toInt() and 0xFF
            val instr = decodes[b0] ?: return Instr(null, 1, hexByte(b0))
            when (instr.sizeInBytes) {
                2 -> {
                    val b = source.readByte().toInt() and 0xFF
                    instr.withArg(b)
                }
                3 -> {
                    val lo = source.readByte().toInt() and 0xFF
                    val hi = source.readByte().toInt() and 0xFF
                    val w = (hi shl 8) + lo
                    instr.withArg(w)
                }
                else -> instr
            }
        } catch (_: EOFException) {
            throw UnexpectedEofException()
        }
    }

    private fun buildCodesMap(): Map<Int, Instr> {
        val map = mutableMapOf<Int, Instr>()
        val output = GetFirstByteCompilerOutput()
        val arg0 = ArgNumber(0)
        for (cmd in I8085Cmd.entries) {
            if (!i8085 && (cmd == I8085Cmd.SIM || cmd == I8085Cmd.RIM)) {
                continue
            }
            when (cmd.arg1) {
                null -> {
                    cmd.compile(output)
                    val code = output.getByte()!!
                    map[code] = Instr(cmd, cmd.size())
                }
                ArgType.LABEL_ABS, ArgType.CONST -> {
                    if (cmd == I8085Cmd.RST) {
                        for (i in 0..7) {
                            val a = ArgNumber(i)
                            cmd.compile(output, a)
                            val code = output.getByte()!!
                            map[code] = Instr(cmd, cmd.size(a), i.toString())
                        }
                    } else {
                        cmd.compile(output, arg0)
                        val code = output.getByte()!!
                        map[code] = Instr(cmd, cmd.size(arg0))
                    }
                }
                ArgType.REG -> {
                    for (reg in ArgReg.entries) {
                        when (cmd.arg2) {
                            null -> {
                                cmd.compile(output, reg)
                                val code = output.getByte()!!
                                map[code] = Instr(cmd, cmd.size(reg), reg.name)
                            }
                            ArgType.REG -> {
                                for (reg2 in ArgReg.entries) {
                                    cmd.compile(output, reg, reg2)
                                    val code = output.getByte()!!
                                    if (reg2 == reg && reg.name == "M") {   // skip 'MOV M, M' == 'HLT'
                                        continue
                                    }
                                    map[code] = Instr(cmd, cmd.size(reg, reg2), reg.name, reg2.name)
                                }
                            }
                            ArgType.CONST -> {
                                cmd.compile(output, reg, arg0)
                                val code = output.getByte()!!
                                map[code] = Instr(cmd, cmd.size(reg, arg0), reg.name)
                            }
                            else -> {
                                internalError()
                            }
                        }
                    }
                }
                ArgType.PAIR -> {
                    if (cmd.arg2 == null) {
                        for (pair in ArgRegPair.entries) {
                            try {
                                cmd.compile(output, pair)
                            } catch (_: AsmCompileException) {
                                continue
                            }
                            val code = output.getByte()!!
                            map[code] = Instr(cmd, cmd.size(pair), pair.name)
                        }
                    } else if (cmd.arg2 == ArgType.CONST) {
                        for (pair in ArgRegPair.entries) {
                            try {
                                cmd.compile(output, pair, arg0)
                            } catch (_: AsmCompileException) {
                                continue
                            }
                            val code = output.getByte()!!
                            map[code] = Instr(cmd, cmd.size(pair, arg0), pair.name)
                        }
                    } else {
                        internalError()
                    }
                }
            }
        }
        return map
    }
}