package ru.trolsoft.compiler.generator

import ru.trolsoft.therat.arch.CompilerOutput

enum class Endian {
    LITTLE,
    BIG
}


class MemoryPage(
    val startAddress: Int,
    var size: Int,
    var data: ByteArray?
) {
    fun alloc(fillByte: Int?) {
        data = if (fillByte != null) {
            ByteArray(size) { fillByte.toByte() }
        } else {
            ByteArray(size)
        }
    }

    operator fun get(offset: Int) = data!![offset].toInt() and 0xff
    operator fun iterator(): ByteIterator = data!!.iterator()

}


class BinaryOutput(
        private val endian: Endian,
        val pages: MutableList<MemoryPage> = mutableListOf(),
        var currentPage: MemoryPage? = null,
        var currentOffset: Int = 0
): CompilerOutput {
    private fun getPage(addr: Int): MemoryPage? {
        for (page in pages) {
            if (page.startAddress <= addr && page.startAddress + page.size >= addr) {
                return page
            }
        }
        return null
    }

    fun gotoAddress(addr: Int): MemoryPage {
        val page = getPage(addr)
        return if (page == null) {
            val newPage = MemoryPage(addr, 0, null)
            currentPage = newPage
            currentOffset = 0
            pages.add(newPage)
            newPage
        } else {
            currentPage = page
            currentOffset = addr - page.startAddress
            page
        }
    }

    override fun addByte(v: Int) {
        assert(v in 0..0xff)
        val page = currentPage!!
        assert(currentOffset < page.size)
        page.data!![currentOffset] = v.toByte()
        currentOffset++
    }

    override fun addWord(v: Int) {
        if (v < 0 || v > 0xffff) {
            print("!!! $v\n")
        }
        assert(v in 0..0xffff)
        if (endian == Endian.LITTLE) {
            addByte(v and 0xff)
            addByte(v shr 8)
        } else {
            addByte(v shr 8)
            addByte(v and 0xff)
        }
    }

    override fun addDword(v: Int) {
        if (endian == Endian.LITTLE) {
            addByte(v and 0xff)
            addByte(v shr 8 and 0xff)
            addByte(v shr 16 and 0xff)
            addByte(v shr 24 and 0xff)
        } else {
            addByte(v shr 24 and 0xff)
            addByte(v shr 16 and 0xff)
            addByte(v shr 8 and 0xff)
            addByte(v and 0xff)
        }
    }

    fun reserve(bytes: Int) {
        val page = currentPage ?: gotoAddress(0)
        if (currentOffset == page.size) {
            page.size += bytes
            currentOffset += bytes
        } else {
            for (i in 0 until bytes) {
                reserveByte(page)
            }
        }
    }

    private fun reserveByte(page: MemoryPage) {
        if (currentOffset >= page.size) {
            page.size++
        }
        currentOffset++
    }

    fun allocatePages(fillByte: Int? = null) {
        for (p in pages) {
            p.alloc(fillByte)
        }
        pages.sortBy { it.startAddress }
    }
}
