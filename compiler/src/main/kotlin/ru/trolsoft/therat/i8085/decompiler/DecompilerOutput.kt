package ru.trolsoft.therat.i8085.decompiler

import ru.trolsoft.compiler.utils.hexByte
import ru.trolsoft.compiler.utils.hexWord

class DecompilerOutput(
    private val decompiler: I8085Decompiler
) {
    fun produceOutput(): MutableList<String> {
        val out = mutableListOf<String>()
        outVariables(decompiler.getVariables(), out)

        for (extProc in decompiler.getExtLabels()) {
            out.add("extern proc proc_${hexWord(extProc)}() absolute 0x${hexWord(extProc)}")
        }
        if (decompiler.getExtLabels().isNotEmpty()) {
            out.add("")
        }
        var offset = decompiler.getStartAddress()
        if (offset != 0) {
            out.add("\$org(0x${hexWord(offset)})")
            out.add("")
        }
        var proc: Procedure? = null
        val dataBlock = mutableListOf<Int>()
        var dataBlockAddress = 0
        while (offset < decompiler.getEndAddress()) {
            if (proc != null && offset >= proc.offset + proc.size) {
                out.add("}")
                out.add("")
                proc = null
            }


            val newProc = decompiler.getProcedure(offset)
            if (newProc != null) {
                if (dataBlock.isNotEmpty()) {
                    outDataBlock(dataBlock, dataBlockAddress, out)
                    dataBlock.clear()
                }
                if (proc != null) {
                    out.add("@${newProc.name}:")
                } else {
                    proc = newProc
                    out.add("proc ${proc.name}() {")
                }
            } else if (decompiler.getLabels().contains(offset)) {
                out.add("lbl_${hexWord(offset)}:")
            }

            val instr = decompiler.getInstructionFrom(offset)
            if (instr == null) {
                if (dataBlock.isEmpty()) {
                    dataBlockAddress = offset
                }
                dataBlock.add(decompiler.getByteFrom(offset))
                offset++
            } else {
                out.add("\t" + decompiler.handleInstr(instr, offset))
                offset += instr.sizeInBytes
            }
        }
        if (proc != null) {
            out.add("}")
        }
        if (dataBlock.isNotEmpty()) {
            outDataBlock(dataBlock, dataBlockAddress, out)
        }
        return out
    }
    private fun outDataBlock(bytes: List<Int>, address: Int, out: MutableList<String>) {
        val sb = StringBuilder()
        var len = 0
        sb.append("\t")
        var remains = bytes.size
        var startAddress = address
        var currentAddress = address
        val ascii = StringBuilder()
        var isAscii = true
        var asciiOpen = false

        for (b in bytes) {
            if (b in 0x20..126) {
                if (!asciiOpen) {
                    asciiOpen = true
                    if (ascii.isNotEmpty()) {
                        ascii.append(", ")
                    }
                    ascii.append('"')
                }
                ascii.append(b.toChar())
            } else {
                if (b != 0x00 && b != 0x0A && b != 0x0D) {
                    isAscii = false
                }
                if (asciiOpen) {
                    ascii.append('"')
                    asciiOpen = false
                }
                ascii.append(", 0x").append(hexByte(b))
            }
            sb.append("0x${hexByte(b)}, ")
            currentAddress++
            remains--
            if (++len == 32 && !decompiler.isPossibleAddress(currentAddress)) {
                len = 0
                sb.append("\n\t")
            }
            if (decompiler.isPossibleAddress(currentAddress) || remains == 0) {
                if (asciiOpen) {
                    ascii.append('"')
                    asciiOpen = false
                }
                out.add("data_${hexWord(startAddress)}:")
                if (isAscii) {
                    out.add("byte[] {")
                    out.add("\t$ascii")
                    out.add("}")
                    out.add("")
                } else {
                    out.add("byte[] {")
                    out.add("${sb.removeSuffix(",").removeSuffix(", ")}")
                    out.add(";\t$ascii")
                    out.add("}")
                    out.add("")
                }
                sb.clear()
                ascii.clear()
                isAscii = true
                asciiOpen = false
                sb.append("\t")
                startAddress = currentAddress
                len = 0
            }
        }
//        if (isAscii) {
//            ignoreRegion(address, bytes.size)
//        }
//        output("data_${hexWord(startAddress)}:")
//        output("byte[] {\n${sb.removeSuffix(", ")}\n}\n")
    }

    private fun outVariables(vars: Map<Int, Variable>, out: MutableList<String>) {
        val offsets = vars.keys.sorted()
        for (offset in offsets) {
            val v = vars[offset] ?: continue
            val type = if (v.detectType() == VarType.WORD) "word" else "byte"
            val addr = "0x${hexWord(offset)}"
            out.add("var ${v.name}: $type absolute $addr")
        }
        out.add("")
    }

}