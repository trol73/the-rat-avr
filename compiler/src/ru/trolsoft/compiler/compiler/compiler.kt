package ru.trolsoft.compiler.compiler

import ru.trolsoft.therat.arch.AsmArg
import ru.trolsoft.therat.arch.AsmCmd
import ru.trolsoft.therat.lang.Scanner
import ru.trolsoft.therat.preprocessor.Preprocessor
import ru.trolsoft.compiler.generator.*
import ru.trolsoft.compiler.lexer.CachedFileLoader
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.therat.arch.DeferredNumberArg
import ru.trolsoft.therat.avr.VariableNode
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.lang.RuntimeException

abstract class Compiler(
        protected val scanner: Scanner,
        loader: FileLoader? = null
)
{
    protected val ppc = Preprocessor(loader ?: CachedFileLoader(scanner))
    private val gens: MutableList<Generator> = mutableListOf()
    private var hexGen: BinaryFileGenerator? = null
    private var binGen: BinaryFileGenerator? = null
    private var asmGen: AsmFileGenerator? = null
    private var totalCommands: Int = 0
    private var lastBlockCommands: Int = 0

    abstract fun parse(stream: TokenStream): AstNode

    abstract fun compile(ast: AstNode)

    fun compile(filePath: String) {
        val stream = ppc.process(filePath)
        val ast = parse(stream)
        compile(ast)
    }

    protected fun addGenerator(gen: Generator) = gens.add(gen)

    fun addBinaryGenerator(endian: Endian, fillEmpty: Int = 0) {
        val gen = BinaryFileGenerator(endian, OutputFormat.BINARY, fillEmpty)
        binGen = gen
        addGenerator(gen)
    }

    fun addIntelHexGenerator(endian: Endian, fillEmpty: Int = 0, width: Int = 16) {
        val gen = BinaryFileGenerator(endian, OutputFormat.INTEL_HEX, fillEmpty, width)
        hexGen = gen
        addGenerator(gen)
    }

    fun addAsmGenerator() {
        val gen = AsmFileGenerator()
        asmGen = gen
        addGenerator(gen)
    }

    fun saveBinary(path: String) = binGen!!.write(path)
    fun saveBinary(stream: OutputStream) = binGen!!.write(stream)
    fun saveHex(path: String) = hexGen!!.write(path)
    fun saveHex(stream: OutputStream) = hexGen!!.write(stream)
    fun saveAsm(path: String) = asmGen!!.write(path)
    fun saveAsm(stream: OutputStream) = asmGen!!.write(stream)
    fun saveMap(path: String) {
        val bg = binGen
        if (bg != null) {
            bg.saveMapFile(path)
        } else {
            hexGen?.saveMapFile(path)
        }
    }


    fun addLabel(label: String, location: SourceLocation) {
        for (gen in gens) {
            gen.addLabel(label, location)
        }
    }

    fun define(name: String, value: String?) {
        if (value == null) {
            ppc.define(name, null)
        } else {
            val stream = ByteArrayInputStream(value.toByteArray())
            val tokens = scanner.scan("<cmd>", stream)
            ppc.define(name, tokens)
        }
    }

    fun define(str: String) {
        val pos = str.indexOf('=')
        if (pos < 0) {
            define(str, null)
        } else {
            val name = str.substring(0, pos)
            val value = str.substring(pos+1)
            define(name, value)
        }
    }

    protected fun addCommand(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg) {
        for (gen in gens) {
            gen.addCommand(location, cmd, *args)
        }
    }

    protected fun org(offset: Int) {
        for (gen in gens) {
            gen.org(offset)
        }
        lastBlockCommands = 0
    }

    protected fun addLine(str: String) {
        for (gen in gens) {
            if (gen is AsmFileGenerator) {
                gen.addLine(str)
            }
        }
    }

    protected fun addCommand(location: SourceLocation, cmd: AsmCmd, comment: String?, vararg args: AsmArg) {
        totalCommands++
        lastBlockCommands++
        for (gen in gens) {
            gen.addCommand(location, cmd, comment, *args)
        }
    }

    protected fun addByte(b: Int, location: SourceLocation) {
        for (gen in gens) {
            gen.addByte(b, location)
        }
    }

    protected fun addWord(b: Int, location: SourceLocation) {
        for (gen in gens) {
            gen.addWord(b, location)
        }
    }

    protected fun addDword(b: Int, location: SourceLocation) {
        for (gen in gens) {
            gen.addDword(b, location)
        }
    }

    protected fun addByte(b: DeferredNumberArg, location: SourceLocation) {
        for (gen in gens) {
            gen.addByte(b, location)
        }
    }

    protected fun addWord(b: DeferredNumberArg, location: SourceLocation) {
        for (gen in gens) {
            gen.addWord(b, location)
        }
    }

    protected fun addDword(b: DeferredNumberArg, location: SourceLocation) {
        for (gen in gens) {
            gen.addDword(b, location)
        }
    }

    protected fun addValidation(location: SourceLocation, startLabel: String, endLabel: String, maxSize: Int) {
        for (gen in gens) {
            gen.addValidation(location, startLabel, endLabel, maxSize)
        }
    }

    protected fun validateSizes() {
        for (gen in gens) {
            gen.validate()
        }
    }

    open fun calculateConst(node: Node): Int {
        when (node) {
            is NumberNode -> return node.value
            is VariableNode -> return node.address
            is BinaryOperNode -> {
                when (node.operator) {
                    Operator.PLUS -> return calculateConst(node.left) + calculateConst(node.right)
                    Operator.MINUS -> return calculateConst(node.left) - calculateConst(node.right)
                    Operator.MUL -> return calculateConst(node.left) * calculateConst(node.right)
                    Operator.DIV -> return calculateConst(node.left) / calculateConst(node.right)
                    Operator.MOD -> return calculateConst(node.left) % calculateConst(node.right)
                    Operator.SHL -> return calculateConst(node.left) shl calculateConst(node.right)
                    Operator.SHR -> return calculateConst(node.left) shr calculateConst(node.right)
                    Operator.OR -> return calculateConst(node.left) or calculateConst(node.right)
                    Operator.XOR -> return calculateConst(node.left) xor calculateConst(node.right)
                    Operator.AND -> return calculateConst(node.left) and calculateConst(node.right)

                    Operator.LOGICAL_OR -> return if (calculateConst(node.left) != 0 || calculateConst(node.right) != 0) 1 else 0
                    Operator.LOGICAL_AND -> return if (calculateConst(node.left) != 0 && calculateConst(node.right) != 0) 1 else 0
                    Operator.EQ -> return if (calculateConst(node.left) == calculateConst(node.right)) 1 else 0
                    Operator.NOT_EQ -> return if (calculateConst(node.left) != calculateConst(node.right)) 1 else 0
                    Operator.LESS -> return if (calculateConst(node.left) < calculateConst(node.right)) 1 else 0
                    Operator.GREAT -> return if (calculateConst(node.left) > calculateConst(node.right)) 1 else 0
                    Operator.LESS_EQ -> return if (calculateConst(node.left) <= calculateConst(node.right)) 1 else 0
                    Operator.GREAT_EQ -> return if (calculateConst(node.left) >= calculateConst(node.right)) 1 else 0

                    else -> errorWrongExpression(node)
                }
            }
            is PrefixOperNode -> {
                return when (node.operator) {
                    Operator.BINARY_NOT -> calculateConst(node.expression).inv()
                    Operator.LOGICAL_NOT -> if (calculateConst(node.expression) == 0) 1 else 0
                    else -> errorWrongExpression(node)
                }
            }
            is IdentifierNode -> errorUndefinedIdentifier(node)
            else -> TODO("$node")//errorWrongExpression(node)
        }
    }

    protected fun getLastBlockCommandsCounter() = lastBlockCommands
    fun getTotalCommandsCounter() = totalCommands



    fun warning(msg: String) {
        print("WARNING: $msg\n")
    }

}

fun internalError(): Nothing {
    throw RuntimeException("Internal error")
}

fun errorWrongExpression(node: Node): Nothing {
    throw CompilerException("Wrong expression: $node", node.getLocation())
}

fun errorWrongExpression(loc: SourceLocation): Nothing {
    throw CompilerException("Wrong expression", loc)
}

fun errorUndefinedIdentifier(node: IdentifierNode): Nothing {
    throw CompilerException("Unknown identifier: ${node.name}", node.getLocation())
}

