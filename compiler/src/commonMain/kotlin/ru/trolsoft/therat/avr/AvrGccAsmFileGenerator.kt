package ru.trolsoft.therat.avr

import kotlinx.io.RawSink
import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.generator.AsmFileGenerator
import ru.trolsoft.compiler.generator.CompilerOutputAdapter
import ru.trolsoft.compiler.generator.GeneratorException
import ru.trolsoft.compiler.generator.Label
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.arch.*
import ru.trolsoft.therat.arch.avr.ArgReg
import ru.trolsoft.therat.arch.avr.ArgRegPair
import ru.trolsoft.therat.arch.avr.AvrCmd

class AvrGccAsmFileGenerator : AsmFileGenerator() {
    private val outStub = CompilerOutputAdapter()
    private val externs = mutableListOf<String>()
    private val globals = mutableListOf<String>()
    private val varNames = mutableListOf<String>()
    private val varSizes = mutableMapOf<String, Int>()
    //private val labelToValidMap = mutableMapOf<String, String>()


    internal fun addExtern(name: String) = externs.add(name)
    internal fun addGlobal(name: String) = globals.add(name)

    internal fun addVar(name: String, size: Int) {
        varNames.add(name)
        varSizes[name] = size
    }

    private fun addHeaders() {
        val header = mutableListOf(
                "#include <avr/io.h>",
                ""
        )
        for (ext in externs) {
            header.add(".extern $ext")
        }
        if (externs.isNotEmpty()) {
            header.add("")
        }
        for (glob in globals) {
            header.add(".global $glob")
        }
        if (globals.isNotEmpty()) {
            header.add("")
        }
        if (varNames.isNotEmpty()) {
            header.add("")
            header.add(".bss")
            header.add("")

            for (n in varNames) {
                val size = varSizes[n]
                header.add("$n:\t.space $size")
            }
            header.add("")
            header.add(".text")
            header.add("")
        }
        addHeader(header)
    }


    override fun buildCommand(loc: SourceLocation, cmd: AsmCmd, vararg args: AsmArg): StringBuilder {
        if (args.isNotEmpty()) {
            when (cmd) {
//                AvrCmd.OUT, AvrCmd.SBI, AvrCmd.CBI, AvrCmd.SBIC, AvrCmd.SBIS ->
//                    return wrapIoFirstArg(cmd, args[0], args[1])
//                AvrCmd.IN ->
//                    return wrapIoSecondArg(cmd, args[0], args[1])
                AvrCmd.MOVW ->
                    return buildPairToPairCmd(cmd, args[0], args[1])
                AvrCmd.ADIW, AvrCmd.SBIW ->
                    return buildPairToVal(cmd, args[0], args[1])

            }
//            ldi    ZL, lo8(table_1)
//            ldi    ZH, hi8(table_1)
//            lpm    r20, Z+
//            lpm    r21, Z

//                    .global some_word_variable
//                    .global other_word_variable
//
//                    ldi	r30, pm_hi8(some_word_variable)
//            ldi	r31, pm_lo8(some_word_variable)
//            sts	other_word_variable+1, r30
//            sts	other_word_variable, r31

        }
        val skipChecking = args.isNotEmpty() &&
                (args[0] is ArgExtern || (args.size == 2 && args[1] is ArgExtern) ||
                args[0] is DeferredNumberArg || (args.size == 2 && args[1] is DeferredNumberArg) ||
                args[0] is ArgExternWithOffset || (args.size == 2 && args[1] is ArgExternWithOffset) )

        for (arg in args) {
            if (arg is ArgLabel && arg.resolved == null) {
                if (!labels.containsKey(arg.name) && !labels.containsKey(arg.localName) && !externs.contains(arg.name)) {
                    errorUnknownLabel(arg)
                }
            }
        }
        if (!skipChecking) {
            try {
                cmd.compile(outStub, *args)
            } catch (e: AsmCompileException) {
                val msg = e.message ?: throw CompilerException("Wrong instruction", loc, e)
                if (!msg.startsWith("Label unresolved:")) {
                    throw CompilerException(msg, loc, e)
                }
            }
        }
        return super.buildCommand(loc, cmd, *args)
    }

