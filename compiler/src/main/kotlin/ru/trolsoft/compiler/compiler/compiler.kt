package ru.trolsoft.compiler.compiler

import ru.trolsoft.compiler.general.CycleBlock
import ru.trolsoft.compiler.general.Var
import ru.trolsoft.compiler.generator.*
import ru.trolsoft.compiler.lexer.CachedFileLoader
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.compiler.utils.RatEncoding
import ru.trolsoft.compiler.utils.RatEncodingReader
import ru.trolsoft.compiler.utils.Stack
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.arch.ArgLabel
import ru.trolsoft.therat.arch.AsmArg
import ru.trolsoft.therat.arch.AsmCmd
import ru.trolsoft.therat.arch.DeferredNumberArg
import ru.trolsoft.therat.avr.errorUnsupportedOperation
import ru.trolsoft.therat.avr.errorValueTooBig
import ru.trolsoft.therat.lang.IdentifierToken
import ru.trolsoft.therat.lang.Scanner
import ru.trolsoft.therat.preprocessor.Preprocessor
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import ru.trolsoft.compiler.utils.hexWord
import ru.trolsoft.compiler.utils.resolvePath


abstract class Compiler(
    protected val scanner: Scanner,
    private var encodingsPath: String,
    private val console: ConsoleReporter,
    loader: FileLoader? = null,
    private val warningListener: ((String) -> Unit)? = null
) {
    protected val ppc = Preprocessor(loader ?: CachedFileLoader(scanner), console)
    private val encodings = mutableMapOf<String, RatEncoding>()
    protected val gens: MutableList<Generator> = mutableListOf()
    private var hexGen: BinaryFileGenerator? = null
    private var binGen: BinaryFileGenerator? = null
    private var asmGen: AsmFileGenerator? = null
    protected var totalCommands: Int = 0
    private var lastBlockCommands: Int = 0

    protected val vars = mutableMapOf<String, Var>()
    protected val procs = mutableMapOf<String, ProcNode>()
    protected val labels = mutableListOf<String>()
    protected val externalVars = mutableMapOf<String, Var>()
    protected val externalProcs = mutableMapOf<String, ProcNode>()
    internal val cycles = Stack<CycleBlock>()
    protected val inlineCallStack = mutableListOf<ProcNode>()

    private var encoding: String? = null
    protected var inlineLabel = ""
    protected var inlineLabelCounter = 0

    protected var currentProc: Procedure? = null
    protected var directiveOffsetNode: DirectiveNode? = null
    protected var directiveOffset: Int? = null
    private var outputName: String? = null

    abstract fun parse(stream: TokenStream): AstNode

    abstract fun compile(ast: AstNode)

    internal abstract fun compileAssembler(node: AssemblerInstrNode, comment: String? = null)
    internal abstract fun resolveArg(arg: Node): Node

    protected fun getVar(name: String): Var? = vars[name]
    protected fun getExternVar(name: String): Var? = externalVars[name]
    protected fun getExternProc(name: String): ProcNode? = externalProcs[name]
    internal fun getCycleStartLabel(): String? = cycles.peek()?.startLabel
    internal fun getCycleEndLabel(): String? = cycles.peek()?.endLabel

    fun compile(filePath: String) {
        val stream = ppc.process(filePath)
        val ast = parse(stream)
        compile(ast)
        forGens { done() }
    }

    fun addIncludePaths(paths: List<String>) =
        ppc.addIncludePaths(paths)

    protected fun addGenerator(gen: Generator) = gens.add(gen)

    protected inline fun forGens(crossinline action: Generator.() -> Unit) {
        for (gen in gens) {
            gen.action()
        }
    }

    fun addBinaryGenerator(endian: Endian, fillEmpty: Int = 0, startOffset: Int = 0) {
        val gen = BinaryFileGenerator(endian, OutputFormat.BINARY, fillEmpty, startOffset)
        binGen = gen
        addGenerator(gen)
    }

    fun addIntelHexGenerator(endian: Endian, fillEmpty: Int = 0, width: Int = 16) {
        val gen = BinaryFileGenerator(endian, OutputFormat.INTEL_HEX, fillEmpty, width)
        hexGen = gen
        addGenerator(gen)
    }

    fun addAsmListingGenerator(handler: ((ListingRec) -> Unit)?) {
        if (handler != null) {
            val gen = ListingGenerator(if (binGen != null) binGen else hexGen, handler)
            addGenerator(gen)
        }
    }

    fun addAsmGenerator() {
        val gen = AsmFileGenerator()
        asmGen = gen
        addGenerator(gen)
    }

    fun setBinaryStartOffset(offset: Int) {
        binGen?.setStartOffset(offset)
    }

    fun saveBinary(path: String) = binGen!!.write(path)
    fun saveBinary(stream: RawSink) = binGen!!.write(stream)
    fun saveBinary(buffer: Buffer) {
        binGen!!.write(object : RawSink {
            override fun write(source: Buffer, byteCount: Long) {
                buffer.write(source, byteCount)
            }
            override fun flush() {
                buffer.flush()
            }
            override fun close() {
                buffer.close()
            }
        })
    }
    fun saveHex(path: String) = hexGen!!.write(path)
    fun saveHex(stream: RawSink) = hexGen!!.write(stream)
    fun saveAsm(path: String) = asmGen!!.write(path)
    fun saveAsm(stream: RawSink) = asmGen!!.write(stream)
    fun saveMap(path: String) {
        val bg = binGen
        if (bg != null) {
            bg.saveMapFile(path)
        } else {
            hexGen?.saveMapFile(path)
        }
    }

    fun getOutputSize(): Int {
        val bg = binGen
        return if (bg != null) {
            bg.calculateSize()
        } else {
            val hg = hexGen
            hg?.calculateSize() ?: 0
        }
    }

    open fun getFreeFlashSize() = -1


    internal fun addLabel(label: String, location: SourceLocation) {
        forGens { addLabel(label, location) }
    }

    protected fun generateStartProc(name: String, location: SourceLocation) {
        forGens { startProc(name, location) }
    }

    protected fun generateEndProc(name: String, location: SourceLocation) {
        forGens { endProc(name, location) }
    }

    internal fun resolveProcLabel(name: String, location: SourceLocation, withLineNum: Boolean = false): String {
        val proc = currentProc
        if (proc != null) {
            val inlinePart = if (inlineLabel.isEmpty()) "" else "${inlineLabel}__${inlineLabelCounter}"
            return if (withLineNum) {
                "${proc.name}$inlinePart@$name@${location.line}"
            } else {
                "${proc.name}@$name"
            }
        }
        return name
    }

    fun addProcLabel(name: String, location: SourceLocation, withLineNum: Boolean = false): String {
        val label = resolveProcLabel(name, location, withLineNum)
        addLabel(label, location)
        return label
    }

    internal fun buildArgLabel(
        name: String, location: SourceLocation, withLineNum: Boolean,
        resolver: (Int, Int) -> Int
    ): ArgLabel {
        val localName = resolveProcLabel(name, location, withLineNum)
        return ArgLabel(name, localName, location, resolver)
    }


    fun define(name: String, value: String?) {
        if (value == null) {
            ppc.define(name, null)
        } else {
            val buf = Buffer().apply {
                write(value.encodeToByteArray())
            }
            val tokens = scanner.scan("<cmd>", buf)
            ppc.define(name, tokens)
        }
    }

    fun define(str: String) {
        val pos = str.indexOf('=')
        if (pos < 0) {
            define(str, null)
        } else {
            val name = str.substring(0, pos)
            val value = str.substring(pos + 1)
            define(name, value)
        }
    }

    protected fun addCommand(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg) {
        forGens { addCommand(location, cmd, *args) }
    }

    protected fun org(offset: Int) {
        forGens { org(offset) }
        lastBlockCommands = 0
    }

    protected fun addLine(str: String) {
        forGens {
            if (this is AsmFileGenerator) {
                addLine(str)
            }
        }
    }

    protected fun addCommand(location: SourceLocation, cmd: AsmCmd, comment: String?, vararg args: AsmArg) {
        totalCommands++
        lastBlockCommands++
        forGens { addCommand(location, cmd, comment, *args) }
    }

    protected fun addByte(b: Int, location: SourceLocation) {
        val v = if (b >= -128 && b <= 127) {
            b and 0xff
        } else if (b !in 0..0xff) {
            errorLimitedValueExpected("Byte", location)
        } else {
            b
        }
        forGens { addByte(v, location) }
    }

    protected fun addWord(b: Int, location: SourceLocation) {
        val v = if (b >= -32768 && b <= 32767) {
            b and 0xffff
        } else if (b < 0 || b > 0xffff) {
            errorLimitedValueExpected("Word", location)
        } else {
            b
        }
        forGens { addWord(v, location) }
    }

    protected fun addDword(b: Int, location: SourceLocation) {
        forGens { addDword(b, location) }
    }

    protected fun addByte(b: DeferredNumberArg, location: SourceLocation) {
        forGens { addByte(b, location) }
    }

    protected fun addWord(b: DeferredNumberArg, location: SourceLocation) {
        forGens { addWord(b, location) }
    }

    protected fun addDword(b: DeferredNumberArg, location: SourceLocation) {
        forGens { addDword(b, location) }
    }

    protected fun addValidation(location: SourceLocation, startLabel: String, endLabel: String, maxSize: Int) {
        forGens { addValidation(location, startLabel, endLabel, maxSize) }
    }

    protected fun validateSizes() {
        forGens { validate() }
    }

    open fun calculateConst(node: Node): Int {
        when (node) {
            is NumberNode -> return node.value
            is VariableNode -> return node.address
            is BinaryOperNode -> return calculateBinaryOpConst(node)
            is PrefixOperNode -> {
                return when (node.operator) {
                    Operator.BINARY_NOT -> {
                        val v = calculateConst(node.expression)
                        if (v <= 0xff) {
                            v.inv() and 0xff
                        } else if (v <= 0xffff) {
                            v.inv() and 0xff
                        } else {
                            errorValueTooBig(node.expression)
                        }
                    }
                    Operator.LOGICAL_NOT -> if (calculateConst(node.expression) == 0) 1 else 0
                    else -> errorWrongExpression(node)
                }
            }

            is IdentifierNode -> errorUndefinedIdentifier(node)
            else -> errorWrongExpression(node)
        }
    }

    private fun calculateBinaryOpConst(node: BinaryOperNode): Int {
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

    protected fun getLastBlockCommandsCounter() = lastBlockCommands
    fun getTotalCommandsCounter() = totalCommands


    fun warning(msg: String, node: Node) {
        val loc = node.getLocation()
        console.warning(msg, loc)
        warningListener?.invoke("${loc.fileName.resolvePath()}:${loc.line}:${loc.column}: $msg")
    }

    protected fun preloadProcedures(ast: AstNode) {
        for (node in ast.nodes) {
            if (node is ProcNode) {
                if (procs.containsKey(node.name)) {
                    errorProcedureAlreadyDefined(node.name, node)
                }
                procs[node.name] = node
            }
        }
    }

    protected fun preloadLabels(ast: AstNode) {
        for (node in ast.nodes) {
            if (node is LabelNode) {
                if (labels.contains(node.name)) {
                    errorLabelAlreadyDefined(node)
                }
                labels.add(node.name)
            }
        }
    }

    protected fun checkVarDefined(name: String, node: Node) {
        if (vars.containsKey(name) || externalVars.containsKey(name)) {
            errorVariableAlreadyExists(name, node)
        }
    }

    protected fun checkProcDefined(name: String, node: Node) {
        if (procs.containsKey(name) || externalProcs.containsKey(name)) {
            errorProcedureAlreadyDefined(name, node)
        }
    }
    protected fun checkVarOrProcDefined(name: String, node: Node) {
        checkVarDefined(name, node)
        checkProcDefined(name, node)
    }

    internal fun compileAsm(
        cmd: String,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, null)
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmReg(
        cmd: String,
        reg: RegisterNode,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegReg(
        cmd: String,
        reg1: RegisterNode,
        reg2: RegisterNode,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg1, reg2))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegNum(
        cmd: String,
        reg1: RegisterNode,
        num2: NumberNode,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg1, num2))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegNum(
        cmd: String,
        reg1: RegisterNode,
        num2: Int,
        location: SourceLocation,
        comment: String? = null,
        radix: Int = 10
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg1, NumberNode(location, num2, radix)))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmNum(
        cmd: String,
        num: Int,
        location: SourceLocation,
        comment: String? = null,
        radix: Int = 10
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(NumberNode(location, num, radix)))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmNum(
        cmd: String,
        num: NumberNode,
        location: SourceLocation,
        comment: String? = null,
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(num))
        compileAssembler(cmdNode, comment)
    }

    internal fun findProc(node: FunctionCallNode): ProcNode {
        val proc = procs[node.name]
        if (proc != null) {
            return proc
        }
        return externalProcs[node.name] ?: errorProcedureNotFound(node)
    }

    protected fun resolveInlineArg(name: String): Node? {
        if (inlineCallStack.isEmpty()) {
            return null
        }
        for (i in inlineCallStack.size - 1 downTo 0) {
            val proc = inlineCallStack[i]
            val arg = proc.getArg(name)
            if (arg != null) {
                return arg
            }
        }
        return null
    }


    fun simplifyBinaryOp(node: BinaryOperNode): Node {
        val left = resolveArg(node.left)
        val right = resolveArg(node.right)
        if (left is BinaryOperNode && right is NumberNode && left.operator != Operator.ARROW_RIGHT) {
            val leftRight = resolveArg(left.right)
            if (leftRight is NumberNode) {
                when {
                    node.operator == Operator.PLUS && left.operator == Operator.PLUS -> {
                        // binop(+, binop(+, X, const1), const2) -> binop(+, X, const1+const2)
                        val leftLeft = resolveArg(left.left)
                        val c = leftRight.value + right.value
                        return simplifyNodePlusConst(leftLeft, c)
                    }
                    node.operator == Operator.PLUS && left.operator == Operator.MINUS -> {
                        // binop(+, binop(-, X, const1), const2) -> binop(+, X, -const1+const2)
                        val leftLeft = resolveArg(left.left)
                        val c = -leftRight.value + right.value
                        return simplifyNodePlusConst(leftLeft, c)
                    }
                    node.operator == Operator.MINUS && left.operator == Operator.PLUS -> {
                        // binop(-, binop(+, X, const1), const2) -> binop(+, X, const1-const2)
                        val leftLeft = resolveArg(left.left)
                        val c =  leftRight.value - right.value
                        return simplifyNodePlusConst(leftLeft, c)
                    }
                    node.operator == Operator.MINUS && left.operator == Operator.MINUS -> {
                        // binop(-, binop(-, X, const1), const2) -> binop(+, X, -const1-const2)
                        val leftLeft = resolveArg(left.left)
                        val c =  -leftRight.value - right.value
                        return simplifyNodePlusConst(leftLeft, c)
                    }
                }
            }
            return BinaryOperNode(node.getLocation(), node.operator, left, right)
        } else if (left is BinaryOperNode) {
            return BinaryOperNode(node.getLocation(), node.operator, simplifyBinaryOp(left), right)
        }

//    * binop(const, const) -> const
//    * binop(+, binop(+, X, const1), const2) -> binop(+, X, const1+const2)

        return BinaryOperNode(node.getLocation(), node.operator, left, right)
    }

    private fun simplifyNodePlusConst(node: Node, c: Int): Node {
        return when {
            c == 0 -> node
            c > 0 -> {
                val cn = NumberNode(node.getLocation(), c)
                BinaryOperNode(node.getLocation(), Operator.PLUS, node, cn)
            }
            else -> {
                val cn = NumberNode(node.getLocation(), -c)
                BinaryOperNode(node.getLocation(), Operator.MINUS, node, cn)
            }
        }
    }

    private fun decomposeAssignOperation(node: BinaryOperNode, dest: Node, out: ArrayDeque<BinaryOperNode>) {
        val loc = node.getLocation()
        when (node.left) {
            is BinaryOperNode -> {
                if (node.right is NumberNode ||
                    (node.right is RegisterNode && dest is RegisterNode) ||
                    (node.right is RegisterGroupNode && dest is RegisterGroupNode)
                    ) {
                    val op = BinaryOperNode(loc, node.operator.toSelfAssign(), dest, node.right)
                    out.addFirst(op)
                    decomposeAssignOperation(node.left, dest, out)
                } else {
                    errorUnsupportedOperation(node)
                }
            }
            else -> {
                val op = BinaryOperNode(loc, node.operator.toSelfAssign(), dest, node.right)
                out.addFirst(op)
                if (dest is RegisterNode && node.left is RegisterNode && dest.reg == node.left.reg) {
                    return
                } else if (dest is RegisterGroupNode && node.left is RegisterGroupNode && dest == node.left) {
                    return
                }
                val op2 = BinaryOperNode(loc, Operator.ASSIGN, dest, node.left)
                out.addFirst(op2)
            }
        }
    }

    internal fun decomposeAssignOperation(node: BinaryOperNode, dest: Node): List<BinaryOperNode> =
        ArrayDeque<BinaryOperNode>().also {
            decomposeAssignOperation(node, dest, it)
        }

    internal fun expandBinaryOp(node: BinaryOperNode, out: MutableList<Node>, dest: Node): Node? {
        // dest - RegisterNode | RegisterPairNode
        // dest = base
        return when (node.left) {
            is BinaryOperNode -> {
                val base = expandBinaryOp(node.left, out, dest)
                if (base != null) {
                    val ex = BinaryOperNode(node.getLocation(), node.operator, dest, node.right)
                    out.add(ex)
                }
                base
            }

            is PrgArrayNode, is MemArrayNode, is IoPortNode -> {
                val base = node.left
                val ex = BinaryOperNode(node.getLocation(), node.operator, base, node.right)
                out.add(ex)
                base
            }

            else -> {
                out.add(node)
                when {
                    node.left is VariableNode || node.right is VariableNode
                            || node.left is ExternVarNode || node.right is ExternVarNode
                            || node.left is ResolvedExternVar || node.right is ResolvedExternVar -> null
                    node.left is RegisterNode -> node.left
                    node.right is RegisterNode && node.operator == Operator.PLUS -> node.right
                    else -> null
                }
            }
        }

//    * (ASSIGN, r1, (MINUS, (MINUS, (PLUS, (SHL, r2, 3), r3), r4), 1))
//    * r1 = r2
//    * (MINUS, (MINUS, (PLUS, (SHL_ASSIGN, r1, 3), r3), r4), 1)
//    *
//    * r1 = r2
//    * (SHL_ASSIGN, r1, 3)
//    * (PLUS_ASSIGN, r1, r3)
//    * (MINUS_ASSIGN, r1, r4)
//    * (MINUS_ASSIGN, r1, 1)

//   r26 = prg[Z++] & r29
//
//
    }


    fun setEncoding(name: String) {
        if (!encodings.contains(name)) {
            try {
                encodings[name] = RatEncodingReader().read("$encodingsPath/$name.enc")
            } catch (e: ParserException) {
                throw CompilerException(e.message!!, e.location, e)
            }
        }
        encoding = name
    }

    protected fun resolveChar(ch: CharLiteralNode): Int {
        return if (encoding == null) {
            ch.char.code
        } else {
            encodings[encoding]?.getCode(ch.char) ?: errorCantResolveChar(ch)
        }
    }

    protected fun resolveChar(s: StringLiteralNode, pos: Int) =
        resolveChar(
            CharLiteralNode(
                SourceLocation(s.getLocation(), pos),
                s.str[pos]
            )
        )

    protected fun processEncodingDirective(node: DirectiveNode) {
        val args = node.args ?: errorArgumentExpected(node)
        if (args.size != 1) {
            errorTooManyArgumentsInDirective(node)
        }
        val enc = args[0]
        if (enc !is StringLiteralNode) {
            errorStringExpected(node)
        }
        try {
            setEncoding(enc.str)
        } catch (_: kotlinx.io.IOException) {
            errorEncodingNotFound(enc)
        }
    }

    protected fun processOutputDirective(node: DirectiveNode) {
        val args = node.args ?: errorArgumentExpected(node)
        if (args.size != 1) {
            errorTooManyArgumentsInDirective(node)
        }
        val name = args[0]
        if (name !is StringLiteralNode) {
            errorStringExpected(node)
        }
        outputName = name.str
    }

    fun getOutputName() = outputName

}

