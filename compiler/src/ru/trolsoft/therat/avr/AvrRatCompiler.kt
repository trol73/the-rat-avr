package ru.trolsoft.therat.avr


import ru.trolsoft.compiler.compiler.*
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.compiler.utils.Stack
import ru.trolsoft.therat.arch.*
import ru.trolsoft.therat.arch.avr.*
import ru.trolsoft.therat.avr.compile.*
import java.io.OutputStream
import kotlin.math.absoluteValue

class AvrRatCompiler(
        private val devPath: String,
        fileLoader: FileLoader? = null
) : Compiler(
        AvrScanner(), fileLoader) {
    var deviceLoader: FileLoader? = null

    private var gccAsmGen: AvrGccAsmFileGenerator? = null
    internal val pins = mutableMapOf<String, Pin>()
    private val uses = mutableMapOf<String, Use>()
    private val vars = mutableMapOf<String, Var>()
    private val externalVars = mutableMapOf<String, Var>()
    private val procs = mutableMapOf<String, ProcNode>()
    private val externalProcs = mutableMapOf<String, ProcNode>()
    internal val cycles = Stack<CycleBlock>()
    private var _device: AvrDevice? = null
    private var variableOffset: Int = 0
    private var directiveOffset: Int? = null
    private var inlineLabel = ""
    private var directiveOffsetNode: DirectiveNode? = null
    private val inlineCallStack = mutableListOf<ProcNode>()


    private var currentProc: Procedure? = null
    private var ast: AvrAstNode? = null

    override fun parse(stream: TokenStream): AvrAstNode {
        return AvrParser(stream).parse()
    }

    override fun compile(ast: AstNode) {
        val avrAst = ast as AvrAstNode
        this.ast = avrAst
        buildVectorsTable(avrAst)
        preloadProcedures(avrAst)
        for (node in ast) {
            when (node) {
                is ProcNode -> compileProc(node)
                is PinDeclarationNode -> processPin(node)
                is VarDeclarationNode -> processVar(node)
                is DataArrayDeclarationNode -> processVar(node)
                is UseNode -> processUse(node)
                is DirectiveNode -> processDirective(node)
                is LabelNode -> addProcLabel(node.name, node.getLocation())
                is DataBlockNode -> compileDataBlock(node)
                is ExternVarNode -> compileExternVar(node)
                is ExternProcNode -> compileExternProc(node)
                else -> TODO("unexpected node: $node")
            }
        }
    }


    fun addGccAsmGenerator() {
        val gen = AvrGccAsmFileGenerator()
        gccAsmGen = gen
        addGenerator(gen)
    }

    fun saveGccAsm(stream: OutputStream) = gccAsmGen!!.write(stream)
    fun saveGccAsm(path: String) = gccAsmGen!!.write(path)

    override fun calculateConst(node: Node): Int {
//        if (node is BinaryOperNode) {
//            val left = resolveArg(node.left)
//            val right = resolveArg(node.right)
//            val resolved = BinaryOperNode(node.operator, left, right)
//            return calculateConst(resolved)
//        }
        val resolved = resolveArg(node)
        return super.calculateConst(resolved)
    }

    internal fun resolveArg(arg: Node): Node {
        if (arg is IdentifierNode) {
            return resolveIdentifierArg(arg)
        } else if (arg is RegisterNode) {
            if (isPartOfXYZ(arg.reg)) {
                return RegisterNode(arg.getLocation(), resolvePartOfXYZ(arg.reg))
            } else if (isPairName(arg.reg)) {
                val pairName = arg.reg.toLowerCase()
                val high = resolvePartOfXYZ(pairName + "h")
                val low = resolvePartOfXYZ(pairName + "l")
                val loc = arg.getLocation()
                return RegisterGroupNode(loc, listOf(high, low))
            }
        } else if (arg is FunctionCallNode && arg.isBuiltin()) {
            return arg.compileBuiltin(this)
        } else if (arg is CharLiteralNode) {
            return NumberNode(arg.getLocation(), arg.char.toInt())
        } else if (arg is BinaryOperNode && arg.operator == Operator.ARROW_RIGHT) {
            return resolvePort(arg)
        } else if (arg is IndexedNode) {
            when {
                arg.name.name == "mem" -> return MemArrayNode(arg.getLocation(), resolveArg(arg.index))
                arg.name.name == "prg" -> return PrgArrayNode(arg.getLocation(), resolveArg(arg.index))
                else -> {
                    val name = resolveArg(arg.name)
                    val index = resolveArg(arg.index)
                    when (name) {
                        is RegisterNode -> when (index) {
                            is NumberNode -> return makeRegisterBitNode(name, index.value)
                            is IdentifierNode -> {
                                val pin = pins[index.name] ?: errorUnsupportedOperation(arg)
                                return makeRegisterBitNode(name, pin.pin)
                            }
                        }
                        is ResolvedExternVar -> {
                            if (index !is NumberNode) {
                                errorUnsupportedOperation(index)
                            }
                            return if (index.value == 0) {
                                name
                            } else {
                                name.modifyName(index.value)
                            }
                        }
                        else -> errorUndefinedIdentifier(arg.name)
                    }
                }
            }
        } else if (arg is IndexedRegNode) {
            when (arg.index) {
                is NumberNode -> return makeRegisterBitNode(arg.reg, arg.index.value)
                is IdentifierNode -> {
                    val pin = pins[arg.index.name] ?: errorUnsupportedOperation(arg)
                    return makeRegisterBitNode(arg.reg, pin.pin)
                }
            }
        } else if (arg is SuffixOperNode) {
            return SuffixOperNode(arg.operator, resolveArg(arg.expression))
        } else if (arg is PrefixOperNode) {
            val res = PrefixOperNode(arg.getLocation(), arg.operator, resolveArg(arg.expression))
            if (res.expression is NumberNode && res.operator == Operator.BINARY_NOT) {
//                return resolveArg(res)
                return NumberNode(res.getLocation(), super.calculateConst(res))
            }
//            print("$res")
            return res
        } else if (arg is BinaryOperNode) {
            val left = resolveArg(arg.left)
            val right = resolveArg(arg.right)
            if (left is NumberNode && right is NumberNode) {
                return NumberNode(arg.getLocation(), super.calculateConst(arg))
            }
        } else if (arg is DotGroupNode) {
            val regs = mutableListOf<String>()
            for (n in arg.items) {
                when (n) {
                    is RegisterNode -> regs.add(n.reg)
                    else -> {
                        val reg = resolveArg(n) as? RegisterNode ?: errorRegisterExpected(n)
                        regs.add(reg.reg)
                    }
                }
            }
            return RegisterGroupNode(arg.getLocation(), regs)
        }
        return arg
    }

    private fun makeRegisterBitNode(reg: RegisterNode, bit: Int): RegisterBitNode {
        if (bit < 0 || bit > 7) {
            errorInvalidBitNumber(bit, reg.getLocation())
        }
        return RegisterBitNode(reg, bit)
    }

    private fun resolveIdentifierArg(arg: IdentifierNode): Node {
        when (arg.name) {
            "RAMEND" -> {
                val dev = getDevice()
                val ramend = dev.ramStart + dev.ramSize - 1
                return NumberNode(arg.getLocation(), ramend, 16)
            }
        }
        val proc = currentProc
        val inlineArg = resolveInlineArg(arg.name)
        if (!inlineCallStack.isEmpty() && inlineArg != null) {
            return inlineArg
        } else if (proc != null && proc.hasUse(arg.name)) {
            return proc.getUse(arg.name)!!.reg
        } else if (proc != null && proc.hasArg(arg)) {
            val procArg = proc.getArg(arg)!!
            return resolveArg(procArg)
        } else if (uses.containsKey(arg.name)) {
            return uses[arg.name]!!.reg
        } else if (getVar(arg.name) != null) {
            val variable = getVar(arg.name)!!
            return VariableNode(arg.getLocation(), variable, getVariableAddress(variable))
        } else if (getExternVar(arg.name) != null) {
            val v = getExternVar(arg.name)!!
            return ResolvedExternVar(arg.getLocation(), arg.name, v.size, v.dataType)
        } else if (getExternProc(arg.name) != null) {
            val p = getExternProc(arg.name)!!
            return ResolvedExternProc(arg.getLocation(), arg.name, p.args)
        } else {
            val ioAddress = getIoAddress(arg.name)
            if (ioAddress >= 0) {
                val io = findIo(arg.name)
                val ioSize = io?.size ?: 1
                val ioRegs = io?.bits ?: listOf()
                // TODO !!! надо правильно получать список битов для многобайтовых портов
                return IoPortNode(arg.getLocation(), arg.name, ioAddress, ioSize, ioRegs)
            }

        }
        return arg
    }

    private fun resolveInlineArg(name: String): Node? {
        if (inlineCallStack.isEmpty()) {
            return null
        }
        for (i in inlineCallStack.size-1 downTo 0) {
            val proc = inlineCallStack[i]
            val arg = proc.getArg(name)
            if (arg != null) {
                return arg
            }
        }
        return null
    }

    private fun resolvePort(arg: BinaryOperNode): Node {
        // Special case
        if (arg.left is IdentifierNode && arg.left.name == "SREG" && arg.right is RegisterNode && arg.right.reg == "Z") {
            val io = findIo("SREG") ?: return arg
            val portNode = IoPortNode(arg.getLocation(), "SREG", io.offset, io.size, io.bits)
            return IoPortBitNode(arg.getLocation(), portNode, "Z", io.getBitNumber("Z"))
        }

        val left = resolveArg(arg.left)
        val right = resolveArg(arg.right)
        if (left is IoPortNode) {
            if (right is IdentifierNode) {
                val bit = left.getBit(right.name) ?: errorWrongIoPortBit(left, right.name)
                return IoPortBitNode(arg.getLocation(), left, right.name, bit)
            }
            if (right is NumberNode) {
                val bitNum = right.value
                if (bitNum < 0 || bitNum > 7) {
                    errorInvalidBitNumber(right)
                }
                return IoPortBitNode(arg.getLocation(), left, "", bitNum)
            }
        } else if (left is IdentifierNode && right is IdentifierNode) {
            val pinName = left.name
            val pinPort = right.name
            val pin = pins[pinName] ?: return arg
            val portName = when (pinPort) {
                "pin" -> "PIN${pin.port}"
                "port" -> "PORT${pin.port}"
                "ddr" -> "DDR${pin.port}"
                else -> errorWrongPinAccess(right)
            }
            val io = findIo(portName) ?: return arg
            val portNode = IoPortNode(arg.getLocation(), portName, io.offset, io.size, io.bits)
            return IoPortBitNode(arg.getLocation(), portNode, "", pin.pin)
        }
        return arg
        //errorWrongExpression(arg)
    }

    private fun findProc(node: FunctionCallNode): ProcNode {
        val proc = procs[node.name]
        if (proc != null) {
            return proc
        }
        return externalProcs[node.name] ?: errorProcedureNotFound(node)
    }

    private fun getIoAddress(name: String): Int = getDevice().getIoAddr(name)
    private fun findIo(name: String): IoRegister? = getDevice().findIo(name)
    internal fun findIoWithBit(bitName: String): IoRegister? = getDevice().findIoWithBit(bitName)
    internal fun getCycleStartLabel(): String? = cycles.peek()?.startLabel
    internal fun getCycleEndLabel(): String? = cycles.peek()?.endLabel
    private fun getVar(name: String): Var? = vars[name]
    private fun getExternVar(name: String): Var? = externalVars[name]
    private fun getExternProc(name: String): ProcNode? = externalProcs[name]


    private fun buildVectorsTable(ast: AvrAstNode) {
        val vectors = ast.vectors ?: return
        val allDeviceVectors = getDevice().interrupts
        assert(allDeviceVectors.size() == allDeviceVectors.vectors.last().offset + 1)
        checkVectorsTable(vectors, allDeviceVectors)
        val defaultNode = vectors.getVectorNode("default") ?: AssemblerInstrNode(vectors.getLocation(), "reti", null)
        for (v in allDeviceVectors) {
            if (v.name.toUpperCase() == "RESET" && vectors.getVectorNode(v.name) == null) {
                val mainProc = getMainProc(ast)
                if (mainProc != null) {
                    compileJump("rjmp", "main", vectors.getLocation(), v.name)
                    continue
                }
            }
            val node = vectors.getVectorNode(v.name) ?: defaultNode
            if (node !is AssemblerInstrNode) {
                errorAssemblerInstructionExpected(node)
            }
            // TODO check 2 byte size
            compileAssembler(node, v.name)
        }
        addLine("\n")
    }

    private fun preloadProcedures(ast: AvrAstNode) {
        for (node in ast.nodes) {
            if (node is ProcNode) {
                if (procs.containsKey(node.name)) {
                    errorProcedureAlreadyDefined(node)
                }
                procs[node.name] = node
            }
        }
    }

    /**
     * cmd: "rjmp", "jmp", "rcall", "call"
     */
    internal fun compileJump(cmd: String, label: String, location: SourceLocation, comment: String? = null) {
        val argNode = IdentifierNode(location, label)
        val jmp = AssemblerInstrNode(location, cmd, listOf(argNode))
        compileAssembler(jmp, comment)
    }

    internal fun compileAsmReg(cmd: String, reg: RegisterNode, location: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegReg(cmd: String, reg1: RegisterNode, reg2: RegisterNode, location: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg1, reg2))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegNum(cmd: String, reg1: RegisterNode, num2: NumberNode, location: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg1, num2))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegDeferredNum(cmd: AsmCmd, reg1: RegisterNode, numNode: Node, location: SourceLocation, comment: String? = null) {
        val arg1 = prepareAsmArg(reg1, ArgType.REG)
        val arg2 = DeferredNumberArg(numNode, this::resolveDeferred)
        addCommand(location, cmd, comment, arg1, arg2)
    }

    internal fun compileAsmLowerIoBit(cmd: String, ioBit: IoPortBitNode) {
        if (!ioBit.port.isLowerPort()) {
            errorLowIoExpected(ioBit)
        }
        val asmArgs = listOf(
                NumberNode(ioBit.getLocation(), ioBit.port.address, 16),
                NumberNode(ioBit.getLocation(), ioBit.bitIndex, 10)
        )
        val cmdNode = AssemblerInstrNode(ioBit.getLocation(), cmd, asmArgs)
        compileAssembler(cmdNode)
    }

    private fun resolveDeferred(arg: DeferredNumberArg, labelResolver: LabelResolver): Int {
        val resolved = resolveDeferredNode(arg.node, labelResolver)
        if (resolved is NumberNode) {
            return resolved.value
        }
        errorWrongExpression(arg.node)
    }

    private fun resolveDeferredNode(node: Node, labelResolver: LabelResolver): Node {
        return when (node) {
            is ProgramMemNode -> {
                val offset = labelResolver.resolve(node.label) ?: errorLabelNotFound(node)
//                assert(!node.inWords)
                NumberNode(node.getLocation(), offset)
            }
            is BinaryOperNode -> {
                val left = resolveDeferredNode(node.left, labelResolver)
                val right = resolveDeferredNode(node.right, labelResolver)
                NumberNode(node.getLocation(), calculateConst(BinaryOperNode(node.getLocation(), node.operator, left, right)))
            }
            else -> resolveArg(node)
        }
    }


    internal fun compileAsmRegNum(cmd: String, reg1: RegisterNode, num2: Int, location: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg1, NumberNode(location, num2)))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmNumReg(cmd: String, num1: NumberNode, reg2: RegisterNode, location: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(num1, reg2))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmNumReg(cmd: String, num1: Int, reg2: RegisterNode, location: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(NumberNode(location, num1), reg2))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmPairReg(cmd: String, pair: Node, reg: RegisterNode, location: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(pair, reg))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegPair(cmd: String, reg: RegisterNode, pair: Node, location: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg, pair))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmExtReg(cmd: String, extern: ResolvedExternVar, reg: RegisterNode, loc: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(loc, cmd, listOf(extern, reg))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegExt(cmd: String, reg: RegisterNode, extern: ResolvedExternVar, loc: SourceLocation, comment: String? = null) {
        val cmdNode = AssemblerInstrNode(loc, cmd, listOf(reg, extern))
        compileAssembler(cmdNode, comment)
    }



    private fun getMainProc(ast: AvrAstNode): ProcNode? {
        for (node in ast.nodes) {
            if (node is ProcNode && node.name == "main") {
                return node
            }
        }
        return null
    }


    private fun checkVectorsTable(vectors: VectorTableNode, interrupts: Interrupts) {
        for ((name, value) in vectors.table) {
            if (name != "default" && !interrupts.hasVector(name)) {
                errorUnknownVectorName(name, value.getLocation())
            }
        }
    }


    private fun getDevice(): AvrDevice {
        val dev = _device
        if (dev != null) {
            return dev
        }
        val name = ppc.getStringMacro("CPU") ?: throw CompilerException("'CPU' macro doesn't defined", ast?.getLocation())
        if (name.isBlank()) {
            throw CompilerException("'CPU' empty value", ppc.getMacroLocation("CPU"))
        }
        val path = "$devPath/$name.dev"
        val loaded = parseAvrDeviceDefinitionFile(path, deviceLoader)
        _device = loaded
        return loaded
    }


    private fun processPin(node: PinDeclarationNode) {
        val dev = getDevice()
        if (node.devicePin.length != 2) {
            errorInvalidPin(node.devicePin, node)
        }
        val portChar = node.devicePin[0]
        val numChar = node.devicePin[1]
        if (numChar < '0' || numChar > '7') {
            errorInvalidPin(node.devicePin, node)
        }
        val pinBit = numChar.toString().toInt()
        if (!dev.hasPort(portChar)) {
            throw CompilerException("Device ${dev.name} doesn't have port $portChar", node.getLocation())
        }
        if (pins.containsKey(node.name)) {
            errorPinAlreadyDefined(node.name, node)
        }
        for (pin in pins.values) {
            if (pin.pin == pinBit && pin.port == portChar) {
                warning("Pin ${node.devicePin} already defined")
            }
        }
        pins[node.name] = Pin(portChar, pinBit)
    }

    private fun processVar(node: Node) {
        val dirOffset = directiveOffset
        if (dirOffset != null) {
            variableOffset = dirOffset
            directiveOffset = null
            directiveOffsetNode = null
        }
        // TODO проверять наложения
        val variable = when (node) {
            is VarDeclarationNode -> {
                val v = Var(node.name, node.dataType, null, variableOffset)
                variableOffset += v.ramSize()
                v
            }
            is DataArrayDeclarationNode -> {
                val size = calculateConst(node.size)
                val v = Var(node.name, node.dataType, size, variableOffset)
                variableOffset += v.ramSize()
                v
            }
            else -> internalError()
        }

        if (vars.containsKey(variable.name)) {
            errorVariableAlreadyExists(variable.name, node)
        }
        if (externalVars.containsKey(variable.name)) {
            errorVariableAlreadyExists(variable.name, node)
        }
        vars[variable.name] = variable
    }

    private fun processUse(node: UseNode) {
        val use = Use(node.name, node.reg, node.capture)
        val proc = currentProc
        if (proc == null) {
            if (uses.containsKey(use.name)) {
                errorGlobalUseAlreadyDefined(node)
            }
            uses[use.name] = use
        } else {
            if (proc.hasUse(use.name)) {
                errorProcUseAlreadyDefined(node)
            }
            if (uses.containsKey(use.name)) {
                warning("Name shadowed: ${use.name}")
            }
            proc.addUse(use)
        }
    }

    private fun processSaveregs(node: SaveregsNode) {
        val regs = mutableListOf<RegisterNode>()
        for (arg in node.args) {
            val reg = resolveArg(arg)
            when (reg) {
                is RegisterNode -> {
                    compileAsmReg("push", reg, arg.getLocation())
                    regs.add(reg)
                }
                else -> {
                    errorUnexpectedArgument(arg)
                }
            }
        }
        compileNode(node.body)
        for (arg in regs.reversed()) {
            compileAsmReg("pop", arg, arg.getLocation())
        }
    }

    private fun processDirective(node: DirectiveNode) {
        if (node.name == "org") {
            processOrgDirective(node)
        } else {
            errorInvalidDirective(node)
        }
    }

    private fun processOrgDirective(node: DirectiveNode) {
        val args = node.args ?: errorArgumentExpected(node)
        if (args.size != 1) {
            errorTooManyArgumentsInDirective(node)
        }
        val arg = calculateConst(args[0])
        directiveOffset = arg
        directiveOffsetNode = node
    }


    private fun compileProc(proc: ProcNode) {
        val dirOffset = directiveOffset
        if (dirOffset != null) {
            if (proc.inline) {
                errorUnexpectedDirective(directiveOffsetNode!!)
            }
            if (dirOffset % 2 != 0) {
                errorEvenValueExpected(directiveOffsetNode!!)
            }
            org(dirOffset)
            directiveOffset = null
            directiveOffsetNode = null
        }
        if (!proc.inline) {
            addLabel(proc.name, proc.getLocation())
            gccAsmGen?.addGlobal(proc.name)
            currentProc = Procedure(proc.name, proc.args, proc)
            compileNode(proc.body)
            addLine("\n")
            currentProc = null
        }
    }


    internal fun compileNode(node: Node) {
        when (node) {
            is CompoundStmt -> {
                for (n in node.nodes) {
                    compileNode(n)
                }
            }
            is AssemblerInstrNode -> compileAssembler(node)
            is LabelNode -> {
                if (node.global) {
                    addLabel(node.name, node.getLocation())
                } else {
                    addProcLabel(node.name, node.getLocation())
                }
            }
            is LoopNode -> compileLoop(this, node)
            is BinaryOperNode -> compileBinaryOp(this, node)
            is SuffixOperNode -> compileSuffixOp(this, node)
            is IfNode -> compileIf(this, node)
            is DoWhileNode -> compileDoWhile(this, node)
            is DataBlockNode -> compileDataBlock(node)
            is FunctionCallNode -> compileInlineCall(node)
            is UseNode -> processUse(node)
            is SaveregsNode -> processSaveregs(node)
            else -> {
                //print("$node\n")
                TODO("${node.javaClass} ${node.getLocation()}\n")
            }
        }

    }

    internal fun compileSizeLimitedNode(node: Node, maxCommands: Int?, maxBytes: Int) {
        val beforeCount= getLastBlockCommandsCounter()
        val startLabel = addProbeStartLabel(node)
        compileNode(node)
        addProbeEndLabel(node, startLabel, maxBytes)
        val commandsInBlock = getLastBlockCommandsCounter() - beforeCount
        if (maxCommands != null && commandsInBlock > maxCommands) {
            if (maxCommands == 1) {
                errorSingleInstructionExpected(node, commandsInBlock)
            } else {
                errorTooManyCommandsInBlock(node, maxCommands, commandsInBlock)
            }
        }
    }

    internal fun compileOneWordNode(node: Node) = compileSizeLimitedNode(node, 1, 2)


    internal fun compileAssembler(node: AssemblerInstrNode, comment: String? = null) {
        val cmd = AvrCmd.getByName(node.instr) ?: errorWrongAssemblerInstruction(node)
        val location = node.getLocation()
        when {
            node.args == null -> addCommand(location, cmd, comment)
            node.args.size == 1 -> {
                val arg = prepareAsmArg(node.args[0], cmd.arg1)
                addCommand(location, cmd, comment, arg)
            }
            node.args.size == 2 -> {
                val arg1 = prepareAsmArg(node.args[0], cmd.arg1)
                val arg2 = prepareAsmArg(node.args[1], cmd.arg2)
                addCommand(location, cmd, comment, arg1, arg2)
            }
            else -> errorTooManyArgumentsInAsmInstruction(node)
        }
    }

    internal fun compileAssembler(loc: SourceLocation, cmd: AvrCmd, vararg args: AsmArg) = addCommand(loc, cmd, *args)


    private fun prepareAsmArg(node: Node, type: ArgType?): AsmArg {
        val arg = resolveArg(node)
        return when (arg) {
            is NumberNode -> {
                ArgNumber(arg.value)
            }
            is RegisterNode -> {
                ArgReg.fromStr(arg.reg) ?: errorRegisterExpected(arg)
            }
            is IdentifierNode -> {
                when (type) {
                    ArgType.LABEL_REL -> {
                        val resolver = { currentOffset: Int, labelOffset: Int ->
                            assert(currentOffset % 2 == 0)
                            assert(labelOffset % 2 == 0)
                            (labelOffset - currentOffset) / 2 - 1
                        }
                        if (procs.containsKey(arg.name)) {
                            ArgLabel(arg.name, arg.name, arg.getLocation(), resolver)
                        } else {
                            buildArgLabel(arg.name, arg.getLocation(), false, resolver)
                        }
                    }
                    ArgType.LABEL_ABS -> {
                        val resolver = { currentOffset: Int, labelOffset: Int ->
                            assert(currentOffset % 2 == 0)
                            assert(labelOffset % 2 == 0)
                            labelOffset / 2
                        }
                        if (procs.containsKey(arg.name)) {
                            ArgLabel(arg.name, arg.name, arg.getLocation(), resolver)
                        } else {
                            buildArgLabel(arg.name, arg.getLocation(), false, resolver)
                        }
                    }
                    ArgType.ADDR -> {
                        val variable = getVar(arg.name) ?: errorVariableNotFound(arg)
                        ArgNumber(getVariableAddress(variable))
                    }
                    ArgType.PORT -> {
                        val addr = getDevice().getIoAddr(arg.name)
                        if (addr < 0) {
                            errorInvalidPortName(arg)
                        }
                        ArgNumber(addr)
                    }
                    ArgType.CONST -> {
                        val io = getDevice().findIoWithBit(arg.name) ?: errorUnexpectedArgument(arg)
                        val value = io.getBitNumber(arg.name)
                        ArgNumber(value)
                    }
                    else -> errorUnexpectedArgument(arg)
                }
            }
            is FunctionCallNode -> {
                val proc = findProc(arg)
                for (callArg in arg.args) {
                    if (callArg !is CallArgumentNode) {
                        errorWrongArgument(callArg)
                    }
                    if (callArg.name == null && proc.args.size == 1) {
                        val procArg = proc.args[0]
                        compileAssignOp(this, procArg.expr, callArg.expr, callArg.getLocation())
                    } else {
                        val argName = callArg.name ?: errorWrongArgument(callArg)
                        val expr = proc.getArg(argName) ?: errorWrongArgument(callArg)
                        compileAssignOp(this, expr, callArg.expr, callArg.getLocation())
                    }
                }
                prepareAsmArg(IdentifierNode(arg.getLocation(), arg.name), type)
            }
            is RegisterGroupNode -> {
                resolveAsmPair(arg)
            }
            is SuffixOperNode -> {
                val group = arg.expression
                if (arg.operator == Operator.INC && group is RegisterGroupNode) {
                    when {
                        group.isX() -> ArgRegPair.X_PLUS
                        group.isY() -> ArgRegPair.Y_PLUS
                        group.isZ() -> ArgRegPair.Z_PLUS
                        else -> errorWrongArgument(arg)
                    }
                } else {
                    errorUnsupportedOperation(arg)
                }
            }
            is PrefixOperNode -> {
                val group = arg.expression
                if (arg.operator == Operator.DEC && group is RegisterGroupNode) {
                    when {
                        group.isX() -> ArgRegPair.MINUS_X
                        group.isY() -> ArgRegPair.MINUS_Y
                        group.isZ() -> ArgRegPair.MINUS_Z
                        else -> errorWrongArgument(arg)
                    }
                } else {
                    errorUnsupportedOperation(arg)
                }
            }
            is BinaryOperNode -> {
                val argLeft = resolveArg(arg.left)
                val argRight = resolveArg(arg.right)
                if (arg.operator == Operator.PLUS) {
                    if (argLeft is RegisterGroupNode && argLeft.isPair() && argRight is NumberNode) {
                        ArgOffsetPair(resolveAsmPair(argLeft), argRight.value)
                    } else if (argRight is RegisterGroupNode && argRight.isPair() && argLeft is NumberNode) {
                        ArgOffsetPair(resolveAsmPair(argRight), argLeft.value)
                    } else if (argLeft is VariableNode && argRight is NumberNode) {
                        ArgNumber(getVariableAddress(argLeft.variable) + argRight.value)
                    } else {
                        errorUnexpectedArgument(arg)
                    }
                } else if (arg.operator == Operator.MINUS) {
                    if (argLeft is VariableNode && argRight is NumberNode) {
                        ArgNumber(getVariableAddress(argLeft.variable) - argRight.value)
                    } else {
                        errorUnexpectedArgument(arg)
                    }
                } else {
                    errorUnexpectedArgument(arg)
                }
            }
            is IoPortNode -> {
                ArgNumber(arg.address)
            }
            is VariableNode -> {
                ArgNumber(getVariableAddress(arg.variable))
            }
            is ResolvedExternVar -> {
                ArgExtern(arg.name)
            }
            is ResolvedExternProc -> {
                val resolver = { currentOffset: Int, labelOffset: Int ->
                    errorUnexpectedArgument(arg)
//                    assert(currentOffset % 2 == 0)
//                    assert(labelOffset % 2 == 0)
//                    labelOffset / 2
                }
                ArgLabel(arg.name, arg.name, arg.getLocation(), resolver)
            }
            else -> {
                errorUnexpectedArgument(arg)
            }
        }
    }

    private fun resolveAsmPair(node: RegisterGroupNode): ArgRegPair {
        return when {
            node.isPair() -> when {
                node.isX() -> ArgRegPair.X
                node.isY() -> ArgRegPair.Y
                node.isZ() -> ArgRegPair.Z
                else -> errorWrongArgument(node)
            }
            node.isR25R24() -> ArgRegPair.R25_R24
            else -> errorWrongArgument(node)
        }
    }


    internal fun resolveProcLabel(name: String, location: SourceLocation, withLineNum: Boolean = false): String {
        val proc = currentProc
        if (proc != null) {
            return if (withLineNum) {
                "${proc.name}$inlineLabel@$name@${location.line}"
            } else {
                "${proc.name}@$name"
            }
        }
        return name
    }

    internal fun addProcLabel(name: String, location: SourceLocation, withLineNum: Boolean = false): String {
        val label = resolveProcLabel(name, location, withLineNum)
        addLabel(label, location)
        return label
    }

    private fun buildArgLabel(name: String, location: SourceLocation, withLineNum: Boolean,
                              resolver: (Int, Int) -> Int): ArgLabel {
        val localName = resolveProcLabel(name, location, withLineNum)
        return ArgLabel(name, localName, location, resolver)
    }

    private fun addProbeStartLabel(node: Node): String {
        val loc = node.getLocation()
        val name = "?probe_${loc.fileName.hashCode().absoluteValue}.${loc.line}_start"
        addLabel(name, loc)
        return name
    }

    private fun addProbeEndLabel(node: Node, startLabel: String, maxBytes: Int) {
        val loc = node.getLocation()
        val name = "?probe_${loc.fileName.hashCode().absoluteValue}.${loc.line}_end"
        addLabel(name, loc)
        addValidation(node.getLocation(), startLabel, name, maxBytes)
    }

    private fun getPinPortName(node: BinaryOperNode): String {
        if (node.left !is IdentifierNode || node.right !is IdentifierNode) {
            errorInvalidArgument(node)
        }
        val pinName = node.left.name
        val pinPort = node.right.name
        val pin = pins[pinName] ?: errorUndefinedPin(node.left)
        return when (pinPort) {
            "pin" -> "PIN${pin.port}"
            "port" -> "PORT${pin.port}"
            "ddr" -> "DDR${pin.port}"
            else -> errorWrongPinAccess(node.right)
        }
    }

    internal fun getPinPinNumber(node: BinaryOperNode): Int {
        if (node.left !is IdentifierNode) {
            errorInvalidArgument(node)
        }
        val pin = pins[node.left.name] ?: errorUndefinedPin(node.left)
        return pin.pin
    }

    internal fun getBitValue(node: NumberNode): Int {
        val value = node.value//calculateConst(node)
        if (value != 0 && value != 1) {
            errorWrongBitValue(node)
        }
        return value
    }

    internal fun resolveProcVar(name: String): Node? {
        val proc = currentProc ?: return null
        for (arg in proc.args) {
            if (arg.name == name) {
                return arg.expr
            }
        }
        return null
    }


    private fun getVariableAddress(v: Var): Int = v.offset + getDevice().ramStart

    internal fun findLabel(name: String): String? {
        val proc = currentProc
        if (proc != null) {
            for (node in proc.node.body) {
                if (node is LabelNode && node.name == name) {
                    return if (node.global) node.name else resolveProcLabel(node.name, node.getLocation())
                }
            }
        }
        val allNodes = ast?.nodes ?: return null
        for (node in allNodes) {
            if (node is LabelNode && node.name == name) {
                return name
            } else if (node is ProcNode && node.name == name) {
                return name
            }
        }
        return null
    }

    private fun compileDataBlock(node: DataBlockNode) {
        if (node.type != DataType.BYTE && node.type != DataType.WORD && node.type != DataType.DWORD) {
            errorWrongDataType(node)
        }
        var bytes = 0
        for (item in node) {
            when (item) {
                is StringLiteralNode -> {
                    if (node.type != DataType.BYTE) {
                        errorNumberExpected(item)
                    }
                    for (ch in item.str) {
                        // TODO encoding
                        val code = ch.toInt()
                        addByte(code, item.getLocation())
                        bytes++
                    }
                }
                is NumberNode -> {
                    when (node.type) {
                        DataType.BYTE -> {
                            addByte(item.value, item.getLocation())
                            bytes++
                        }
                        DataType.WORD -> {
                            addWord(item.value, item.getLocation())
                            bytes += 2
                        }
                        DataType.DWORD -> {
                            addDword(item.value, item.getLocation())
                            bytes += 4
                        }
                        else -> internalError()
                    }
                    // .dw 0x1234 -> 34 12
                }
                is CharLiteralNode -> {
                    if (node.type != DataType.BYTE) {
                        errorNumberExpected(item)
                    }
                    // TODO encoding
                    val code = item.char.toInt()
                    addByte(code, item.getLocation())
                }
                is IdentifierNode -> {
                    val label = findLabel(item.name) ?: errorUnexpectedArgument(item)
                    val arg = DeferredNumberArg(ProgramMemNode(item.getLocation(), label), this::resolveDeferred)
                    when (node.type) {
                        DataType.BYTE -> {
                            addByte(arg, item.getLocation())
                            bytes++
                        }
                        DataType.WORD -> {
                            addWord(arg, item.getLocation())
                            bytes += 2
                        }
                        DataType.DWORD -> {
                            addWord(arg, item.getLocation())
                            bytes += 4
                        }
                        else -> internalError()
                    }

//                        val labelNode = ProgramMemNode(loc, label)
//                        val rightNode = BinaryOperNode(right.operator, labelNode, opRight)
//                        val lowNode = BinaryOperNode(Operator.AND, rightNode, NumberNode(loc, 0xff))
//                        val highNode = BinaryOperNode(Operator.SHR, rightNode, NumberNode(loc, 8))
//                        compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(0), highNode, loc)
//                        compileAsmRegDeferredNum(AvrCmd.LDI, group.buildRegisterNode(1), lowNode, loc)

                }
                is BinaryOperNode -> {
                    if (item.left !is IdentifierNode) {
                        errorUnexpectedArgument(item)
                    }
                    val label = findLabel(item.left.name) ?: errorUnexpectedArgument(item)
                    val arg = BinaryOperNode(item.getLocation(), item.operator, ProgramMemNode(item.getLocation(), label), item.right)
                    val defArg = DeferredNumberArg(arg, this::resolveDeferred)

                    when (node.type) {
                        DataType.BYTE -> {
                            addByte(defArg, item.getLocation())
                            bytes++
                        }
                        DataType.WORD -> {
                            addWord(defArg, item.getLocation())
                            bytes += 2
                        }
                        DataType.DWORD -> {
                            addWord(defArg, item.getLocation())
                            bytes += 4
                        }
                        else -> internalError()
                    }

                }
                else -> errorUnexpectedArgument(item)
            }
        }
        if (node.type == DataType.BYTE && (bytes % 2) != 0) {
            addByte(0, node.getLocation())
        }
    }

    private fun compileExternProc(node: ExternProcNode) {
        if (gccAsmGen == null) {
            errorExternalAvailableOnlyForGcc(node)
        }
        val name = node.proc.name
        if (procs.containsKey(name)) {
            errorProcedureAlreadyDefined(node.proc)
        }
        if (externalProcs.containsKey(name)) {
            errorProcedureAlreadyDefined(node.proc)
        }
        externalProcs[name] = node.proc
        //gccAsmGen?.addExtern(name)
    }

    private fun compileExternVar(node: ExternVarNode) {
        if (gccAsmGen == null) {
            errorExternalAvailableOnlyForGcc(node)
        }
        for (vNode in node) {
            val variable = when (vNode) {
                is VarDeclarationNode -> {
                    val v = Var(vNode.name, vNode.dataType, null, -0xffff)
                    v
                }
                is DataArrayDeclarationNode -> {
                    val size = calculateConst(vNode.size)
                    val v = Var(vNode.name, vNode.dataType, size, -0xffff)
                    v
                }
                else -> internalError()
            }
            val name = variable.name
            if (vars.containsKey(name)) {
                errorVariableAlreadyExists(name, node)
            }
            if (externalVars.containsKey(name)) {
                errorVariableAlreadyExists(name, node)
            }
            externalVars[name] = variable
            gccAsmGen?.addExtern(name)
        }
    }


    private fun compileInlineCall(node: FunctionCallNode) {
        val proc = findProc(node)
        if (!proc.inline) {
            errorNotInlineProcCall(node)
        }
        for (prev in inlineCallStack) {
            if (prev.name == node.name) {
                errorRecursiveInlineCall(node)
            }
        }
        for (a in node.args) {
            if (a !is CallArgumentNode) {
                errorWrongArgument(a)
            }
            if (a.name == null && proc.args.size == 1) {
                val procArg = proc.args[0]
                compileAssignOp(this, procArg.expr, a.expr, a.getLocation())
            } else {
                for (callArgNode in node.args) {
                    val callArg = callArgNode as CallArgumentNode
                    val argName = callArg.name ?: errorWrongArgument(callArg)
                    val expr = proc.getArg(argName) ?: errorWrongArgument(callArg)
                    compileAssignOp(this, expr, callArg.expr, callArg.getLocation())
                }
            }
        }
        inlineCallStack.add(proc)
        inlineLabel += "_inl"
        compileNode(proc.body)
        inlineLabel = inlineLabel.substring(0, inlineLabel.length-1)
        inlineCallStack.removeAt(inlineCallStack.size-1)
    }


}



