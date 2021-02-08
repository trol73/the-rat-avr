package ru.trolsoft.therat.avr

import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.parser.AssemblerInstrNode
import ru.trolsoft.therat.arch.avr.isAvrPairHigh
import ru.trolsoft.therat.arch.avr.isAvrPairLow
import ru.trolsoft.compiler.parser.RegisterGroupNode
import ru.trolsoft.compiler.parser.RegisterNode
import ru.trolsoft.therat.arch.avr.AvrCmd


fun RegisterGroupNode.isPair(): Boolean = regs.size == 2 && isAvrPairHigh(regs[0]) && isAvrPairLow(regs[1])
fun RegisterGroupNode.isR25R24(): Boolean = regs.size == 2 && regs[0].toLowerCase() == "r25" && regs[1].toLowerCase() == "r24"    // ADIW/SBIW
fun RegisterGroupNode.isX(): Boolean = isPair() && regs[0] == "r27"
fun RegisterGroupNode.isY(): Boolean = isPair() && regs[0] == "r29"
fun RegisterGroupNode.isZ(): Boolean = isPair() && regs[0] == "r31"
fun RegisterGroupNode.buildRegisterNode(i: Int) = RegisterNode(getLocation(), regs[i])
fun RegisterGroupNode.isHighPair(): Boolean = isX() || isY() || isZ() || isR25R24()

fun RegisterGroupNode.buildRegNumbers(): List<Int> {
    val res = mutableListOf<Int>()
    for (reg in regs) {
        val name = reg.toLowerCase()
        val num = when {
            name.startsWith('r') -> name.substring(1).toInt()
            name == "xl" -> 26
            name == "xh" -> 27
            name == "yl" -> 28
            name == "yh" -> 29
            name == "zl" -> 30
            name == "zh" -> 31
            else -> internalError()
        }
        res.add(num)
    }
    return res
}

fun AssemblerInstrNode.getInstructionSize(): Int = AvrCmd.getByName(this.instr)?.size() ?: -1