fun internalError(): Nothing {
    throw RuntimeException("Internal error")
}

fun errorWrongExpression(node: Node): Nothing {
    throw CompilerException("Wrong expression", node.getLocation())
}

fun errorWrongExpression(loc: SourceLocation): Nothing {
    throw CompilerException("Wrong expression", loc)
}

fun errorUndefinedIdentifier(node: IdentifierNode): Nothing {
    throw CompilerException("Unknown identifier: ${node.name}", node.getLocation())
}

fun errorDirectPortWrite(port: IoPortNode, value: NumberNode): Nothing {
    throw CompilerException(
        "Can't write to port directly. You can do this using a temporary register, like so:\n\t ${port.name} = r24 = $value.",
        port.getLocation()
    )
}

fun errorDirectMemeWrite(mem: VariableNode, value: Node): Nothing {
    throw CompilerException(
        "Can't write to memory directly. You can do this using a temporary register, like so:\n\t ${mem.variable.name} = r24 = $value.",
        mem.getLocation()
    )
}

fun errorDirectRegBitsWrite(dest: RegisterBitNode, src: RegisterBitNode): Nothing {
    throw CompilerException(
        "Can't assign register bits directly. But you can do this using the T flag:\n\t ${dest.reg.reg}[${dest.bit}] <- ${src.reg.reg}[${src.bit}]",
        dest.getLocation()
    )
}

