package ru.trolsoft.therat.avr

import ru.trolsoft.compiler.generator.AsmFileGenerator
import ru.trolsoft.compiler.generator.CompilerOutputAdapter
import ru.trolsoft.compiler.generator.GeneratorException
import ru.trolsoft.compiler.generator.Label
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.BinaryOperNode
import ru.trolsoft.compiler.parser.Node
import ru.trolsoft.compiler.parser.NumberNode
import ru.trolsoft.compiler.parser.Operator
import ru.trolsoft.therat.arch.*
import ru.trolsoft.therat.arch.avr.ArgRegPair
import ru.trolsoft.therat.arch.avr.ArgReg
import ru.trolsoft.therat.arch.avr.AvrCmd
import java.io.OutputStream
import java.lang.RuntimeException

class AvrGccAsmFileGenerator : AsmFileGenerator() {
    private val outStub = CompilerOutputAdapter()
    private val externs = mutableListOf<String>()
    private val globals = mutableListOf<String>()
    //private val labelToValidMap = mutableMapOf<String, String>()


    internal fun addExtern(name: String) = externs.add(name)
    internal fun addGlobal(name: String) = globals.add(name)

    private fun addHeader() {
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
        addHeader(header)
    }


    override fun buildCommand(cmd: AsmCmd, vararg args: AsmArg): StringBuilder {
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
        val skipChecking = args.isNotEmpty() && (args[0] is ArgExtern || (args.size == 2 && args[1] is ArgExtern) ||
                args[0] is DeferredNumberArg || (args.size == 2 && args[1] is DeferredNumberArg))
        if (!skipChecking) {
            try {
                cmd.compile(outStub, *args)
            } catch (e: AsmCompileException) {
                val msg = e.message ?: throw e
                if (!msg.startsWith("Label unresolved:")) {
                    throw e
                }
            }
        }
        return super.buildCommand(cmd, *args)
    }

    override fun resolveArgName(arg: AsmArg): String {
        when (arg) {
            is ArgLabel -> {
//                when {
//                    labels.containsKey(arg.localName) -> return arg.localName
//                    labels.containsKey(arg.name) -> return arg.name
//                }
                return when {
                    arg.name.contains('@') -> "_${arg.name.replace('@', '_')}"
                    arg.localName.contains('@') -> "_${arg.localName.replace('@', '_')}"
                    else -> arg.name
                }
//print("${arg.name} - ${arg.localName}\n")
//                return resolveLabelName(arg.localName)
                //val resolvedName = labelToValidMap[arg.localName]
//                if (resolvedName != null) {
//                    return resolvedName
//                }
            }
            is DeferredNumberArg -> {
                return resolveDeferredNode(arg.node)
            }
        }
        return super.resolveArgName(arg)
    }


    private fun resolveDeferredNode(node: Node): String {
        when {
            node is BinaryOperNode && node.left is ProgramMemNode && node.right is NumberNode -> {
                if (node.operator == Operator.SHR && node.right.value == 8) {
                    return "pm_hi8(${node.left.label})"
                } else if (node.operator == Operator.AND && node.right.value == 255) {
                    return "pm_lo8(${node.left.label})"
                }
            }
            node is ResolvedExternVar -> return node.name
        }
        errorUnsupportedOperation(node)
    }


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
            else -> {
                throw RuntimeException("Internal error")
            }
        }
        return sb
    }

    override fun write(os: OutputStream) {
        addHeader()
        super.write(os)
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
        addLine(".byte ${b.resolved!!}")
    }

    override fun addWord(w: DeferredNumberArg, location: SourceLocation) {
        addLine(".word ${w.resolved!!}")
    }

    override fun addDword(dw: DeferredNumberArg, location: SourceLocation) {
        addLine(".word ${dw.resolved!! and 0xffff} ")
        addLine(".word ${dw.resolved!! shr 16 and 0xffff}")
    }

    override fun addLabel(name: String, location: SourceLocation) {
        if (labels.containsKey(name)) {
            throw GeneratorException("Label already defined", location)
        }
        labels[name] = Label(name, -1, location)
        if (name.startsWith('?')) {
            return
        }
//        val validName = resolveLabelName(name)
//        lines.add(Line("$validName:", null, null))
        if (name.contains('@')) {
            val validName = "_${name.replace('@', '_')}"
//            labelToValidMap[name] = validName
            lines.add(Line("$validName:", null, null))
        } else {
            lines.add(Line("$name:", null, null))
        }
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