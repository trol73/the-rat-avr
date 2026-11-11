package ru.trolsoft.compiler.generator

import kotlinx.io.RawSink
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.therat.arch.*
import ru.trolsoft.compiler.utils.hexByte
import ru.trolsoft.compiler.utils.hexDword
import ru.trolsoft.compiler.utils.hexWord

private data class ListingItem (
    val location: SourceLocation,
    val offset: Int,
    val rec: ListingRec?,
    val cmd: AsmCmd?,
    val args: List<AsmArg>?,
    val directive: String?
)
open class ListingGenerator(
    private val binaryGenerator: BinaryFileGenerator?,
    val handler: (ListingRec) -> Unit
): Generator(), LabelResolver {
    private val listing = mutableListOf<ListingItem>()
    private var offset: Int = 0
    private val emptyByteArray = ByteArray(0)
    private val dumpOutput = DumpOutput()
    private var currentProcName: String? = null
    private var currentProcListingRec: ListingRec? = null

    private fun add(location: SourceLocation, cmd: AsmCmd? = null, args: List<AsmArg>? = null) {
        listing.add(ListingItem(location, offset, null, cmd, args, null))
    }

    private fun add(location: SourceLocation, rec: ListingRec) {
        listing.add(ListingItem(location, offset, rec, null, null, null))
    }

    private fun add(location: SourceLocation, directive: String, arg: AsmArg) {
        listing.add(ListingItem(location, offset, null, null, listOf(arg), directive))
    }
    override fun addCommand(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg) {
//        if (offset % 2 != 0) {
//            offset++
//        }
        add(location, cmd, args.toList())
//        var s = cmd.str(*args)
//        val cmdName = s.substringBefore('\t')
//        if (cmdName != s && cmdName.length < 7) {
//            s = cmdName + SPACES[7 - cmdName.length] + s.substring(cmdName.length + 1)
//        }
//        val line = ListingRec(location, offset, s, commandDump(cmd, *args))
//
//        handler(line)
        offset += cmd.size(*args)
    }

    override fun addLabel(name: String, location: SourceLocation) {
        if (name.startsWith("?probe_")) {
            return
        }
        // Track procedure labels for size calculation
        if (currentProcName == name) {
            val rec = ListingRec(location, offset, "$name:")
            currentProcListingRec = rec
            add(location, rec)
            currentProcName = null
        } else {
            add(location, ListingRec(location, offset, "$name:"))
        }
    }

    override fun write(sink: RawSink) {}

    override fun org(offset: Int) {
        this.offset = offset
    }

    override fun addByte(b: Int, location: SourceLocation) {
        //handler(ListingRec(location, offset, ".db 0x" + hexByte(b)))
        add(location, ListingRec(location, offset, ".db 0x" + hexByte(b)))
        offset++
    }

    override fun addByte(b: DeferredNumberArg, location: SourceLocation) {
        add(location, ".db", b)
//        if (b.resolved != null) {
//            handler(ListingRec(location, offset, ".db 0x" + hexByte(b.resolved!!)))
//        } else {
//            handler(ListingRec(location, offset, ".db ??"))
//        }
        offset++
    }

    override fun addWord(w: Int, location: SourceLocation) {
        add(location, ListingRec(location, offset, ".dw 0x" + hexWord(w)))
        //handler(ListingRec(location, offset, ".dw 0x" + hexWord(w)))
        offset += 2
    }

    override fun addWord(w: DeferredNumberArg, location: SourceLocation) {
        add(location, ".dw", w)
//        if (w.resolved != null) {
//            handler(ListingRec(location, offset, ".dw 0x" + hexWord(w.resolved!!)))
//        } else {
//            handler(ListingRec(location, offset, ".dw ????"))
//        }
        offset += 2
    }

    override fun addDword(dw: Int, location: SourceLocation) {
        add(location, ListingRec(location, offset, ".dd 0x" + hexDword(dw)))
//        handler(ListingRec(location, offset, ".dd 0x" + hexDword(dw)))
        offset += 4
    }

    override fun addDword(dw: DeferredNumberArg, location: SourceLocation) {
        add(location, ".dd", dw)
//        if (dw.resolved != null) {
//            handler(ListingRec(location, offset, ".dd 0x" + hexDword(dw.resolved!!)))
//        } else {
//            handler(ListingRec(location, offset, ".dd ????????"))
//        }
        offset += 4
    }

    override fun addValidation(location: SourceLocation, startLabel: String, endLabel: String, maxSize: Int) {
    }

    override fun validate() {
    }

    override fun resolveLabels() {
    }

    override fun resolve(name: String): Int? {
        return null
    }

    override fun startProc(name: String, location: SourceLocation) {
        currentProcName = name
    }

    override fun endProc(name: String, location: SourceLocation) {
        currentProcListingRec?.let { rec ->
            val size = offset - rec.offset
            rec.asmCode = "$name: ; $size bytes"
        }
        currentProcListingRec = null
        currentProcName = null
    }

    private class DumpOutput : CompilerOutput {
        private var array: ByteArray = ByteArray(0)
        private var pos = 0

        fun setArray(out: ByteArray) {
            array = out
            pos = 0
        }
        override fun addByte(v: Int) {
            array[pos++] = v.toByte()
        }

        override fun addWord(v: Int) {
            addByte(v and 0xff)
            addByte((v shr 8) and 0xff)
        }

        override fun addDword(v: Int) {
            addWord(v shl 0xffff)
            addWord((v shr 16) and 0xffff)
        }
    }

    override fun done() {
        for (itm in listing) {
            when {
                itm.rec != null -> {
                    handler(itm.rec)
                }
                itm.cmd != null -> {
                    val args = itm.args!!.toTypedArray()
                    var s = itm.cmd.str(*args)
                    val cmdName = s.substringBefore('\t')
                    if (cmdName != s && cmdName.length < 7) {
                        s = cmdName + SPACES[7 - cmdName.length] + s.substring(cmdName.length + 1)
                    }
                    val line = ListingRec(itm.location, itm.offset, s, commandDump(itm.cmd, *args))
                    handler(line)
                }
                itm.directive != null -> {
                    val arg = itm.args!![0] as DeferredNumberArg
                    val resolved = arg.resolved
                    val argStr = when (itm.directive) {
                        ".db" -> if (resolved != null) "0x" + hexByte(resolved) else "??"
                        ".dw" -> if (resolved != null) "0x" + hexWord(resolved) else "????"
                        ".qw" -> if (resolved != null) "0x" + hexDword(resolved) else "????????"
                        else -> "?"
                    }
                    handler(ListingRec(itm.location, itm.offset, itm.directive + " " + argStr))
                }
            }
        }
    }

    private fun commandDump(cmd: AsmCmd, vararg args: AsmArg): ByteArray {
        if (binaryGenerator == null) {
            return emptyByteArray
        }
        val size = cmd.size(*args)
        val res = ByteArray(size)
        dumpOutput.setArray(res)
        for (arg in args) {
            if (arg is ArgLabel && arg.resolved == null) {
                arg.resolved = binaryGenerator.getLabelOffset(arg)
            }
        }
        try {
            cmd.compile(dumpOutput, *args)
        } catch (_: Exception) {
            return emptyByteArray
        }
        return res
    }

    companion object {
        val SPACES = arrayOf("", " ", "  ", "   ", "    ", "     ", "      ", "       ", "        ", "         ", "          ")
    }
}