private fun errorUnknownAssemblerCommand(name: String, location: SourceLocation): Nothing {
    throw CompilerException("Unknown assembler instruction: $name", location)
}

private fun errorUnknownRegister(node: RegisterNode): Nothing {
    throw CompilerException("Unknown register instruction: ${node.reg}", node.getLocation())
}

internal fun errorInvalidArgument(node: Node): Nothing {
    throw CompilerException("Invalid argument", node.getLocation())
}

private fun errorUnknownVectorName(name: String, location: SourceLocation): Nothing {
    throw CompilerException("Unknown interrupt vector: $name", location)
}

private fun errorAssemblerInstructionExpected(node: Node): Nothing {
    throw CompilerException("Assembler instruction expected", node.getLocation())
}

private fun errorInvalidPin(pin: String, node: Node): Nothing {
    throw CompilerException("Invalid device pin: $pin", node.getLocation())
}

private fun errorInvalidBitNumber(bit: NumberNode): Nothing {
    throw CompilerException("Invalid bit number: ${bit.value}", bit.getLocation())
}

private fun errorInvalidBitNumber(bit: Int, loc: SourceLocation): Nothing {
    throw CompilerException("Invalid bit number: $bit", loc)
}

private fun errorPinAlreadyDefined(pin: String, node: Node): Nothing {
    throw CompilerException("Pin '$pin' already defined", node.getLocation())
}

