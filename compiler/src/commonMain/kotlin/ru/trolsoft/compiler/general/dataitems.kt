package ru.trolsoft.compiler.general

import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.parser.Node
import kotlin.math.max

data class Var (
    val name: String,
    val dataType: DataType,
    val arraySize: Int?,
    val offset: Int,
    val structName: String?
) {
    fun ramSize(): Int =
        if (dataType != DataType.STRUCTURED) {
            dataType.ramSize * (arraySize ?: 1)
        } else {
            internalError()
        }
    }


data class Use (
    val name: String,
    val reg: Node,
    val capture: Boolean
)

data class CycleBlock (
    val startLabel: String,
    val endLabel: String,
)

enum class StructUnion {
    STRUCT,
    UNION
}


data class StructuredData(
    val type: StructUnion,
    val name: String,
    val items: List<DataItem>
) {
    fun getSize(): Int {
        var size = 0
        for (itm in items) {
            when (type) {
                StructUnion.STRUCT -> size += itm.size
                StructUnion.UNION -> size = max(size, itm.size)
            }
        }
        return size
    }

    fun getOffset(name: String): Int {
        if (type == StructUnion.UNION) {
            return 0
        }
        var offset = 0
        for (itm in items) {
            if (itm.name == name) {
                return offset
            }
            offset += itm.size
        }
        return -1
    }

    fun getItem(name: String): DataItem? {
        for (itm in items) {
            if (itm.name == name) {
                return itm
            }
        }
        return null
    }

    fun getSize(name: String): Int = getItem(name)?.size ?: -1
}

sealed class DataItem {
    abstract val name: String
    abstract val size: Int
}

data class PrimitiveItem(
    override val name: String,
    override val size: Int,
    val dataType: DataType,
): DataItem()

data class StructItem(
    override val name: String,
    override val size: Int,
    val structuredData: StructuredData,
): DataItem()

data class PointerItem(
    override val name: String,
    override val size: Int,
    val source: DataItem,
): DataItem()

data class ArrayItem(
    override val name: String,
    override val size: Int,
    val source: DataItem,
    val arraySize: Int
): DataItem()
