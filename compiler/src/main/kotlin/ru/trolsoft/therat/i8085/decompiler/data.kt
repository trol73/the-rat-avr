package ru.trolsoft.therat.i8085.decompiler

enum class VarType {
    BYTE, WORD, PTR
}
data class Procedure(
    val name: String,
    val offset: Int,
    val size: Int,
    val uses: Set<Int>
)

data class Variable(
    val name: String,
    val offset: Int,
    val uses: MutableSet<Int> = mutableSetOf(),
    var size: Int = 0,
    var usedAsByteCnt: Int = 0,
    var usedAsWordCnt: Int = 0,
    var usedAsPtrCnt: Int = 0
) {
    fun detectType(): VarType {
        return if (usedAsWordCnt > 0) VarType.WORD
        else if (usedAsByteCnt > 0) VarType.BYTE
        else VarType.PTR
    }

    fun renameTo(newName: String) = Variable(newName, offset, uses, size, usedAsByteCnt, usedAsWordCnt, usedAsPtrCnt)
}

data class Options(
    val romOffset: Int?,
    val entryPoints: List<Int>,
    val ignorePoints: List<Int>,
    val optimizeZero: Boolean,
    val optimizeIncDec: Boolean,
    val ratChains: Boolean
)