fun errorStructuredFieldNotFound(field: IdentifierToken, struct: String): Nothing {
    throw CompilerException("Unknown field '${field.name}' for structure $struct", field.location)
}

fun errorArrayOfPrimitivesExpected(loc: SourceLocation): Nothing {
    throw CompilerException("Array of primitives expected", loc)
}

fun errorArrayOfPrimitivesExpected(node: Node): Nothing {
    errorArrayOfPrimitivesExpected(node.getLocation())
}

fun errorStringExpected(node: Node): Nothing {
    throw CompilerException("String expected", node.getLocation())
}
fun errorWrongArraySize(expectedSize: Int, node: DataBlockNode): Nothing {
    throw CompilerException(
        "Wrong array size. Expected $expectedSize but found ${node.items.size} items",
        node.getLocation()
    )
}

fun errorWrongArraySize(loc: SourceLocation): Nothing {
    throw CompilerException("Wrong array size.", loc)
}

fun errorAddRegisterToGroup(group: RegisterGroupNode, reg: RegisterNode): Nothing {
    val zeroReg = if (reg.reg == "r1") "r0" else "r1"
    var add = ""
    repeat(group.size() - 1) {
        add += "$zeroReg."
    }
    throw CompilerException(
        "You can't just add a register to a group. The easiest way to do this is to use a register that contains 0 (for example, it could be $zeroReg), like so:\n\t $group += $add${reg.reg}",
        reg.getLocation()
    )
}

