package ru.trolsoft.therat.avr

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.AssemblerInstrNode
import ru.trolsoft.compiler.parser.NumberNode
import ru.trolsoft.therat.arch.avr.isAvrPairHigh
import ru.trolsoft.therat.arch.avr.isAvrPairLow
import ru.trolsoft.compiler.parser.RegisterGroupNode
import ru.trolsoft.compiler.parser.RegisterNode
import ru.trolsoft.therat.arch.avr.AvrCmd


fun RegisterGroupNode.isPair(): Boolean = regs.size == 2 && isAvrPairHigh(regs[0]) && isAvrPairLow(regs[1])
fun RegisterGroupNode.isR25R24(): Boolean = regs.size == 2 && regs[0].lowercase() == "r25" && regs[1].lowercase() == "r24"    // ADIW/SBIW
fun RegisterGroupNode.isX(): Boolean = isPair() && (regs[0] == "r27" || regs[0].lowercase() == "xh")
fun RegisterGroupNode.isY(): Boolean = isPair() && (regs[0] == "r29" || regs[0].lowercase() == "yh")
fun RegisterGroupNode.isZ(): Boolean = isPair() && (regs[0] == "r31" || regs[0].lowercase() == "zh")
fun RegisterGroupNode.buildRegisterNode(i: Int) = RegisterNode(getLocation(), regs[i])
fun RegisterGroupNode.isHighPair(): Boolean = isX() || isY() || isZ() || isR25R24()

fun RegisterGroupNode.buildRegNumbers(): List<Int> {
    val res = mutableListOf<Int>()
    for (reg in regs) {
        val name = reg.lowercase()
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

fun NumberNode.extractByteNode(offset: Int): NumberNode {
    val byte = (value shr (offset*8)) and 0xff
    return NumberNode(getLocation(), byte)
}

fun RegisterGroupNode.getNumber(i: Int): Int = getRegisterNumber(get(i), getLocation())

fun RegisterGroupNode.isWordPair(): Boolean {
    if (size() != 2) {
        return false
    }
    val hi = getNumber(0)
    val lo = getNumber(1)
    return (hi % 2 == 1) && (hi - lo == 1)
}

fun RegisterNode.getNumber(): Int = getRegisterNumber(reg, getLocation())

private fun getRegisterNumber(reg: String, loc: SourceLocation): Int =
    when {
        reg[0] == 'r' || reg[0] == 'R' -> reg.substring(1).toInt()
        reg == "XL" -> 26
        reg == "XH" -> 27
        reg == "YL" -> 28
        reg == "YH" -> 29
        reg == "ZL" -> 30
        reg == "ZH" -> 31
        else -> throw CompilerException("Internal error, wrong register $reg", loc)
    }