    override fun resolveArgName(arg: AsmArg): String {
        when (arg) {
            is ArgLabel -> {
                return when {
                    !arg.name.contains('@') && labels.keys.contains(arg.name) -> arg.name
                    arg.name.contains('@') -> "_${arg.name.replace('@', '_')}"
                    arg.localName.contains('@') -> "_${arg.localName.replace('@', '_')}"
                    else -> arg.name
                }
            }
            is DeferredNumberArg -> {
                val resolved = resolveDeferredNode(arg.node)
                arg.resolvedStr = resolved
                return resolved
            }
            is ArgExternWithOffset -> {
                return arg.toString()
            }
        }
        return super.resolveArgName(arg)
    }


    private fun resolveDeferredNode(node: Node): String {
        when {
            node is BinaryOperNode && node.right is NumberNode ->
            when {
                // prgmem >> 8
                // prgmem & 0xff
                node.left is ProgramMemNode -> {
                    if (node.operator == Operator.SHR && node.right.value == 8) {
                        val lblName = fixLabelName(node.left.label)
                        return "hi8($lblName)"
                    } else if (node.operator == Operator.AND && node.right.value == 255) {
                        val lblName = fixLabelName(node.left.label)
                        return "lo8($lblName)"
                    }
                }
                // (0x10000 - prgmem) & 0xff
                // (0x10000 - prgmem) >> 8
                node.left is BinaryOperNode && node.left.left is NumberNode && node.left.left.value == 0x10000 && node.left.operator == Operator.MINUS && node.left.right is ProgramMemNode -> {
                    val lblName = fixLabelName(node.left.right.label)
                    if (node.operator == Operator.AND && node.right.value == 0xff) {
                        return "lo8(-($lblName-0x10000))"
                    } else if (node.operator == Operator.SHR && node.right.value == 8) {
                        return "hi8(-($lblName-0x10000))"
                    }
                }

                // (0x10000 - prgmem/2) & 0xff
                // (0x10000 - prgmem/2) >> 8
                node.left is BinaryOperNode && node.left.left is NumberNode && node.left.left.value == 0x20000 && node.left.operator == Operator.MINUS && node.left.right is BinaryOperNode && node.left.right.isPrgMemDiv2() && node.left.right.left is ProgramMemNode -> {
                    val lblName = fixLabelName(node.left.right.left.label)
                    if (node.operator == Operator.AND && node.right.value == 0xff) {
                        return "pm_lo8(-($lblName-0x20000))"
                    } else if (node.operator == Operator.SHR && node.right.value == 8) {
                        return "pm_hi8(-($lblName-0x20000))"
                    }
                }

                // prgmem/2
                node.left is BinaryOperNode && node.left.isPrgMemDiv2() && node.left.left is ProgramMemNode -> {
                    if (node.operator == Operator.SHR && node.right.value == 8) {
                        val lblName = fixLabelName(node.left.left.label)
                        return "pm_hi8($lblName)"
                    } else if (node.operator == Operator.AND && node.right.value == 255) {
                        val lblName = fixLabelName(node.left.left.label)
                        return "pm_lo8($lblName)"
                    }
                }
            }
            node is ResolvedExternVar -> return node.name
            node is ResolvedExternProc -> return node.name
        }
        errorUnsupportedOperation(node)
    }

    private fun BinaryOperNode.isPrgMemDiv2() = operator == Operator.DIV && right is NumberNode && right.value == 2 && left is ProgramMemNode


    private fun wrapIoFirstArg(cmd: AsmCmd, arg1: AsmArg, arg2: AsmArg): StringBuilder {
        val sb = StringBuilder()
        sb.append('\t').append(cmd.str()).append(" _SFR_IO_ADDR(")
                .append(arg1.toString()).append("), ").append(arg2.toString())
        return sb
    }