internal fun Operator.toSelfAssign() =
    when (this) {
        Operator.PLUS -> Operator.PLUS_ASSIGN
        Operator.MINUS -> Operator.MINUS_ASSIGN
        Operator.AND -> Operator.AND_ASSIGN
        Operator.OR -> Operator.OR_ASSIGN
        Operator.SHL -> Operator.SHL_ASSIGN
        Operator.SHR -> Operator.SHR_ASSIGN
        Operator.MUL -> Operator.MUL_ASSIGN
        Operator.DIV -> Operator.DIV_ASSIGN
        Operator.MOD -> Operator.MOD_ASSIGN
        Operator.XOR -> Operator.XOR_ASSIGN
        else -> internalError()
    }

internal fun errorProcedureAlreadyDefined(name: String, node: Node): Nothing {
    throw CompilerException("Procedure already defined: $name", node.getLocation())
}

internal fun errorConstantAlreadyDefined(name: String, node: Node): Nothing {
    throw CompilerException("Constant '$name' already defined", node.getLocation())
}

internal fun errorLabelAlreadyDefined(node: LabelNode): Nothing {
    throw CompilerException("Label already defined: ${node.name}", node.getLocation())
}

internal fun errorUnexpectedDirective(node: DirectiveNode): Nothing {
    throw CompilerException("Unexpected directive", node.getLocation())
}