private fun errorVariableAlreadyExists(name: String, node: Node): Nothing {
    throw CompilerException("Variable '$name' already exists", node.getLocation())
}

private fun errorWrongAssemblerInstruction(node: AssemblerInstrNode): Nothing {
    throw CompilerException("Hm.. Wrong assembler instruction: ${node.instr}", node.getLocation())
}

private fun errorRegisterExpected(node: RegisterNode): Nothing {
    throw CompilerException("Register expected: ${node.reg}", node.getLocation())
}

private fun errorTooManyArgumentsInAsmInstruction(node: AssemblerInstrNode): Nothing {
    throw CompilerException("Too many arguments", node.getLocation())
}

private fun errorUnexpectedArgument(node: Node): Nothing {
    throw CompilerException("Unexpected argument: $node", node.getLocation())
}

private fun errorInvalidPortName(node: IdentifierNode) {
    throw CompilerException("Invalid or unknown port name: ${node.name}", node.getLocation())
}

private fun errorUndefinedPin(node: IdentifierNode): Nothing {
    throw CompilerException("Undefined pin: $node", node.getLocation())
}

private fun errorWrongPinAccess(node: IdentifierNode): Nothing {
    throw CompilerException("Wrong pin syntax: 'pin', 'port' or 'ddr' expected", node.getLocation())
}

