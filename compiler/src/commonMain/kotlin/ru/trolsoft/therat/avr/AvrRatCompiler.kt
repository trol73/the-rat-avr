package ru.trolsoft.therat.avr

import ru.trolsoft.compiler.compiler.*
import ru.trolsoft.compiler.general.*
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.arch.*
import ru.trolsoft.therat.arch.avr.*
import ru.trolsoft.therat.avr.compiler.bus.parseBusDefinition
import ru.trolsoft.therat.avr.compiler.compileAssignNodeToRegister
import ru.trolsoft.therat.avr.compiler.compileAssignOp
import ru.trolsoft.therat.avr.compiler.compileAssignRegisterTo
import ru.trolsoft.therat.avr.compiler.compileBinaryOp
import ru.trolsoft.therat.avr.compiler.compileBuiltin
import ru.trolsoft.therat.avr.compiler.compileDoWhile
import ru.trolsoft.therat.avr.compiler.compileIf
import ru.trolsoft.therat.avr.compiler.compileLoop
import ru.trolsoft.therat.avr.compiler.compileSuffixOp
import ru.trolsoft.therat.avr.compiler.isBuiltin
import kotlinx.io.RawSink
import ru.trolsoft.therat.lang.IdentifierToken
import kotlin.collections.get
import kotlin.math.abs
import kotlin.math.absoluteValue