internal fun errorTooManyArgumentsInAsmInstruction(node: AssemblerInstrNode): Nothing {
    throw CompilerException("Too many arguments", node.getLocation())
}

internal fun errorUnexpectedArgument(node: Node): Nothing {
    throw CompilerException("Unexpected argument: $node", node.getLocation())
}

internal fun errorWrongAssemblerInstruction(node: AssemblerInstrNode): Nothing {
    throw CompilerException("Hm.. Wrong assembler instruction: ${node.instr}", node.getLocation())
}

internal fun errorUnexpectedExpression(node: Node): Nothing {
    throw CompilerException("Unexpected expression", node.getLocation())
}

internal fun errorRegisterExpected(node: RegisterNode): Nothing {
    throw CompilerException("Register expected: ${node.reg}", node.getLocation())
}

internal fun errorVariableAlreadyExists(name: String, node: Node): Nothing {
    throw CompilerException("Variable '$name' already exists", node.getLocation())
}

internal fun errorProcedureNotFound(node: FunctionCallNode): Nothing {
    throw CompilerException("Procedure not found: ${node.name}", node.getLocation())
}

internal fun errorWrongArgument(node: Node): Nothing {
    throw CompilerException("Wrong argument: $node", node.getLocation())
}

internal fun errorProcedureArgumentsCount(proc: ProcNode, callNode: FunctionCallNode): Nothing {
    throw CompilerException("Procedure ${proc.name} has ${proc.args.size} arguments, but ${callNode.args.size} are passed.", callNode.getLocation())
}

