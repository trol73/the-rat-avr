package ru.trolsoft.compiler.generator

import ru.trolsoft.therat.arch.*
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.utils.hexWord
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

enum class OutputFormat {
    BINARY,
    INTEL_HEX
}

open class BinaryFileGenerator(
        private val endian: Endian,
        private val format: OutputFormat,
        private val fillEmpty: Int = 0,
        private val intelHexWidth: Int = 16
): Generator(), LabelResolver {

    val list = AsmList()
    private val labels = mutableMapOf<String, Label>()
    private val validationRules = mutableListOf<SizeValidationRule>()


    override fun addCommand(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg) {
        list.addCmd(location, cmd, *args)
    }

    override fun addLabel(name: String, location: SourceLocation) {
        if (labels.containsKey(name)) {
            throw GeneratorException("Label already defined", location)
        }
        labels[name] = Label(name, list.offset, location)
    }

    override fun org(offset: Int) {
        list.offset = offset
    }

    override fun addByte(b: Int, location: SourceLocation) = list.addByte(b, location)
    override fun addWord(w: Int, location: SourceLocation) = list.addWord(w, location)
    override fun addDword(dw: Int, location: SourceLocation) = list.addDword(dw, location)
    override fun addByte(b: DeferredNumberArg, location: SourceLocation) = list.addByte(b, location)
    override fun addWord(w: DeferredNumberArg, location: SourceLocation) = list.addWord(w, location)
    override fun addDword(dw: DeferredNumberArg, location: SourceLocation) = list.addDword(dw, location)


    override fun write(os: OutputStream) {
        resolveLabels()
        validate()
        val out = BinaryOutput(endian)
        // TODO need to sort blocks and check overlaps

        for (block in list.blocks) {
            out.gotoAddress(block.offset)
            out.reserve(block.bytesLength)
        }
        out.allocatePages(fillEmpty)

        for (block in list.blocks) {
            block.compile(out)
        }
        when (format) {
            OutputFormat.BINARY -> out.saveToBinaryFile(os, fillEmpty)
            OutputFormat.INTEL_HEX -> out.saveToIntelHex(os, intelHexWidth)
        }
    }

    override fun resolveLabels() {
        for (block in list.blocks) {
            var currentOffset = block.offset
            for (call in block) {
                for (arg in call.args) {
                    if (arg is ArgLabel) {
                        val labelOffset = getLabelOffset(arg)
                        arg.resolved = arg.resolver(currentOffset, labelOffset)
                    } else if (arg is DeferredNumberArg) {
                        arg.resolved = arg.resolver(arg,this)
                    }
                }
                currentOffset += call.size()
            }
        }
    }

    override fun addValidation(location: SourceLocation, startLabel: String, endLabel: String, maxSize: Int) {
        validationRules.add(SizeValidationRule(location, startLabel, endLabel, maxSize))
    }


    private fun getLabelOffset(argLabel: ArgLabel): Int {
        val localLabel = labels[argLabel.localName]
        if (localLabel != null) {
            return localLabel.offset
        }
        val globalLabel = labels[argLabel.name]
        if (globalLabel != null) {
            return globalLabel.offset
        }
        throw GeneratorException("Label not found: ${argLabel.name}", argLabel.location)
    }

    override fun resolve(name: String): Int? {
        val label = labels[name] ?: return null
        return label.offset
    }

    fun saveMapFile(path: String) {
        val writer = OutputStreamWriter(FileOutputStream(path))
        val labelsList = labels.values.sortedBy { it.offset }
        for (lbl in labelsList) {
            val line = "${hexWord(lbl.offset)}: ${lbl.name}\n"
            writer.write(line)
        }
        writer.close()
    }

    override fun validate() {
        for (v in validationRules) {
            val start = resolve(v.startLabel)
            val end = resolve(v.endLabel)
            if (start == null || end == null) {
                throw GeneratorException("Internal error: validation label not found", v.location)
            }
            val size = end - start
            if (size > v.maxSize) {
                throw GeneratorException("Block too large: expected: ${v.maxSize}, but found $size bytes", v.location)
            }
        }
    }


}

private data class SizeValidationRule(
        val location: SourceLocation,
        val startLabel: String,
        val endLabel: String,
        val maxSize: Int
)