private fun errorWrongBitValue(node: Node): Nothing {
    throw CompilerException("1 or 0 expected, but $node found", node.getLocation())
}

internal fun errorRegisterExpected(node: Node): Nothing {
    throw CompilerException("Register expected: $node", node.getLocation())
}

internal fun errorRegisterOrPairExpected(node: Node): Nothing {
    throw CompilerException("Register or pair expected: $node", node.getLocation())
}

internal fun errorWrongArgument(node: Node): Nothing {
    throw CompilerException("Wrong argument: $node", node.getLocation())
}

private fun errorProcedureAlreadyDefined(node: ProcNode): Nothing {
    throw CompilerException("Procedure already defined: ${node.name}", node.getLocation())
}

private fun errorProcedureNotFound(node: FunctionCallNode): Nothing {
    throw CompilerException("Procedure not found: ${node.name}", node.getLocation())
}

internal fun errorInvalidBitNumber(bit: Int, node: Node): Nothing {
    throw CompilerException("Invalid bit number (0..7 expected): $bit", node.getLocation())
}

internal fun errorSizeMismatch(location: SourceLocation, left: Int, right: Int): Nothing {
    throw CompilerException("Operands has different sizes: $left and $right", location)
}

private fun errorWrongIoPortBit(port: IoPortNode, bit: String): Nothing {
    throw CompilerException("Undefined: ${port.name}->$bit", port.getLocation())
}