internal fun errorInvalidBitNumber(bit: Int, node: Node): Nothing {
    throw CompilerException("Invalid bit number (0..7 expected): $bit", node.getLocation())
}

internal fun errorInvalidBitNumber(bit: NumberNode): Nothing {
    throw CompilerException("Invalid bit number: ${bit.value}", bit.getLocation())
}

internal fun errorWrongDataType(node: DataBlockNode): Nothing {
    throw CompilerException(
        "Wrong data type: ${node.type.toString().lowercase()}",
        node.getLocation()
    )
}

internal fun errorInvalidDirective(node: DirectiveNode): Nothing {
    throw CompilerException("Invalid directive: ${node.name}", node.getLocation())
}

internal fun errorArgumentExpected(node: DirectiveNode): Nothing {
    throw CompilerException("Argument expected", node.getLocation())
}

internal fun errorTooManyArgumentsInDirective(node: DirectiveNode): Nothing {
    throw CompilerException("Extra arguments in directive", node.getLocation())
}

internal fun errorParentCycleNotFound(node: Node): Nothing {
    throw CompilerException("Parent cycle not found", node.getLocation())
}

private fun errorCantResolveChar(ch: CharLiteralNode): Nothing {
    throw CompilerException("Can't resolve character '${ch.char}' (0x${hexWord(ch.char.code)})", ch.getLocation())
}

private fun errorEncodingNotFound(name: StringLiteralNode): Nothing {
    throw CompilerException("Encoding not found: ${name.str}", name.getLocation())
}

internal fun errorLimitedValueExpected(type: String, loc: SourceLocation): Nothing {
    throw CompilerException("$type value expected", loc)
}