    private fun wrapIoSecondArg(cmd: AsmCmd, arg1: AsmArg, arg2: AsmArg): StringBuilder {
        val sb = StringBuilder()
        sb.append('\t').append(cmd.str()).append(' ').append(arg1.toString()).append(", _SFR_IO_ADDR(")
                .append(arg2.toString()).append(')')
        return sb
    }

    private fun buildPairToPairCmd(cmd: AsmCmd, arg1: AsmArg, arg2: AsmArg): StringBuilder {
        val a1 = arg1 as ArgRegPair
        val a2 = arg2 as ArgRegPair
        val sb = StringBuilder()
        sb.append('\t').append(cmd.str()).append(' ').append(a1.low.name).append(", ")
                .append(a2.low.toString())
        return sb
    }

    private fun buildPairToVal(cmd: AsmCmd, arg1: AsmArg, arg2: AsmArg): StringBuilder {
        val sb = StringBuilder()
        when (arg1) {
            is ArgReg -> {
                sb.append('\t').append(cmd.str()).append(' ').append(arg1.name).append(", ")
                        .append(arg2.toString())
            }
            is ArgRegPair -> {
                sb.append('\t').append(cmd.str()).append(' ').append(arg1.low.name).append(", ")
                        .append(arg2.toString())
            }
            else -> internalError()
        }
        return sb
    }

    override fun write(sink: RawSink) {
        addHeaders()
        super.write(sink)
    }

    override fun addByte(b: Int, location: SourceLocation) {
        addLine(".byte $b")
    }

    override fun addWord(w: Int, location: SourceLocation) {
        addLine(".word $w")
    }

    override fun addDword(dw: Int, location: SourceLocation) {
        addLine(".word ${dw and 0xffff} ")
        addLine(".word ${dw shr 16 and 0xffff}")
    }

    override fun addByte(b: DeferredNumberArg, location: SourceLocation) {
        if (b.resolved == null) {
            errorUnresolvedData(b.node)
        } else {
            addLine(".byte ${b.resolved}")
        }
    }

    override fun addWord(w: DeferredNumberArg, location: SourceLocation) {
        when {
            w.resolved != null ->
                addLine(".word ${w.resolved}")
            w.node is ProgramMemNode ->
                addLine(".word ${w.node.label}")
            w.node is BinaryOperNode && w.node.isPrgMemDiv2() && w.node.left is ProgramMemNode ->
                addLine(".word pm(${w.node.left.label})")
            else -> {
                errorUnresolvedData(w.node)
            }
        }
    }

    override fun addDword(dw: DeferredNumberArg, location: SourceLocation) {
        if (dw.resolved == null) {
            errorUnresolvedData(dw.node)
        } else {
            addLine(".word ${dw.resolved!! and 0xffff}")
            addLine(".word ${dw.resolved!! shr 16 and 0xffff}")
        }
    }

    private fun Node.isPrgMemDiv2() =
        this is BinaryOperNode && right is NumberNode && right.value == 2 && operator == Operator.DIV && left is ProgramMemNode

    override fun addLabel(name: String, location: SourceLocation) {
        if (labels.containsKey(name)) {
            throw GeneratorException("Label already defined: $name", location)
        }
        labels[name] = Label(name, -1, location)
        if (name.startsWith('?')) {
            return
        }
//        val validName = resolveLabelName(name)
//        lines.add(Line("$validName:", null, null))
        val labelName = fixLabelName(name)
        lines.add(Line("$labelName:", null, null))
    }

    private fun fixLabelName(name: String): String =
        if (name.contains('@')) {
            "_${name.replace('@', '_')}"
        } else {
            name
        }

//    private fun resolveLabelName(name: String): String {
//        val resolved = labelToValidMap[name]
//        if (resolved != null) {
//            return resolved
//        }
//        return if (name.contains('@')) {
//            val validName = "_${name.replace('@', '_')}"
//            labelToValidMap[name] = validName
//            validName
//        } else {
//            name
//        }
//    }
}