private fun errorGlobalUseAlreadyDefined(node: UseNode): Nothing {
    throw CompilerException("Global alias ${node.name} already defined", node.getLocation())
}

private fun errorProcUseAlreadyDefined(node: UseNode): Nothing {
    throw CompilerException("Local alias ${node.name} already defined", node.getLocation())
}

internal fun errorUnsupportedOperation(location: SourceLocation): Nothing {
    throw CompilerException("Unsupported operation", location)
}

internal fun errorUnsupportedOperation(node: Node): Nothing {
    errorUnsupportedOperation(node.getLocation())
}

internal fun errorVariableNotFound(node: Node): Nothing {
    throw CompilerException("Variable not found: $node", node.getLocation())
}

private fun errorInvalidDirective(node: DirectiveNode): Nothing {
    throw CompilerException("Invalid directive: ${node.name}", node.getLocation())
}

private fun errorArgumentExpected(node: DirectiveNode): Nothing {
    throw CompilerException("Argument expected", node.getLocation())
}

private fun errorTooManyArgumentsInDirective(node: DirectiveNode): Nothing {
    throw CompilerException("Extra arguments in directive", node.getLocation())
}

private fun errorEvenValueExpected(node: DirectiveNode): Nothing {
    throw CompilerException("Even value expected", node.getLocation())
}

