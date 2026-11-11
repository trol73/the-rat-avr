package ru.trolsoft.compiler.generator

import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.utils.hexByte
import ru.trolsoft.compiler.utils.hexDword

class ListingRec(
    val location: SourceLocation,
    val offset: Int,
    var asmCode: String,
    val dump: ByteArray? = null
) {
    override fun toString() =
        "${location.fileName}:${location.line}\t${hexDword(offset)}: ${if (dump == null || dump.isEmpty()) "" else hexDumpStr(9)}$asmCode"

    fun hexDumpStr(len: Int): String {
        if (dump == null) {
            return SPACES[len]
        }
        val sb = StringBuilder()
        for (b in dump) {
            sb.append(hexByte(b.toInt()))
            sb.append(' ')
        }
        sb.append(SPACES[len-dump.size*3])
        return sb.toString()
    }

    companion object {
        val SPACES = arrayOf("", " ", "  ", "   ", "    ", "     ", "      ", "       ", "        ", "         ", "          ", "           ", "            ")
    }
}