open class AvrRatCompiler(
    private val devPath: String,
    encodingsPath: String,
    console: ConsoleReporter,
    fileLoader: FileLoader? = null,
    warningListener: ((String) -> Unit)? = null
) : Compiler(
    AvrScanner(), encodingsPath, console, fileLoader, warningListener
) {
    var deviceLoader: FileLoader? = null

    private var gccAsmGen: AvrGccAsmFileGenerator? = null
    internal val pins = mutableMapOf<String, Pin>()
    internal val buses = mutableMapOf<String, List<Pin>>()
    private val uses = mutableMapOf<String, Use>()
    private val structures = mutableMapOf<String, StructuredData>()
    private val constValues = mutableMapOf<String, Node>()
    private var _device: AvrDevice? = null
    private var variableOffset: Int = 0
    private var pendingPadToNode: DirectiveNode? = null



    private var ast: AvrAstNode? = null

    override fun parse(stream: TokenStream): AvrAstNode = AvrRatParser(stream).parse()


    override fun compile(ast: AstNode) {
        this.ast = ast as AvrAstNode
        this.structures.putAll(ast.structures)
        buildVectorsTable(ast)
        preloadProcedures(ast)
        for (node in ast) {
            when (node) {
                is ProcNode -> compileProc(node)
                is PinDeclarationNode -> processPin(node)
                is BusDeclarationNode -> processBus(node)
                is VarDeclarationNode -> processVar(node)
                is DataArrayDeclarationNode -> processVar(node)
                is UseNode -> processUse(node)
                is DirectiveNode -> processDirective(node)
                is LabelNode -> addProcLabel(node.name, node.getLocation())
                is DataBlockNode -> compileDataBlock(node, true)
                is ExternVarNode -> compileExternVar(node)
                is ExternProcNode -> compileExternProc(node)
                is ConstValueNode -> processConst(node)
                else -> TODO("unexpected node: $node")
            }
        }
    }


    fun addGccAsmGenerator() {
        val gen = AvrGccAsmFileGenerator()
        gccAsmGen = gen
        addGenerator(gen)
    }

    fun saveGccAsm(stream: RawSink) = gccAsmGen!!.write(stream)
    fun saveGccAsm(path: String) = gccAsmGen!!.write(path)

    override fun calculateConst(node: Node): Int {
        val resolved = resolveArg(node)
        return super.calculateConst(resolved)
    }

    override fun resolveArg(arg: Node): Node {
        when {
            arg is IdentifierNode -> {
                val res = resolveIdentifierArg(arg)
                return if (res is RegisterNode && isAvrPairName(res.reg)) {
                    resolveArg(res)
                } else if (res is DotGroupNode) {
                    resolveArg(res)
                } else {
                    res
                }
            }

            arg is RegisterNode -> {
                // Check local consts first (they shadow global consts)
                currentProc?.getConst(arg.reg)?.let { localConst ->
                    val resolved = resolveArg(localConst)
                    return resolveArg(resolved)
                }
                // Check global consts
                if (constValues.containsKey(arg.reg)) {
                    val constExpr = resolveArg(constValues[arg.reg]!!)
                    return resolveArg(constExpr)
                }
                if (isPartOfXYZ(arg.reg)) {
                    return RegisterNode(arg.getLocation(), resolvePartOfXYZ(arg.reg))
                } else if (isAvrPairName(arg.reg)) {
                    val pairName = arg.reg.lowercase()
                    val high = resolvePartOfXYZ(pairName + "h")
                    val low = resolvePartOfXYZ(pairName + "l")
                    val loc = arg.getLocation()
                    return RegisterGroupNode(loc, listOf(high, low))
                }
            }

            arg is FunctionCallNode && arg.isBuiltin() -> {
                return arg.compileBuiltin(this)
            }

            arg is CharLiteralNode -> return NumberNode(arg.getLocation(), resolveChar(arg))

            arg is BinaryOperNode -> {
                if (arg.operator == Operator.ARROW_RIGHT) {
                    return resolveRightArrowed(arg)
                } else {
                    val left = resolveArg(arg.left)
                    val right = resolveArg(arg.right)
                    if (left is NumberNode && right is NumberNode) {
                        return NumberNode(arg.getLocation(), super.calculateConst(arg))
                    }
                }
            }

            arg is IndexedNode -> {
                return resolveIndexedArg(arg)
            }

            arg is IndexedRegNode -> {
                when (arg.index) {
                    is NumberNode -> return makeRegisterBitNode(arg.reg, arg.index.value)
                    is IdentifierNode -> {
                        val pin = pins[arg.index.name] ?: errorUnsupportedOperation(arg)
                        return makeRegisterBitNode(arg.reg, pin.pin)
                    }
                }
            }

            arg is SuffixOperNode -> {
                return SuffixOperNode(arg.operator, resolveArg(arg.expression))
            }

            arg is PrefixOperNode -> {
                val res = PrefixOperNode(arg.getLocation(), arg.operator, resolveArg(arg.expression))
                if (res.expression is NumberNode && res.operator == Operator.BINARY_NOT) {
                    return NumberNode(res.getLocation(), super.calculateConst(res))
                }
                return res
            }

            arg is DotGroupNode -> {
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

            arg is IndexedNumNode -> {
                val num = resolveArg(arg.num)
                val index = resolveArg(arg.index)
                if (num is NumberNode && index is NumberNode && index.value >= 0 && index.value < 32) {
                    val mask = 1 shl index.value
                    val bitVal = if (abs(num.value) and mask != 0) 1 else 0
                    return NumberNode(num.getLocation(), bitVal, 10)
                }
                return arg
            }
        }
        return arg
    }

    private fun resolveRightArrowed(arg: BinaryOperNode): Node {
        val left = resolveArg(arg.left)
        val right = arg.right as? IdentifierNode ?: resolveArg(arg.right)

        val fieldName: String? = when (right) {
            is IdentifierNode -> right.name
            is IndexedNode -> right.name.name
            else -> null
        }
        when {
            left is IndexedNode && left.index is NumberNode -> {
                val index = left.index.value
                val variable = resolveArg(left.name)
                if (variable is VariableNode && variable.variable.dataType == DataType.STRUCTURED && right is IdentifierNode) {
                    val subFieldName = right.name
                    val structName = variable.variable.structName
                    val struct = getStruct(structName) ?: internalError()
                    val offset = struct.getOffset(subFieldName)
                    if (offset < 0) errorWrongFieldName(subFieldName, struct.name, right.getLocation())
                    val field = struct.getItem(subFieldName) ?: internalError()
                    val address = variable.address + offset + struct.getSize() * index
                    val loc = arg.getLocation()
                    return resolveStructVarField(field, loc, address, right)
                }
            }

            left is VariableNode && fieldName != null -> {
                val name = left.variable.name
                val variable = getVar(name)
                if (variable != null && variable.dataType == DataType.STRUCTURED) {
                    val structName = variable.structName ?: internalError()
                    val struct = structures[structName] ?: internalError()
                    val offset = struct.getOffset(fieldName)
                    val field =
                        struct.getItem(fieldName) ?: errorWrongFieldName(fieldName, struct.name, right.getLocation())
                    val address = getVariableAddress(variable) + offset
                    val loc = arg.getLocation()
                    return resolveStructVarField(field, loc, address, right)
                }
            }

            left is ResolvedExternVar && left.struct != null && fieldName != null -> {
                return resolveFieldOfExternalStruct(left, fieldName, right, left.struct)
            }

            left is InternalStructVarNode && right is IdentifierNode -> {
                val subFieldName = right.name
                val struct = left.struct
                val offset = struct.getOffset(subFieldName)
                val field =
                    struct.getItem(subFieldName) ?: errorWrongFieldName(subFieldName, struct.name, right.getLocation())
                val address = left.address + offset
                val loc = arg.getLocation()
                return resolveStructVarField(field, loc, address, right)
            }

            left is InternalStructVarNode && right is IndexedNode -> {
                val subFieldName = right.name.name
                val struct = left.struct
                val offset = struct.getOffset(subFieldName)
                if (offset < 0) internalError()
                val field =
                    struct.getItem(subFieldName) ?: errorWrongFieldName(subFieldName, struct.name, right.getLocation())
                val address = left.address + offset
                val loc = arg.getLocation()
                return resolveStructVarField(field, loc, address, right)
            }
        }

        return resolvePort(arg)
    }

    private fun resolveFieldOfExternalStruct(
        structVar: ResolvedExternVar,
        fieldName: String,
        right: Node,
        struct: StructuredData
    ): ResolvedExternVar {
        val name = structVar.name
        val offset = struct.getOffset(fieldName)
        if (offset < 0) {
            errorWrongFieldName(fieldName, struct.name, right.getLocation())
        }
        when (val item = struct.getItem(fieldName)) {
            is PrimitiveItem -> {
                return ResolvedExternVar(
                    structVar.getLocation(),
                    name,
                    null,
                    item.dataType,
                    struct
                ).modifyName(offset)
            }

            is PointerItem -> {
                return ResolvedExternVar(
                    structVar.getLocation(),
                    name,
                    null,
                    DataType.PTR,
                    struct
                ).modifyName(offset)
            }

            is ArrayItem -> {
                when (right) {
                    is IndexedNode if right.index is NumberNode -> {
                        val index = right.index
                        when (val source = item.source) {
                            is PrimitiveItem -> {
                                return ResolvedExternVar(
                                    structVar.getLocation(),
                                    name,
                                    null,
                                    source.dataType,
                                    null
                                ).modifyName(offset + index.value * source.dataType.ramSize)
                            }

                            is StructItem -> {
                                return ResolvedExternVar(
                                    structVar.getLocation(),
                                    name,
                                    null,
                                    DataType.STRUCTURED,
                                    source.structuredData
                                ).modifyName(offset + index.value * source.structuredData.getSize())
                            }

                            else -> errorUnsupportedOperation(right)
                        }
                    }

                    is IdentifierNode -> {

                        return ResolvedExternVar(
                            structVar.getLocation(),
                            name,
                            null,
                            DataType.PTR,
                            null
                        ).modifyName(offset)
                    }

                    else -> {
                        internalError()
                    }
                }
            }

            is StructItem -> {
                if (right is IdentifierNode && structVar.struct != null) {
                    when (val itemType = structVar.struct.getItem(right.name)) {
                        is StructItem ->
                            return ResolvedExternVar(
                                right.getLocation(),
                                name,
                                null,
                                DataType.STRUCTURED,
                                itemType.structuredData
                            ).modifyName(offset)// + itemOffset)
                        else -> {
                            errorUnsupportedOperation(right)
                        }
                    }
                } else {
                    errorUnsupportedOperation(right)
                }
            }

            null -> {
                internalError()
            }
        }
    }

    private fun resolveStructVarField(
        field: DataItem,
        loc: SourceLocation,
        address: Int,
        node: Node,
    ) = when (field) {
        is PrimitiveItem ->
            buildVariable(field.name, field.dataType, loc, address)

        is ArrayItem ->
            if (node is IndexedNode && node.index is NumberNode) {
                val index = node.index.value
                when (field.source) {
                    is PrimitiveItem -> {
                        val dataType = field.source.dataType
                        buildVariable(field.name, dataType, loc, address + index * dataType.ramSize)
                    }

                    is StructItem -> {
                        InternalStructVarNode(loc, address + index * field.source.size, field.source.structuredData)
                    }

                    else -> {
                        errorUnsupportedOperation(node)
                    }
                }
            } else if (node is IdentifierNode) {
                VariableNode(loc, Var(field.name, DataType.PTR, 0, 0, null), address)
            } else {
                internalError()
            }

        is PointerItem ->
            VariableNode(loc, Var(field.name, DataType.PTR, 0, 0, null), address)

        is StructItem -> {
            InternalStructVarNode(loc, address, field.structuredData)
        }
    }

    private fun buildVariable(name: String, dataType: DataType, loc: SourceLocation, address: Int) =
        VariableNode(loc, Var(name, dataType, null, 0, null), address)

    private fun resolveIndexedArg(arg: IndexedNode): Node {
        val resolvedName = resolveArg(arg.name)
        if (resolvedName is RegisterGroupNode && arg.index is NumberNode) {
            if (arg.index.value < 0) {
                errorInvalidBitNumber(arg.index)
            }
            val bit = arg.index.value
            val regNum = resolvedName.size() - 1 - bit / 8
            return RegisterBitNode(resolvedName.getReg(regNum), bit % 8)
        }
        when (arg.name.name) {
            "mem" -> return MemArrayNode(arg.getLocation(), resolveArg(arg.index))
            "prg" -> return PrgArrayNode(arg.getLocation(), resolveArg(arg.index))
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

                    is VariableNode -> {
                        val v = name.variable
                        return if (index is NumberNode && v.arraySize != null && v.structName == null) {
                            val address = getVariableAddress(v) + index.value * v.dataType.ramSize
                            VariableNode(
                                arg.getLocation(),
                                Var(
                                    "${v.name}[${index.value}]",
                                    v.dataType,
                                    null,
                                    address,
                                    null
                                ),
                                address
                            )
                        } else {
                            arg
                        }
                    }

                    is ResolvedExternVar -> {
                        if (index !is NumberNode) {
                            errorUnsupportedOperation(index)
                        }
                        val itemSize = when (val type = name.dataType) {
                            DataType.PTR -> 1
                            DataType.STRUCTURED -> {
                                name.struct!!.getSize()
                            }

                            DataType.PRGPTR -> errorUnsupportedOperation(name)
                            else -> type.ramSize
                        }
                        return name.modifyName(index.value * itemSize)
                    }

                    else -> return arg//errorUndefinedIdentifier(arg.name)
                }
            }
        }
        return arg
    }

    private fun makeRegisterBitNode(reg: RegisterNode, bit: Int): RegisterBitNode {
        val resolved = resolveArg(reg)
        if (resolved is RegisterGroupNode) {
            if (bit < 0 || bit >= resolved.size() * 8) {
                errorInvalidBitNumber(bit, reg.getLocation())
            }
            val bitIndex = bit % 8
            val regIndex = resolved.size() - 1 - (bit / 8)
            return makeRegisterBitNode(resolved.getReg(regIndex), bitIndex)
        }
        if (bit !in 0..7) {
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
        when {
            inlineCallStack.isNotEmpty() && inlineArg != null -> {
                return resolveArg(inlineArg)
            }

            proc != null && proc.hasUse(arg.name) -> {
                return proc.getUse(arg.name)!!.reg
            }

            proc != null && proc.hasArg(arg) -> {
                val procArg = proc.getArg(arg) ?: internalError()
                return resolveArg(procArg)
            }

            uses.containsKey(arg.name) -> {
                return uses[arg.name]?.reg ?: internalError()
            }

            getVar(arg.name) != null -> {
                val variable = getVar(arg.name)!!
                return VariableNode(arg.getLocation(), variable, getVariableAddress(variable))
            }

            getExternVar(arg.name) != null -> {
                val v = getExternVar(arg.name)!!
                val struct = structures[v.structName]
                return ResolvedExternVar(arg.getLocation(), arg.name, v.arraySize, v.dataType, struct)
            }

            getExternProc(arg.name) != null -> {
                val p = getExternProc(arg.name)!!
                return ResolvedExternProc(arg.getLocation(), arg.name, p.args)
            }

            // Local consts shadow global consts
            proc != null && proc.hasConst(arg.name) -> {
                val localConst = resolveArg(proc.getConst(arg.name)!!)
                return resolveArg(localConst)
            }

            constValues.containsKey(arg.name) -> {
                val expr = resolveArg(constValues[arg.name]!!)
                return resolveArg(expr)
            }

            else -> {
                val ioAddress = getIoAddress(arg.name)
                if (ioAddress >= 0) {
                    val io = findIo(arg.name)
                    val ioSize = io?.size ?: 1
                    val ioRegs = io?.bits ?: listOf()
                    // TODO !!! надо правильно получать список битов для многобайтовых портов
                    return IoPortNode(arg.getLocation(), arg.name, ioAddress, ioSize, ioRegs)
                }

            }
        }
        return arg
    }

    private fun resolvePort(arg: BinaryOperNode): Node {
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
                if (bitNum !in 0..7) {
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


    private fun getIoAddress(name: String): Int = getDevice().getIoAddr(name)
    private fun findIo(name: String): IoRegister? = getDevice().findIo(name)
    internal fun findIoWithBit(bitName: String): IoRegister? = getDevice().findIoWithBit(bitName)
    fun getStruct(name: String?): StructuredData? = structures[name]

    private fun buildVectorsTable(ast: AvrAstNode) {
        val vectors = ast.vectors ?: return
        val allDeviceVectors = getDevice().interrupts
        require(allDeviceVectors.size() == allDeviceVectors.vectors.last().offset + 1)
        checkVectorsTable(vectors, allDeviceVectors)
        val definedDefaultNode = vectors.getVectorNode("default")
        val defaultNode = when {
            definedDefaultNode != null -> definedDefaultNode
            getVectorSizeInWords() == 1 -> AssemblerInstrNode(vectors.getLocation(), "reti", null)
            else -> CompoundStmt(listOf(
                AssemblerInstrNode(vectors.getLocation(), "reti", null),
                AssemblerInstrNode(vectors.getLocation(), "nop", null)
            ))
        }

        for (v in allDeviceVectors) {
            if (v.name.uppercase() == "RESET" && vectors.getVectorNode(v.name) == null) {
                val mainProc = getMainProc(ast)
                if (mainProc != null) {
                    compileJump("rjmp", "main", vectors.getLocation(), v.name)
                    if (getVectorSizeInWords() == 2) {
                        compileAsm("nop", vectors.getLocation())
                    }
                    continue
                }
            }
            val node = vectors.getVectorNode(v.name) ?: defaultNode
            val beforeSize = totalCommands
            compileNode(node)
            val nodeSize = totalCommands - beforeSize
            if (nodeSize != getVectorSizeInWords()) {
                errorWrongInterruptNodeSize(nodeSize, getVectorSizeInWords(), node.getLocation())
            }
            // TODO check 2 byte size
            //compileAssembler(node, v.name)
        }
        addLine("\n")
    }

    private fun getVectorSizeInWords() = if (getDevice().flashSize <= 8 * 1024) 1 else 2


    /**
     * cmd: "rjmp", "jmp", "rcall", "call"
     */
    internal fun compileJump(cmd: String, label: String, location: SourceLocation, comment: String? = null) {
        val argNode = IdentifierNode(location, label)
        val jmp = AssemblerInstrNode(location, cmd, listOf(argNode))
        compileAssembler(jmp, comment)
    }


    internal fun compileAsmRegDeferredNum(
        cmd: AsmCmd,
        reg1: RegisterNode,
        numNode: Node,
        location: SourceLocation,
        comment: String? = null
    ) {
        if (cmd.isHighRegCmd() && !isAvrUpperRegister(reg1.reg)) {
            errorHighRegisterExpected(reg1)
        }
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
                NumberNode(node.getLocation(), offset)
            }

            is BinaryOperNode -> {
                val left = resolveDeferredNode(node.left, labelResolver)
                val right = resolveDeferredNode(node.right, labelResolver)
                NumberNode(
                    node.getLocation(),
                    calculateConst(BinaryOperNode(node.getLocation(), node.operator, left, right))
                )
            }

            else -> resolveArg(node)
        }
    }

    internal fun compileAsmPairNum(
        cmd: String,
        pair1: RegisterGroupNode,
        num2: Int,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(pair1, NumberNode(location, num2)))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmNumReg(
        cmd: String,
        num1: NumberNode,
        reg2: RegisterNode,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(num1, reg2))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmNumReg(
        cmd: String,
        num1: Int,
        reg2: RegisterNode,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(NumberNode(location, num1), reg2))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmPairReg(
        cmd: String,
        pair: Node,
        reg: RegisterNode,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(pair, reg))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegPair(
        cmd: String,
        reg: RegisterNode,
        pair: Node,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(reg, pair))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmExtReg(
        cmd: String,
        extern: ResolvedExternVar,
        reg: RegisterNode,
        loc: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(loc, cmd, listOf(extern, reg))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmRegExt(
        cmd: String,
        reg: RegisterNode,
        extern: ResolvedExternVar,
        loc: SourceLocation,
        comment: String? = null
    ) {
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
        val name =
            ppc.getStringMacro("CPU") ?: throw CompilerException("'CPU' macro isn't defined", ast?.getLocation())
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
        val alias = pins[node.devicePin]
        val isAlias: Boolean
        val ps = if (alias != null) {
            isAlias = true
            "${alias.port}${alias.pin}"
        } else if (node.devicePin.length != 2) {
            errorInvalidPin(node.devicePin, node)
        } else {
            isAlias = false
            node.devicePin
        }
        val portChar = ps[0]
        val numChar = ps[1]
        if (numChar !in '0'..'7') {
            errorInvalidPin(node.devicePin, node)
        }
        val pinBit = numChar.toString().toInt()
        if (!dev.hasPort(portChar)) {
            throw CompilerException("Device ${dev.name} doesn't have port $portChar", node.getLocation())
        }
        if (pins.containsKey(node.name)) {
            errorPinAlreadyDefined(node.name, node)
        }
        if (!isAlias) {
            for (pin in pins.values) {
                if (pin.pin == pinBit && pin.port == portChar) {
                    warning("Pin ${node.devicePin} already defined", node)
                }
            }
        }
        pins[node.name] = Pin(portChar, pinBit)
    }

    private fun processBus(node: BusDeclarationNode) {
        if (buses.containsKey(node.name)) {
            errorBusAlreadyDefined(node.name, node)
        }
        val dev = getDevice()
        parseBusDefinition(this, node, dev)
    }

    private fun processVar(node: Node) {
        val dirOffset = directiveOffset
        if (dirOffset != null) {
            variableOffset = dirOffset
            directiveOffset = null
            directiveOffsetNode = null
        }

        val variable = when (node) {
            is VarDeclarationNode -> {
                val v = Var(node.name, node.dataType, null, variableOffset, node.structName)
                variableOffset += v.getRamSize()

                if (variableOffset > getDevice().ramSize) errorNoRam(variableOffset, getDevice().ramSize, node.getLocation())
                v
            }

            is DataArrayDeclarationNode -> {
                val size = calculateConst(node.size)
                val v = Var(node.name, node.dataType, size, variableOffset, node.structName)
                variableOffset += v.getRamSize()
                if (variableOffset > getDevice().ramSize) errorNoRam(variableOffset, getDevice().ramSize, node.getLocation())
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
        val gcc = gccAsmGen
        val name = variable.name
        if (gcc != null) {
            gcc.addVar(name, variable.ramSize())
            if (!name.startsWith("_")) {
                gcc.addGlobal(name)
            }
            externalVars[name] = variable
        } else {
            vars[name] = variable
        }
    }

    private fun Var.getRamSize() =
        if (dataType != DataType.STRUCTURED) {
            ramSize()
        } else {
            val varSize = structures[structName]?.getSize() ?:internalError()
            varSize * (arraySize ?: 1)
        }

    private fun processUse(node: UseNode) {
        val resolvedReg = if (node.reg is IdentifierNode) {
            val name = node.reg.name
            if (uses.containsKey(name)) {
                uses[name]?.reg ?: node.reg
            } else if (currentProc != null && currentProc!!.hasUse(name)) {
                currentProc!!.getUse(name)!!.reg
            } else {
                node.reg
            }
        } else {
            node.reg
        }

        val use = Use(node.name, resolvedReg, node.capture)

        val proc = currentProc
        if (node.isReuse) {
            // reuse is only allowed inside procedures
            if (proc == null) {
                errorReuseOnlyInProc(node)
            }
            // Procedure scope: remove only local aliases pointing to the same register
            val procToRemove = proc.getUses().entries.filter { (_, u) -> isSameRegister(u.reg, resolvedReg) }
                .map { it.key }.toSet()

            // Check if a global alias points to the same register (cannot remove global from proc scope)
            val globalHasSameReg = uses.any { (_, u) -> isSameRegister(u.reg, resolvedReg) }

            // Check if there is any alias pointing to this register (global or local)
            val hasAnyAliasForReg = procToRemove.isNotEmpty() || globalHasSameReg
            if (!hasAnyAliasForReg) {
                errorReuseNoAliasForRegister(node)
            }

            // If the only alias for this register is global, we cannot remove it
            if (globalHasSameReg && procToRemove.isEmpty()) {
                errorReuseNoAliasForRegister(node)
            }

            // Check if alias with this name still exists (points to a different register)
            // This check must consider both local and global aliases
            val hasAliasWithSameName = proc.hasUse(use.name) || uses.containsKey(use.name)
            if (hasAliasWithSameName) {
                errorProcUseAlreadyDefined(node)
            }

            // Check if the alias with this name is the one being removed (meaningless reuse)
            if (procToRemove.contains(use.name)) {
                errorProcUseAlreadyDefined(node)
            }

            procToRemove.forEach { proc.removeUse(it) }
            proc.addUse(use)
        } else {
            // Regular use: error if alias with this name already exists
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
                    warning("Name shadowed: ${use.name}", node)
                }
                proc.addUse(use)
            }
        }
    }

    private fun isSameRegister(a: Node, b: Node): Boolean {
        if (a === b) return true
        if (a is RegisterNode && b is RegisterNode) {
            return a.reg == b.reg
        }
        return false
    }

    private fun processSaveregs(node: SaveregsNode) {
        val regs = mutableListOf<Node>()
        for ((i, arg) in node.args.withIndex()) {
            val reg = resolveArg(arg)
            when {
                reg is RegisterNode -> {
                    compileAsmReg("push", reg, arg.getLocation())
                    regs.add(reg)
                }
                reg is RegisterGroupNode -> {
                    val cmdNode = AssemblerInstrNode(arg.getLocation(), "push", listOf(reg))
                    compileAssembler(cmdNode)
                    for (i in 0..<reg.size()) {
                        regs.add(reg.getReg(i))
                    }
                }
                reg is IoPortNode && reg.name == "SREG" -> {
                    if (i == 0) {
                        errorSregCannotBeFirstHere(arg)
                    }
                    val prevReg = regs[i - 1]
                    if (prevReg !is RegisterNode) {
                        errorSregCannotBeFirstHere(arg)
                    }
                    compileAssignRegisterTo(prevReg, reg, arg.getLocation())
                    compileAsmReg("push", prevReg, arg.getLocation())
                    regs.add(reg)
                }
                else -> errorUnexpectedArgument(arg)
            }
        }
        compileNode(node.body)
        for ((i, arg) in regs.reversed().withIndex()) {
            when (arg) {
                is RegisterNode -> {
                    compileAsmReg("pop", arg, arg.getLocation())
                }
                is RegisterGroupNode -> {
                    val cmdNode = AssemblerInstrNode(arg.getLocation(), "pop", listOf(arg))
                    compileAssembler(cmdNode)
                }
                is IoPortNode -> {
                    val prevReg = regs.reversed()[i + 1]
                    if (prevReg !is RegisterNode) {
                        internalError()
                    }
                    compileAsmReg("pop", prevReg, arg.getLocation())
                    compileAssignNodeToRegister(arg, prevReg, arg.getLocation())
                }

                else -> internalError()
            }
        }
    }

    private fun processBreak(node: BreakNode) {
        val endLabel = getCycleEndLabel() ?: errorParentCycleNotFound(node)
        compileJump("rjmp", endLabel, node.getLocation())
    }

    private fun processDirective(node: DirectiveNode) {
        when (node.name) {
            "org" -> processOrgDirective(node)
            "encoding" -> processEncodingDirective(node)
            "output" -> processOutputDirective(node)
            "cleanup" -> processCleanupDirective(node)
            "pad_to" -> processPadToDirective(node)
            else -> errorInvalidDirective(node)
        }
    }

    private fun processConst(node: ConstValueNode) {
        if (constValues.containsKey(node.name)) {
            errorConstantAlreadyDefined(node.name, node)
        }
        constValues[node.name] = node.expr
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

    private fun processPadToDirective(node: DirectiveNode) {
        val args = node.args ?: errorArgumentExpected(node)
        if (args.size != 1) {
            errorTooManyArgumentsInDirective(node)
        }
        val arg = calculateConst(args[0])
        if (arg < 0) {
            errorValueTooBig(NumberNode(node.getLocation(), arg))
        }
        pendingPadToNode = node
    }

    private fun processCleanupDirective(node: DirectiveNode) {
        var cleanupPins = false
        var cleanupExternal = false
        var cleanupUses = false
        val args = node.args
        if (args.isNullOrEmpty()) {
            cleanupPins = true
            cleanupExternal = true
            cleanupUses = true
        } else {
            for (a in args) {
                if (a !is StringLiteralNode) {
                    errorStringExpected(a)
                }
                when (a.str) {
                    "pins" -> cleanupPins = true
                    "external" -> cleanupExternal = true
                    "uses" -> cleanupUses = true
                    else -> errorWrongArgument(a)
                }
            }
        }
        if (cleanupPins) {
            pins.clear()
        }
        if (cleanupExternal) {
            externalVars.clear()
        }
        if (cleanupUses) {
            uses.clear()
        }
    }

    private fun compileProc(proc: ProcNode) {
        handleOrgDirective(proc)
        if (!proc.inline) {
            val location = proc.getLocation()
            generateStartProc(proc.name, location)
            addLabel(proc.name, location)
            if (!proc.name.startsWith("_")) {
                gccAsmGen?.addGlobal(proc.name)
            }
            currentProc = Procedure(proc.name, proc.args, proc.results, proc)
            // Process local const declarations in the procedure body
            for (bodyNode in proc.body) {
                if (bodyNode is ConstValueNode) {
                    currentProc!!.addConst(bodyNode.name, bodyNode.expr)
                }
            }
            val bodyStartCommands = totalCommands
            compileNode(proc.body)
            addLine("\n")
            currentProc = null
            checkProcedureRet(proc)
            generateEndProc(proc.name, location)

            handlePadToDirective(bodyStartCommands, location, proc)
        }
        checkProcArgs(proc.args)
    }

    private fun handleOrgDirective(proc: ProcNode) {
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
    }

    private fun handlePadToDirective(
        bodyStartCommands: Int,
        location: SourceLocation,
        proc: ProcNode
    ) {
        val padToNode = pendingPadToNode
        if (padToNode != null) {
            val padTo = calculateConst(padToNode.args?.first()!!)
            val bodyEndCommands = totalCommands
            val bodyBytes = (bodyEndCommands - bodyStartCommands) * 2
            if (bodyBytes < padTo) {
                val nopsNeeded = (padTo - bodyBytes) / 2
                repeat(nopsNeeded) {
                    compileAsm("nop", location)
                }
            } else {
                val paddingWords = (bodyBytes - padTo) / 2
                val padMsg =
                    "\$pad_to(${paddingWords}) violated for '${proc.name}': body is ${bodyBytes} bytes, target is $padTo"
                warning(padMsg, pendingPadToNode!!)
            }
            pendingPadToNode = null
        }
    }

    private fun checkProcArgs(args: List<ProcArgumentNode>) {
        val usedArgNames = mutableSetOf<String>()
        val usedArgValues = mutableSetOf<String>()
        args.forEach {
            val name = it.name
            val value = resolveArg(it.expr)
            if (usedArgNames.contains(name)) {
                errorArgumentAlreadyExists(name, it.expr.getLocation())
            }
            usedArgNames.add(name)
            val valueStr = if (value is RegisterNode) {
                value.reg
            } else {
                value.toString()
            }
            if (usedArgValues.contains(valueStr)) {
                warning("Proc argument $valueStr already used", it.expr)
            }
            usedArgValues.add(valueStr)
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

            is LoopNode -> compileLoop(node)
            is BinaryOperNode -> compileBinaryOp(node)
            is SuffixOperNode -> compileSuffixOp(node)
            is IfNode -> compileIf(node)
            is DoWhileNode -> compileDoWhile(node)
            is DataBlockNode -> compileDataBlock(node, true)
            is FunctionCallNode -> compileInlineOrNextCall(node)
            is UseNode -> processUse(node)
            is SaveregsNode -> processSaveregs(node)
            is BreakNode -> processBreak(node)
            is ConstValueNode -> {
                // Global consts are already processed in compile()
                // Local consts are processed in compileNode when inside a proc
            }
            is IdentifierNode -> errorUnexpectedExpression(node)
            else -> {
                errorUnexpectedExpression(node)
            }
        }
    }


    private fun compileSizeLimitedNode(node: Node, maxCommands: Int?, maxBytes: Int) {
        val beforeCount = getLastBlockCommandsCounter()
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


    override fun compileAssembler(node: AssemblerInstrNode, comment: String?) {
        val cmd = AvrCmd.getByName(node.instr) ?: errorWrongAssemblerInstruction(node)
        val location = node.getLocation()
        when {
            node.args == null -> addCommand(location, cmd, comment)
            node.args.size == 1 -> {
                if (cmd == AvrCmd.PUSH || cmd == AvrCmd.POP) {
                    compilePushPopAssembler(node.args, cmd, location, comment)
                } else {
                    val argNode = node.args[0]
                    // Handle rcall/call with result: rcall func() -> dest
                    if ((cmd == AvrCmd.RCALL || cmd == AvrCmd.CALL) && argNode is BinaryOperNode && argNode.operator == Operator.ARROW_RIGHT) {
                        compileCallWithResult(cmd, argNode, location, comment)
                    } else {
                        val arg = prepareAsmArg(argNode, cmd.arg1)
                        addCommand(location, cmd, comment, arg)
                    }
                }
            }

            node.args.size == 2 -> {
                val arg1 = prepareAsmArg(node.args[0], cmd.arg1)
                val arg2 = prepareAsmArg(node.args[1], cmd.arg2)
                addCommand(location, cmd, comment, arg1, arg2)
            }

            else -> errorTooManyArgumentsInAsmInstruction(node)
        }
    }

    private fun compileCallWithResult(cmd: AvrCmd, arrowNode: BinaryOperNode, location: SourceLocation, comment: String?) {
        val funcCall = arrowNode.left
        if (funcCall !is FunctionCallNode) {
            errorUnsupportedOperation(arrowNode)
        }
        
        val proc = findProc(funcCall)
        
        for (callArg in funcCall.args) {
            if (callArg !is CallArgumentNode) {
                errorWrongArgument(callArg)
            }
            if (callArg.name == null && proc.args.size == 1) {
                val procArg = proc.args[0]
                compileAssignOp(procArg.expr, callArg.expr, callArg.getLocation())
            } else {
                val argName = callArg.name ?: errorWrongArgument(callArg)
                val expr = proc.getArg(argName) ?: errorWrongArgument(callArg)
                compileAssignOp(expr, callArg.expr, callArg.getLocation())
            }
        }
        
        val argNode = IdentifierNode(funcCall.getLocation(), funcCall.name)
        val arg = prepareAsmArgIdentifier(cmd.arg1, argNode)
        addCommand(location, cmd, comment, arg)
        
        compileBinaryOp(arrowNode)
    }

    private fun AvrRatCompiler.compilePushPopAssembler(
        args: List<Node>,
        cmd: AvrCmd,
        location: SourceLocation,
        comment: String?
    ) {
        for (a in args) {
            val arg  = resolveArg(a)
            if (arg is RegisterGroupNode) {
                for (i in 0..< arg.size()) {
                    val index = if (cmd == AvrCmd.PUSH) i else arg.size() - 1 - i
                    val prepared = prepareAsmArg(arg.buildRegisterNode(index), cmd.arg1)
                    addCommand(location, cmd, comment, prepared)
                }
            } else {
                val prepared = prepareAsmArg(arg, cmd.arg1)
                addCommand(location, cmd, comment, prepared)
            }
        }
    }

    internal fun compileAssembler(loc: SourceLocation, cmd: AvrCmd, vararg args: AsmArg) = addCommand(loc, cmd, *args)


    private fun prepareAsmArg(node: Node, type: ArgType?): AsmArg {
        return when (val arg = resolveArg(node)) {
            is NumberNode -> {
                ArgNumberWithRadix(arg.value, arg.radix)
            }

            is RegisterNode -> {
                ArgReg.fromStr(arg.reg) ?: errorRegisterExpected(arg)
            }

            is IdentifierNode -> {
                prepareAsmArgIdentifier(type, arg)
            }

            is FunctionCallNode -> {
                val proc = findProc(arg)
                for (callArg in arg.args) {
                    if (callArg !is CallArgumentNode) {
                        errorWrongArgument(callArg)
                    }
                    if (callArg.name == null && proc.args.size == 1) {
                        val procArg = proc.args[0]
                        compileAssignOp(procArg.expr, callArg.expr, callArg.getLocation())
                    } else {
                        val argName = callArg.name ?: errorWrongArgument(callArg)
                        val expr = proc.getArg(argName) ?: errorWrongArgument(callArg)
                        compileAssignOp(expr, callArg.expr, callArg.getLocation())
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
                    when {
                        argLeft is RegisterGroupNode && argLeft.isPair() && argRight is NumberNode -> {
                            ArgOffsetPair(resolveAsmPair(argLeft), argRight.value)
                        }

                        argRight is RegisterGroupNode && argRight.isPair() && argLeft is NumberNode -> {
                            ArgOffsetPair(resolveAsmPair(argRight), argLeft.value)
                        }

                        argLeft is VariableNode && argRight is NumberNode -> {
                            ArgNumber(getVariableAddress(argLeft.variable) + argRight.value)
                        }

                        argLeft is ResolvedExternVar && argRight is NumberNode -> {
                            ArgExternWithOffset(argLeft.name, argRight.value)
                        }

                        else -> {
                            errorUnexpectedArgument(arg)
                        }
                    }
                } else if (arg.operator == Operator.MINUS) {
                    if (argLeft is VariableNode && argRight is NumberNode) {
                        ArgNumber(getVariableAddress(argLeft.variable) - argRight.value)
                    } else if (argLeft is ResolvedExternVar && argRight is NumberNode) {
                        ArgExternWithOffset(argLeft.name, -argRight.value)
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
                }
                ArgLabel(arg.name, arg.name, arg.getLocation(), resolver)
            }

            else -> {
                errorUnexpectedArgument(arg)
            }
        }
    }

    private fun prepareAsmArgIdentifier(type: ArgType?, arg: IdentifierNode): AsmArg =
        when (type) {
            ArgType.LABEL_REL -> {
                val resolver = { currentOffset: Int, labelOffset: Int ->
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

    private fun resolveAsmPair(node: RegisterGroupNode): ArgRegPair =
        when {
            node.isPair() -> when {
                node.isX() -> ArgRegPair.X
                node.isY() -> ArgRegPair.Y
                node.isZ() -> ArgRegPair.Z
                else -> errorWrongArgument(node)
            }

            node.isR25R24() -> ArgRegPair.R25_R24
            else -> errorWrongArgument(node)
        }


    private fun addProbeStartLabel(node: Node): String {
        val loc = node.getLocation()
        val name = "?probe_${loc.fileName.hashCode().absoluteValue}.${loc.line}_${inlineLabel}_start"
        addLabel(name, loc)
        return name
    }

    private fun addProbeEndLabel(node: Node, startLabel: String, maxBytes: Int) {
        val loc = node.getLocation()
        val name = "?probe_${loc.fileName.hashCode().absoluteValue}.${loc.line}_${inlineLabel}_end"
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
        val value = node.value
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
            when (node) {
                is LabelNode if node.name == name -> {
                    return name
                }

                is ProcNode if node.name == name -> {
                    return name
                }

                is ExternProcNode if node.proc.name == name -> {
                    return name
                }
            }
        }
        return null
    }

    private fun compileDataBlock(node: DataBlockNode, align: Boolean): Int {
        if (node.declaredSize != null) {
            val declaredSize = resolveArg(node.declaredSize)
            if (declaredSize !is NumberNode || declaredSize.value < 0) {
                errorWrongArraySize(node.declaredSize.getLocation())
            }
            if (declaredSize.value != node.items.size) {
                errorWrongArraySize(declaredSize.value, node)
            }
        }
        val dirOffset = directiveOffset
        if (dirOffset != null) {
            if (dirOffset % 2 != 0) {
                errorEvenValueExpected(directiveOffsetNode!!)
            }
            org(dirOffset)
            directiveOffset = null
            directiveOffsetNode = null
        }
        val size = when (node.type) {
            DataType.STRUCTURED -> compileStructuredDataBlock(node)
            DataType.BYTE, DataType.WORD, DataType.DWORD -> compilePrimitiveDataBlock(node)
            else -> errorWrongDataType(node)
        }
        if (align && (size % 2) != 0) {
            addByte(0, node.getLocation())
            return size + 1
        }
        return size
    }

    private fun compilePrimitiveDataBlock(node: DataBlockNode): Int {
        var bytes = 0
        for (itm in node) {
            val item = if (itm is BinaryOperNode) resolveArg(itm) else itm
            when (item) {
                is StringLiteralNode -> {
                    if (node.type != DataType.BYTE) {
                        errorNumberExpected(item)
                    }
                    for (i in 0 until item.str.length) {
                        addByte(resolveChar(item, i), item.getLocation())
                        bytes++
                    }
                }

                is NumberNode -> {
                    bytes += compilePrimitiveDataItem(node.type, item)
                }

                is CharLiteralNode -> {
                    if (node.type != DataType.BYTE) {
                        errorNumberExpected(item)
                    }
                    addByte(resolveChar(item), item.getLocation())
                    bytes++
                }

                is IdentifierNode -> {
                    bytes += compileIdentifierDataItem(node.type, item)
                }

                is BinaryOperNode -> {
                    bytes += compileBinaryIdentifierOpDataItem(node.type, item)
                }

                else -> errorUnexpectedArgument(item)
            }
        }
        return bytes
    }

    private fun compileStructuredDataBlock(node: DataBlockNode): Int {
        val struct = getStruct(node.structName) ?: internalError()
        var size = 0
        for (item in node.items) {
            size += compileStructuredDataBlockItem(item as MapNode, struct)
        }
        return size
    }

    private fun compileStructuredDataBlockItem(node: MapNode, struct: StructuredData): Int {
        var size = 0
        for (item in node.items) {
            if (struct.getItem(item.key) == null) {
                errorStructuredFieldNotFound(IdentifierToken(item.key, node.getLocation()), struct.name)
            }
        }
        for (item in struct.items) {
            when (item) {
                is PrimitiveItem -> {
                    val value = node.items[item.name] ?: errorUndefinedValueForField(item.name, node.getLocation())
                    size += when (value) {
                        is NumberNode -> compilePrimitiveDataItem(item.dataType, value)
                        is IdentifierNode -> compileIdentifierDataItem(item.dataType, value)
                        is BinaryOperNode -> compileBinaryIdentifierOpDataItem(item.dataType, value)
                        else -> errorUnexpectedArgument(value)
                    }
                }

                is ArrayItem -> {
                    val value = node.items[item.name] ?: errorUndefinedValueForField(item.name, node.getLocation())
                    if (value !is DataBlockNode) {
                        errorArrayOfPrimitivesExpected(value)
                    }
                    val expectedSize = item.arraySize
                    val foundSize = value.size()
                    if (foundSize != expectedSize) {
                        errorWrongArraySize(expectedSize, value)
                    }
                    if (item.source !is PrimitiveItem) {
                        errorArrayOfPrimitivesExpected(node)
                    }
                    size += compileDataBlock(value, false)
                }

                else -> {
                    errorUnexpectedArgument(node)   // TODO внятное описание ошибки
                }
            }
            //item.name
        }
        return size
    }


    private fun compilePrimitiveDataItem(type: DataType, value: NumberNode): Int {
        when (type) {
            DataType.BYTE -> addByte(value.value, value.getLocation())
            DataType.WORD -> addWord(value.value, value.getLocation())
            DataType.DWORD -> addDword(value.value, value.getLocation())
            else -> internalError()
        }
        return type.ramSize
    }

    private fun compileIdentifierDataItem(type: DataType, value: IdentifierNode): Int {
        val label = findLabel(value.name) ?: errorUnexpectedArgument(value)
        val arg = ProgramMemNode(value.getLocation(), label)
        return compileDeferredNumberDataItem(type, arg)
    }

    private fun compileBinaryIdentifierOpDataItem(type: DataType, value: BinaryOperNode): Int {
        if (value.left !is IdentifierNode) {
            errorUnexpectedArgument(value)
        }
        val label = findLabel(value.left.name) ?: errorUnexpectedArgument(value.left)
        val arg =
            BinaryOperNode(value.getLocation(), value.operator, ProgramMemNode(value.getLocation(), label), value.right)
        return compileDeferredNumberDataItem(type, arg)
    }

    private fun compileDeferredNumberDataItem(type: DataType, value: Node): Int {
        val arg = DeferredNumberArg(value, this::resolveDeferred)
        when (type) {
            DataType.BYTE -> addByte(arg, value.getLocation())
            DataType.WORD -> addWord(arg, value.getLocation())
            DataType.DWORD -> addDword(arg, value.getLocation())
            else -> internalError()
        }
        return type.ramSize
    }

    private fun compileExternProc(node: ExternProcNode) {
        if (gccAsmGen == null) {
            errorExternalAvailableOnlyForGcc(node)
        }
        val name = node.proc.name
        checkVarOrProcDefined(name, node)

        externalProcs[name] = node.proc
        gccAsmGen?.addExtern(name)
    }

    private fun compileExternVar(node: ExternVarNode) {
        if (gccAsmGen == null) {
            errorExternalAvailableOnlyForGcc(node)
        }
        for (vNode in node) {
            val variable = when (vNode) {
                is VarDeclarationNode -> {
                    val v = Var(vNode.name, vNode.dataType, null, -0xffff, vNode.structName)
                    v
                }

                is DataArrayDeclarationNode -> {
                    val size = calculateConst(vNode.size)
                    val v = Var(vNode.name, vNode.dataType, size, -0xffff, vNode.structName)
                    v
                }

                else -> internalError()
            }
            val name = variable.name
            checkProcDefined(name, node)
            if (vars.containsKey(name)) {   // не проверяем существование такой external-переменной чтобы можно было переопределять тип
                errorVariableAlreadyExists(name, node)
            }
            externalVars[name] = variable
            gccAsmGen?.addExtern(name)
        }
    }

    private fun compileInlineOrNextCall(node: FunctionCallNode) {
        val proc = findProc(node)
        if (proc.inline) {
            compileInlineCall(node, proc)
        } else {
            val astNodes = ast?.nodes
            val currentProcIndex = astNodes?.indexOf(currentProc!!.node)
            val calledProcIndex = astNodes?.indexOf(proc)
            if (currentProcIndex == null || calledProcIndex == null || currentProcIndex < 0 || calledProcIndex < 0 || currentProcIndex + 1 != calledProcIndex) {
                errorNotInlineProcCall(node)
            }
            if (node.args.isNotEmpty()) {
                compileCallArgsAssignments(node, proc)
            }
        }
    }

    private fun compileInlineCall(node: FunctionCallNode, proc: ProcNode) {
        for (prev in inlineCallStack) {
            if (prev.name == node.name) {
                errorRecursiveInlineCall(node)
            }
        }

        compileCallArgsAssignments(node, proc)
        inlineCallStack.add(proc)
        val labelSuffix = "_inl_${currentProc?.name ?: ""}_${node.getLocation().line}"
        inlineLabel += labelSuffix
        inlineLabelCounter++
        compileNode(proc.body)
        inlineLabel = inlineLabel.substring(0, inlineLabel.length - labelSuffix.length)
        inlineCallStack.removeAt(inlineCallStack.size - 1)
    }

    private fun AvrRatCompiler.compileCallArgsAssignments(
        node: FunctionCallNode,
        proc: ProcNode
    ) {
        for (a in node.args) {
            if (a !is CallArgumentNode) {
                errorWrongArgument(a)
            }
        }
        if (node.args.size == 1 && (node.args[0] as CallArgumentNode).name == null) {
            val a = node.args[0] as CallArgumentNode
            val procArg = resolveArg(proc.args[0].expr)
            compileAssignOp(procArg, a.expr, a.getLocation())
        } else {
            for (callArgNode in node.args) {
                val callArg = callArgNode as CallArgumentNode
                val argName = callArg.name ?: errorWrongArgument(callArg)
                val dest = proc.getArg(argName) ?: errorWrongArgument(callArg)
                compileAssignOp(dest, callArg.expr, callArg.getLocation())
            }
        }
    }


    fun isSingleInstructionNode(node: Node): Boolean =
        when {
            node is AssemblerInstrNode -> true
            node is SuffixOperNode && node.operator.isDecOrInc() -> {
                isSingleInstructionIncDec(node.expression)
            }

            node is PrefixOperNode && node.operator.isDecOrInc() -> {
                isSingleInstructionIncDec(node.expression)
            }

            node is CompoundStmt && node.size() == 1 -> isSingleInstructionNode(node[0])
            node is BinaryOperNode -> {
                val left = resolveArg(node.left)
                val right = resolveArg(node.right)
                when (left) {
                    is RegisterNode -> right is RegisterNode || right is NumberNode || right is MemArrayNode || right is PrgArrayNode
                    is RegisterBitNode -> right is NumberNode
                    is IoPortNode -> right is RegisterNode && node.operator == Operator.ASSIGN
                    is IoPortBitNode -> right is NumberNode && node.operator == Operator.ASSIGN
                    is RegisterGroupNode -> {
                        node.operator == Operator.ASSIGN && right is RegisterGroupNode && left.isWordPair() && right.isWordPair()
                    }

                    else -> false
                }
            }

            else -> false
        }

    private fun isSingleInstructionIncDec(node: Node): Boolean =
        when (val expr = resolveArg(node)) {
            is RegisterNode -> true
            is RegisterGroupNode -> expr.isHighPair()
            else -> false
        }

    fun getRamSize(): Int = variableOffset

    override fun getFreeFlashSize(): Int = getDevice().flashSize - getOutputSize()

    private fun checkProcedureRet(proc: ProcNode) {
        if (proc.body.isEmpty()) {
            warning("Empty procedure", proc)
        } else {
            var lastNode = try {
                proc.body.last { it !is DataBlockNode }
            } catch (_: NoSuchElementException) {
                proc.body.last()
            }
            if (lastNode is LabelNode && proc.body.last() is DataBlockNode) {
                try {
                    lastNode = proc.body.last { it !is LabelNode && it !is DataBlockNode}
                } catch (_: NoSuchElementException) {}
            }
            if (!checkFunctionLastNodeIsReturnable(lastNode)) {
                warning("function does not end with a return or an unconditional jump", lastNode)
            }
        }
    }

    private fun checkFunctionLastNodeIsReturnable(lastNode: Node): Boolean {
        when (lastNode) {
            is AssemblerInstrNode -> {
                val instr = lastNode.instr
                if (instr == "ret" || instr == "reti" || instr == "rjmp" || instr == "jmp" || instr == "ijmp" || instr == "eijmp") {
                    return true
                }
            }

            is LoopNode -> {
                return lastNode.cond == null
            }

            is CompoundStmt -> {
                return !lastNode.isEmpty() && checkFunctionLastNodeIsReturnable(lastNode.last())
            }

            is IfNode -> {
                return checkFunctionLastNodeIsReturnable(lastNode.thenBlock) &&
                        (lastNode.elseBlock == null || checkFunctionLastNodeIsReturnable(lastNode.elseBlock))
            }

            is FunctionCallNode -> return true
        }
        return false
    }
}


private fun AsmCmd.isHighRegCmd() =
    this == AvrCmd.LDI || this == AvrCmd.SUBI || this == AvrCmd.ANDI || this == AvrCmd.ORI || this == AvrCmd.SBCI || this == AvrCmd.CPI


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

private fun errorInvalidPin(pin: String, node: Node): Nothing {
    throw CompilerException("Invalid device pin: $pin", node.getLocation())
}

private fun errorInvalidBitNumber(bit: Int, loc: SourceLocation): Nothing {
    throw CompilerException("Invalid bit number: $bit", loc)
}

private fun errorPinAlreadyDefined(name: String, node: Node): Nothing {
    throw CompilerException("Pin '$name' already defined", node.getLocation())
}

private fun errorBusAlreadyDefined(name: String, node: Node): Nothing {
    throw CompilerException("Bus '$name' already defined", node.getLocation())
}

private fun errorInvalidPortName(node: IdentifierNode): Nothing {
    throw CompilerException("Invalid or unknown port name: ${node.name}", node.getLocation())
}

private fun errorUndefinedPin(node: IdentifierNode): Nothing {
    throw CompilerException("Undefined pin: $node", node.getLocation())
}

private fun errorWrongPinAccess(node: IdentifierNode): Nothing {
    throw CompilerException("Wrong pin syntax: 'pin', 'port' or 'ddr' expected", node.getLocation())
}

internal fun errorWrongBitValue(node: Node): Nothing {
    throw CompilerException("1 or 0 expected, but $node found", node.getLocation())
}

internal fun errorRegisterExpected(node: Node): Nothing {
    throw CompilerException("Register expected: $node", node.getLocation())
}

internal fun errorRegisterOrPairExpected(node: Node): Nothing {
    throw CompilerException("Register or pair expected: $node", node.getLocation())
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

private fun errorReuseNoAliasForRegister(node: UseNode): Nothing {
    val regName = when (val reg = node.reg) {
        is RegisterNode -> reg.reg
        is RegisterGroupNode -> reg.buildRegisterNode(0).reg
        else -> reg.toString()
    }
    throw CompilerException("No alias found for register $regName", node.getLocation())
}

private fun errorReuseOnlyInProc(node: UseNode): Nothing {
    throw CompilerException("reuse is only allowed inside procedures", node.getLocation())
}

internal fun errorUnsupportedOperation(location: SourceLocation): Nothing {
    throw CompilerException("Unsupported operation", location)
}

internal fun errorUnsupportedOperation(node: Node): Nothing {
    errorUnsupportedOperation(node.getLocation())
}

internal fun errorUnknownLabel(node: ArgLabel): Nothing {
    throw CompilerException("Unknown label: ${node.name}", node.location)
}

internal fun errorVariableNotFound(node: Node): Nothing {
    throw CompilerException("Variable not found: $node", node.getLocation())
}

private fun errorEvenValueExpected(node: DirectiveNode): Nothing {
    throw CompilerException("Even value expected", node.getLocation())
}

private fun errorLabelNotFound(node: ProgramMemNode): Nothing {
    throw CompilerException("Label not found: ${node.label}", node.getLocation())
}

private fun errorNotInlineProcCall(node: FunctionCallNode): Nothing {
    throw CompilerException("Not inline function call", node.getLocation())
}

private fun errorRecursiveInlineCall(node: FunctionCallNode): Nothing {
    throw CompilerException("Recursive inline call", node.getLocation())
}

private fun errorSingleInstructionExpected(node: Node, commandsInBlock: Int): Nothing {
    throw CompilerException("Single instructions expected (but $commandsInBlock found)", node.getLocation())
}

private fun errorTooManyCommandsInBlock(node: Node, maxCommands: Int, commandsInBlock: Int): Nothing {
    throw CompilerException(
        "Block too large: expected $maxCommands instructions but found $commandsInBlock",
        node.getLocation()
    )
}

private fun errorExternalAvailableOnlyForGcc(node: Node): Nothing {
    throw CompilerException("External objects can be used only in GCC-mode", node.getLocation())
}

internal fun errorValueTooBig(node: Node): Nothing {
    throw CompilerException("Value too big: $node", node.getLocation())
}

internal fun errorLowIoExpected(node: Node): Nothing {
    throw CompilerException("Low (0..31) address expected", node.getLocation())
}

internal fun errorUnresolvedData(node: Node): Nothing {
    throw CompilerException("Can't resolve data", node.getLocation())
}

internal fun errorSregCannotBeFirstHere(node: Node): Nothing {
    throw CompilerException("SREG cannot be the first argument here", node.getLocation())
}

internal fun errorWrongFieldName(fieldName: String, structName: String, loc: SourceLocation): Nothing {
    throw CompilerException("$structName has no member named '$fieldName'", loc)
}

internal fun errorUndefinedValueForField(fieldName: String, loc: SourceLocation): Nothing {
    throw CompilerException("Undefined value for field '$fieldName'", loc)
}

internal fun errorWrongInterruptNodeSize(size: Int, expected: Int, loc: SourceLocation): Nothing {
    throw CompilerException("Invalid interrupt handler size. Expected ${wordStr(expected)} but found ${wordStr(size)}", loc)
}

internal fun errorNoRam(used: Int, size: Int, loc: SourceLocation): Nothing {
    throw CompilerException("No RAM (used $used from $size bytes)", loc)
}

fun errorCantReadHighPortBitDirectly(port: IoPortNode): Nothing {
    throw CompilerException(
        "Can't read from high port (${port.name} = 0x${port.address.toString(16)}) directly. You can do this using a temporary register.",
        port.getLocation()
    )
}

fun errorHighRegisterExpected(reg: RegisterNode): Nothing {
    throw CompilerException(
        "r16..r31 expected but ${reg.reg} found. Perhaps you can do this using a temporary register.",
        reg.getLocation()
    )
}

fun errorArgumentAlreadyExists(name: String, location: SourceLocation): Nothing {
    throw CompilerException(
        "Argument already exists: $name",
        location
    )
}

internal fun errorSizeMismatch(dest: Node, src: Node): Nothing {
    val destName = getNodeName(dest)
    val srcName = getNodeName(src)
    val destSize = getNodeBitSize(dest)
    val srcSize = getNodeBitSize(src)
    if (destName != null && srcName != null && destSize != null && srcSize != null) {
        throw CompilerException(
            "Size mismatch: $srcName ($srcSize-bit) cannot be directly assigned to $destName ($destSize-bit)",
            dest.getLocation()
        )
    } else if (destName != null && srcName != null) {
        throw CompilerException("Size mismatch: $destName cannot be directly assigned to $srcName", dest.getLocation())
    } else {
        throw CompilerException("Size mismatch", dest.getLocation())
    }
}

private fun getNodeName(node: Node): String? =
    when (node) {
        is RegisterNode -> node.reg
        is IoPortNode -> node.name
        is RegisterGroupNode -> node.toString()
        is VariableNode -> node.variable.name
        is ResolvedExternVar -> node.name
        else -> null
    }

private fun getNodeBitSize(node: Node): Int? =
    when (node) {
        is RegisterNode -> 8
        is IoPortNode -> node.size * 8
        is RegisterGroupNode -> node.size() * 8
        is VariableNode -> node.variable.ramSize() * 8
        is ResolvedExternVar -> if (node.size != null)  node.size * 8 else null
        else -> null
    }


private fun wordStr(words: Int) =
    if (words == 1) "$words word" else "$words words"