private fun errorWrongDataType(node: DataBlockNode) {
    throw CompilerException("Wrong data type: ${node.type.toString().toLowerCase()}", node.getLocation())
}

private fun errorLabelNotFound(node: ProgramMemNode): Nothing {
    throw CompilerException("Label not found: ${node.label}", node.getLocation())
}

internal fun errorParentCycleNotFound(node: Node): Nothing {
    throw CompilerException("Parent cycle not found", node.getLocation())
}

private fun errorNotInlineProcCall(node: FunctionCallNode) {
    throw CompilerException("Not inline function call", node.getLocation())
}

private fun errorUnexpectedDirective(node: DirectiveNode) {
    throw CompilerException("Unexpected directive", node.getLocation())
}

private fun errorRecursiveInlineCall(node: FunctionCallNode) {
    throw CompilerException("Recursive inline call", node.getLocation())
}

private fun errorSingleInstructionExpected(node: Node, commandsInBlock: Int): Nothing {
    throw CompilerException("Single instructions expected (but $commandsInBlock found)", node.getLocation())
}

private fun errorTooManyCommandsInBlock(node: Node, maxCommands: Int, commandsInBlock: Int): Nothing {
    throw CompilerException("Block too large: expected $maxCommands instructions but found $commandsInBlock", node.getLocation())
}

private fun errorExternalAvailableOnlyForGcc(node: Node): Nothing  {
    throw CompilerException("External objects can be used only in GCC-mode", node.getLocation())
}

internal fun errorValueTooBig(node: Node): Nothing {
    throw CompilerException("Value too big: $node", node.getLocation())
}

internal fun errorLowIoExpected(node: Node): Nothing {
    throw CompilerException("Low (0..31) address expected", node.getLocation())
}
