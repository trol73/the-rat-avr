package ru.trolsoft.therat.avr.compiler.bus

import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.AssemblerInstrNode


private const val EMPTY = -1

data class BusOperation(
    val port: String,
    val dataReg: Int,    // при записи берём отсюда данные, при чтении сохраняем их сюда
    val tempReg: Int,    // временный регистр для работы с портом
    val map: BitMap,     // отображает номера битов tempReg в биты dataReg
    val availableTempRegisters: List<Int>
)

class BitMap {
    private val bits = arrayOf(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY)

    operator fun get(index: Int): Int? {
        val b = bits[index]
        return if (b in 0..7) b else null
    }

    operator fun set(index: Int, bit: Int) {
        bits[index] = bit
    }

    operator fun contains(index: Int): Boolean = get(index) != null

    fun size(): Int {
        var res = 0
        for (i in 0..7) {
            if (contains(i)) {
                res++
            }
        }
        return res
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("|")
        for (i in 0..7) {
            if (contains(i)) {
                sb.append(' ')
                sb.append(i)
            } else {
                sb.append(" -")
            }
            sb.append(" |")
        }
        return sb.toString()
    }

}


interface Algo {
    /**
     * Стоимость операции в количестве инструкций
     */
    fun cost(op: BusOperation): Int

    /**
     * Проверяет, возможно ли хотя бы частично обработать операцию
     */
    fun canAccept(op: BusOperation): Boolean

    /**
     * Возвращает операцию, которую останется выполнить, если алгоритм не позволяет обработать сразу все биты.
     * Если операция будет выполнена полностью, вернет null
     */
    fun remains(op: BusOperation): BusOperation?

    /**
     * Возвращает необходимое количество дополнительных временных регистров
     */
    fun tempRegsCost(): Int

    fun process(op: BusOperation, loc: SourceLocation): List<AssemblerInstrNode>
}