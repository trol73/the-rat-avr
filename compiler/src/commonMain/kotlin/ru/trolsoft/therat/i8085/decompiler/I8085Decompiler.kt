package ru.trolsoft.therat.i8085.decompiler

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.buffered
import ru.trolsoft.compiler.utils.BinaryInputStream
import ru.trolsoft.compiler.utils.RkrInputStream
import ru.trolsoft.compiler.utils.RomInputStream
import ru.trolsoft.therat.arch.i8085.I8085Cmd
import ru.trolsoft.compiler.utils.hexWord


class I8085Decompiler(
    is8085: Boolean = false
) {
    private val disassembler = I8085Disassembler(is8085)
    private val out = DecompilerOutput(this)
    private val labels = mutableSetOf<Int>()
    private val extLabels = mutableSetOf<Int>()
    private val procedures = mutableMapOf<Int, Procedure>()
    private val variables = mutableMapOf<Int, Variable>()
    private val listing = mutableMapOf<Int, Instr>()
    private val possibleAddresses = mutableListOf<Int>()
    private val ignorePoints = mutableSetOf<Int>()

    private lateinit var src: BinaryInputStream

    private lateinit var options: Options

    fun loadFile(
        path: String,
        options: Options
    ): List<String> {
        val lowerPath = path.lowercase()
        val fileSource = SystemFileSystem.source(Path(path)).buffered()
        src = if (lowerPath.endsWith(".bin") || lowerPath.endsWith(".rom")) {
            RomInputStream(fileSource, options.romOffset ?: 0)
        } else {
            RkrInputStream(fileSource)
        }
        this.options = options
        //endAddress = src.getEndAddress()
        this.ignorePoints.addAll(ignorePoints)
        val entryPoints = options.entryPoints.ifEmpty { listOf(getStartAddress()) }
        return load(entryPoints)
    }

    private fun load(entryPoints: List<Int>): List<String> {
        for (ep in entryPoints) {
            processProcedure(ep)
        }

        for (addr in variables.keys) {
            val v = variables[addr] ?: continue
            when (v.detectType()) {
                VarType.BYTE -> variables[addr] = v.renameTo("byte_${hexWord(v.offset)}")
                VarType.WORD -> variables[addr] = v.renameTo("word_${hexWord(v.offset)}")
                else -> {}
            }
        }

        val out = out.produceOutput()
        if (options.ratChains) {
            simplifyOutput(out)
        }
        return out
    }

    private fun simplifyOutput(out: MutableList<String>) {
        var line = 0
        var prev: String? = null
        while (line < out.size) {
            val s = out[line]
            if (prev == null) {
                prev = s
                continue
            }
            if (isSimpleAssign(s) && isSimpleAssign(prev) && assignDest(prev) == assignSrc(s)) {
                out[line-1] = "\t" + assignDest(s) + " = " + out[line-1].trim()
                out.removeAt(line)
                prev = null
                continue
            }
            prev = s
            line++
        }
    }

    private fun isSimpleAssign(s: String) = s.startsWith("\t") && s.count { it == '='} == 1 && !s.contains("+=") && !s.contains("-=") && !s.contains("*=") && !s.contains("/=") && !s.contains("&=")&& !s.contains("|=")&& !s.contains("<<=")&& !s.contains(">>=")
    private fun assignSrc(s: String) = s.substringAfter("=").trim()
    private fun assignDest(s: String) = s.substringBefore("=").trim()

    fun printResult(out: List<String>) {
        for (s in out) {
            output(s)
        }
    }
    private fun output(s: String) = println(s)





    private fun ignoreRegion(address: Int, size: Int) {
        for (i in address until address + size) {
            ignorePoints.add(i)
        }
    }

    private fun processProcedure(offset: Int) {
        if (procedures.contains(offset)) {
            return
        }
        if (offset < src.getStartAddress() || offset > src.getEndAddress()) {
            return
        }
        src.seek(offset)

        val subroutines = mutableListOf<Int>()

        while (!src.eof()) {
            val addr = src.getAddress()
            val instr = disassembler.decompileNext(src.toRawSource()) ?: break
            listing[addr] = instr
            when (instr.cmd) {
                I8085Cmd.RST -> {
                    subroutines.add(instr.getArg1Int()!! * 8)
                }
                I8085Cmd.LDA, I8085Cmd.STA -> {
                    createOrUpdateVariable(instr.getArg1Int()!!, offset, VarType.BYTE)
                }
                I8085Cmd.LHLD, I8085Cmd.SHLD -> {
                    createOrUpdateVariable(instr.getArg1Int()!!, offset, VarType.WORD)
                }
                else -> {
                    if (instr.cmd == null) {
//                        for (a in offset .. addr) {
//                            listing.remove(a)
//                        }
//                        src.seek(offset)
                        return
                    }
                    if (instr.cmd.isJumpOrCall()) {
                        val destAddr = instr.getArg1Int()!!
                        if (destAddr == 0x300e) {
                            println()
                        }

                        if (destAddr.isExternalAddress()) {
                            extLabels.add(destAddr)
                        } else if (!ignorePoints.contains(destAddr)) {
                            subroutines.add(destAddr)
                        } else {
//                            labels.add(destAddr)
                        }
                    }
                }
            }

            if (instr.cmd == I8085Cmd.RET || instr.cmd == I8085Cmd.JMP || instr.cmd == I8085Cmd.RST) {
                break
            }
        }

        val endOffset = src.getAddress() - 1
        val uses = subroutines.filter { it !in offset..endOffset }.toSet()
        labels.addAll(subroutines)

        createProcedure(offset, src.getAddress() - offset, uses)
       // ignoreRegion(offset, src.getAddress() - offset)
        for (subProc in subroutines) {
            if (subProc < offset || subProc > endOffset) {
                processProcedure(subProc)
            }
        }
    }

    private fun Int.isExternalAddress() = this < src.getStartAddress() || this > src.getEndAddress()

    private fun createProcedure(offset: Int, size: Int, uses: Set<Int>) {
        val procName = if (offset == getStartAddress()) {
            "start"
        } else {
            "proc_" + hexWord(offset)
        }
        procedures[offset] = Procedure(procName, offset, size, uses)
    }

    private fun createOrUpdateVariable(offset: Int, usedFrom: Int, type: VarType): Variable {
        val name = "var_" + hexWord(offset)
        val v = variables[offset] ?: Variable(name, offset)
        v.uses.add(usedFrom)
        when (type) {
            VarType.BYTE -> v.usedAsByteCnt++
            VarType.WORD -> v.usedAsWordCnt++
            else -> v.usedAsPtrCnt++
        }
        if (!variables.containsKey(offset)) {
            variables[offset] = v
        }
        return v
    }

    fun handleInstr(instr: Instr, instrOffset: Int): String {
        if (instr.cmd == null) {
            return "<null>"
        }
        if (instr.cmd.isJumpOrCall()) {
            val offset = instr.getArg1Int()!!
            val proc = procedures[offset]
            val lbl = proc?.name ?: if (offset.isExternalAddress()) "proc_${hexWord(offset)}" else "lbl_${hexWord(offset)}"
            return "${instr.cmd.name.lowercase()}\t$lbl"
        }
        return when (instr.cmd) {
            I8085Cmd.MOV, I8085Cmd.LXI -> {
                if (instr.cmd == I8085Cmd.LXI && instr.arg1 == "HL") {
                    possibleAddresses.add(instr.getArg2Int()!!)
                }
                if (instr.cmd == I8085Cmd.MOV && instr.arg1 == "M") {
                    "mem[HL] = ${instr.arg2}"
                } else if (instr.cmd == I8085Cmd.MOV && instr.arg2 == "M") {
                    "${instr.arg1} = mem[HL]"
                } else {
                    "${instr.arg1} = ${instr.arg2}"
                }
            }
            I8085Cmd.MVI -> {
                if (instr.arg1 == "A" && instr.arg2 == "0" && !options.optimizeZero) {
                    instr.toString()
                } else {
                    "${instr.arg1} = ${instr.arg2}"
                }
            }
            I8085Cmd.XRA -> {
                if (instr.arg1 == "A") "A = 0" else "A ^= ${instr.arg1}"
            }
            I8085Cmd.ORA -> "A |= ${instr.arg1}"
            I8085Cmd.ANA -> "A &= ${instr.arg1}"
            I8085Cmd.ANI -> "A &= ${instr.arg1}"
            I8085Cmd.ORI -> "A |= ${instr.arg1}"
            I8085Cmd.XRI -> "A ^= ${instr.arg1}"
            I8085Cmd.RLC -> "A <<= 1"
            I8085Cmd.RRC -> "A >>= 1"
            I8085Cmd.DCR, I8085Cmd.DCX -> "${instr.arg1}--"
            I8085Cmd.INR, I8085Cmd.INX -> "${instr.arg1}++"
            I8085Cmd.LDA -> {
                val mem = variables[instr.getArg1Int()]?.name ?: "mem[${instr.arg1}]"
                "A = $mem"
            }
            I8085Cmd.STA -> {
                val mem = variables[instr.getArg1Int()]?.name ?: "mem[${instr.arg1}]"
                "$mem = A"
            }
            I8085Cmd.LDAX -> "A = mem[${instr.arg1}]"
            I8085Cmd.STAX -> "mem[${instr.arg1}] = A"
            I8085Cmd.ADD, I8085Cmd.ADI -> {
                if (instr.arg1 == "0x01" && !options.optimizeIncDec) {
                    instr.toString()
                } else {
                    "A += ${instr.arg1}"
                }
            }
            I8085Cmd.SUB, I8085Cmd.SUI -> {
                if (instr.arg1 == "0x01" && !options.optimizeIncDec) {
                    instr.toString()
                } else {
                    "A -= ${instr.arg1}"
                }
            }
            I8085Cmd.DAD -> "HL += ${instr.arg1}"
            I8085Cmd.LHLD -> {
                val mem = variables[instr.getArg1Int()]?.name ?: "mem[${instr.arg1}]"
                "HL = $mem"
            }
            I8085Cmd.SHLD -> {
                val mem = variables[instr.getArg1Int()]?.name ?: "mem[${instr.arg1}]"
                "$mem = HL"
            }
            I8085Cmd.SPHL -> "SP = HL"
            I8085Cmd.IN -> "A = port[${instr.arg1}]"
            I8085Cmd.OUT -> "port[${instr.arg1}] = A"
            I8085Cmd.CMA -> "A = ~A"
            else -> instr.toString()
        }
    }

    internal fun isPossibleAddress(v: Int) = possibleAddresses.contains(v)
    internal fun getVariables(): Map<Int, Variable> = variables
    internal fun getProcedure(offset: Int): Procedure? = procedures[offset]
    internal fun getExtLabels(): Set<Int> = extLabels
    internal fun getLabels(): Set<Int> = labels
    internal fun getStartAddress() = src.getStartAddress()
    internal fun getEndAddress() = src.getEndAddress()
    internal fun getInstructionFrom(offset: Int) = listing[offset]
    internal fun getByteFrom(offset: Int) = src.getByte(offset)
}

fun I8085Cmd.isJumpOrCall() = when (this) {
    I8085Cmd.JMP, I8085Cmd.JM, I8085Cmd.JP, I8085Cmd.JC, I8085Cmd.JZ, I8085Cmd.JNC, I8085Cmd.JNZ, I8085Cmd.JPE, I8085Cmd.JPO,
    I8085Cmd.CALL, I8085Cmd.CZ, I8085Cmd.CC, I8085Cmd.CNZ, I8085Cmd.CNC, I8085Cmd.CP, I8085Cmd.CM, I8085Cmd.CPE, I8085Cmd.CPO -> true
    else -> false
}

