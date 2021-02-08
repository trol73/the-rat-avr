package ru.trolsoft.compiler.generator

import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.therat.arch.*
import java.io.OutputStream
import java.io.OutputStreamWriter

open class AsmFileGenerator: Generator(), LabelResolver {

    class Line(
        val str: String?,
        val comment: String?,
        val asm: AsmCall?
    )

    protected val lines = mutableListOf<Line>()
    protected val labels = mutableMapOf<String, Label>()

    override fun addCommand(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg) {
        lines.add(Line(null, null, AsmCall(cmd, args, location)))
    }

    override fun addCommand(location: SourceLocation, cmd: AsmCmd, comment: String?, vararg args: AsmArg) {
        lines.add(Line(null, comment, AsmCall(cmd, args, location)))
    }

    override fun org(offset: Int) {
        addLine(".org $offset")
    }

    override fun addByte(b: Int, location: SourceLocation) {
        addLine(".db $b")
    }

    override fun addWord(w: Int, location: SourceLocation) {
        addLine(".dw $w")
    }

    override fun addDword(dw: Int, location: SourceLocation) {
        addLine(".dd $dw")
    }

    override fun addByte(b: DeferredNumberArg, location: SourceLocation) {
        addLine(".db ${b.resolved}")
    }

    override fun addWord(w: DeferredNumberArg, location: SourceLocation) {
        addLine(".dw ${w.resolved}")
    }

    override fun addDword(dw: DeferredNumberArg, location: SourceLocation) {
        addLine(".dd ${dw.resolved}")
    }



    override fun addLabel(name: String, location: SourceLocation) {
        if (labels.containsKey(name)) {
            throw GeneratorException("Label already defined", location)
        }
        labels[name] = Label(name, -1, location)
        lines.add(Line("$name:", null, null))
    }

    override fun resolveLabels() {
    }

    fun addLine(s: String) {
        lines.add(Line(s, null, null))
    }

    fun addHeader(header: List<String>) {
        for ((index, s) in header.withIndex()) {
            lines.add(index, Line(s, null, null))
        }
    }

    override fun write(os: OutputStream) {
        val writer = OutputStreamWriter(os)
        for (line in lines) {
            writer.write(prepareLine(line))
            writer.write('\n'.toInt())
        }
        writer.close()
    }

    private fun prepareLine(line: Line): String {
        if (line.str != null) {
            return line.str
        } else if (line.asm != null) {
            val sb = buildCommand(line.asm.cmd, *line.asm.args)
            if (line.comment != null) {
                while (sb.length < 20) {
                    sb.append(' ')
                }
                sb.append("; ")
                sb.append(line.comment)
            }
            return sb.toString()
        }
        return ""
    }

    protected open fun buildCommand(cmd: AsmCmd, vararg args: AsmArg): StringBuilder {
        val sb = StringBuilder()
        sb.append('\t').append(cmd.str())
        if (args.isNotEmpty()) {
            while (sb.length < 8) {
                sb.append(' ')
            }
            sb.append(resolveArgName(args[0]))
            if (args.size > 1) {
                sb.append(", ")
                sb.append(resolveArgName(args[1]))
            }
        }
        return sb
    }

    protected open fun resolveArgName(arg: AsmArg): String {
        when (arg) {
            is ArgLabel -> {
                return when {
                    labels.containsKey(arg.localName) -> arg.localName
                    labels.containsKey(arg.name) -> arg.name
                    else -> throw GeneratorException("Label not found: ${arg.name}", arg.location)
                }
            }
            is DeferredNumberArg -> {
                arg.resolved = arg.resolver(arg, this)
                return arg.resolved?.toString() ?: "<unresolved>"
            }
        }
        return arg.toString()
    }

    override fun resolve(name: String): Int? {
        val label = labels[name] ?: return null
        return label.offset
    }

    override fun addValidation(location: SourceLocation, startLabel: String, endLabel: String, maxSize: Int) {
        val start = "$startLabel:"
        val end = "$endLabel:"
        lines.removeAll { it.str == start || it.str == end }
    }

    override fun validate() {
    }

}