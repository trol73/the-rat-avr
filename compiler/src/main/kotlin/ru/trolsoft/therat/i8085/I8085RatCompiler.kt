package ru.trolsoft.therat.i8085

import ru.trolsoft.compiler.compiler.*
import ru.trolsoft.compiler.general.DataType
import ru.trolsoft.compiler.general.StructuredData
import ru.trolsoft.compiler.general.Var
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.arch.*
import ru.trolsoft.therat.arch.i8085.*
import ru.trolsoft.therat.i8085.compile.*
import ru.trolsoft.therat.i8085.compile.compileAssignOp
import ru.trolsoft.therat.i8085.compile.compileBinaryOp
import ru.trolsoft.therat.i8085.compile.compileSuffixOp


class I8085RatCompiler(
    val is8085: Boolean = false,
    encodingsPath: String,
    console: ConsoleReporter,
    fileLoader: FileLoader? = null
) : Compiler(
    I8085Scanner(), encodingsPath, console, fileLoader
) {
    private val structures = mutableMapOf<String, StructuredData>()
    private val externalAddresses = mutableMapOf<String, Int>()
    private val constValues = mutableMapOf<String, Node>()
    private var variableOffset: Int = 0
    private var outputStartOffset: Int? = null
    private var varBaseOffset: Int? = null

    private var ast: I8085AstNode? = null

    override fun parse(stream: TokenStream): AstNode = I8085RatParser(stream).parse()


    override fun compile(ast: AstNode) {
        this.ast = ast as I8085AstNode
        this.structures.putAll(ast.structures)

        preloadProcedures(ast)
        preloadLabels(ast)
        for (node in ast) {
            when (node) {
                is ProcNode -> compileProc(node)
                is VarDeclarationNode -> processVar(node)
                is DataArrayDeclarationNode -> processVar(node)
                //is UseNode -> processUse(node)
                is DirectiveNode -> processDirective(node)
                is LabelNode -> addProcLabel(node.name, node.getLocation())
                is DataBlockNode -> compileDataBlock(node, true)
//                is ExternVarNode -> compileExternVar(node)
                is ExternProcNode -> compileExternProc(node)
                is ConstValueNode -> processConst(node)

                else -> TODO("unexpected node: $node")
            }
        }
    }


    override fun resolveArg(arg: Node): Node {
        when (arg) {
            is IdentifierNode -> return resolveIdentifierArg(arg)
            is RegisterNode -> {
                if (is8085Pair(arg.reg)) {
                    return RegisterPairNode(arg.getLocation(), arg.reg)
                }
            }
            is CharLiteralNode -> return NumberNode(arg.getLocation(), resolveChar(arg))
            is IndexedNode -> return resolveIndexedArg(arg)
            is BinaryOperNode -> return resolveBinaryOpArg(arg)
            is PrefixOperNode -> return resolvePrefixOpArg(arg)
            is FunctionCallNode -> {
                if (arg.isBuiltin()) {
                    return arg.compileBuiltin(this)
                }
            }
        }
        return arg
    }



    private fun resolveIdentifierArg(arg: IdentifierNode): Node {
        val externalProc = externalAddresses[arg.name]
        if (externalProc != null) {
            return NumberNode(arg.getLocation(), externalProc, 16)
        }
        constValues[arg.name]?.let { constExpr ->
            val resolved = resolveArg(constExpr)
            return resolveArg(resolved)
        }
        // Local consts shadow global consts
        val proc = currentProc
        proc?.getConst(arg.name)?.let { localConst ->
            val resolved = resolveArg(localConst)
            return resolveArg(resolved)
        }
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

//            uses.containsKey(arg.name) -> {
//                return uses[arg.name]?.reg ?: internalError()
//            }

//            getVar(arg.name) != null -> {
//                val variable = getVar(arg.name)!!
//                return VariableNode(arg.getLocation(), variable, getVariableAddress(variable))
//            }
            getVar(arg.name) != null -> {
                val variable = getVar(arg.name)!!
                return VariableNode(arg.getLocation(), variable, variable.offset)
            }
//            getExternVar(arg.name) != null -> {
//                val v = getExternVar(arg.name)!!
//                val struct = structures[v.structName]
//                return ResolvedExternVar(arg.getLocation(), arg.name, v.arraySize, v.dataType, struct)
//            }
        }
        return arg
    }

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
        return when (arg.name.name) {
            "mem" -> MemArrayNode(arg.getLocation(), resolveArg(arg.index))
            "port" -> PortArrayNode(arg.getLocation(), resolveArg(arg.index))
            else -> arg
        }
    }

    private fun resolveBinaryOpArg(arg: BinaryOperNode): Node {
        if (arg.operator == Operator.ARROW_RIGHT) {
            TODO()
//            return resolveRightArrowed(arg)
        } else {
            val left = resolveArg(arg.left)
            val right = resolveArg(arg.right)
            if (left is NumberNode && right is NumberNode) {
                return NumberNode(arg.getLocation(), super.calculateConst(arg), left.radix)
            }
        }
        return arg
    }

    private fun resolvePrefixOpArg(arg: PrefixOperNode): Node {
        val expr = resolveArg(arg.expression)
        if (arg.operator == Operator.BINARY_NOT && expr is NumberNode) {
            return NumberNode(arg.getLocation(), 0xFF - expr.value, 2)
        }
        return arg
    }


    /*
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

        }
        return arg
    }
     */
    private fun compileProc(proc: ProcNode) {
        val dirOffset = directiveOffset
        if (dirOffset != null) {
            if (proc.inline) {
                errorUnexpectedDirective(directiveOffsetNode!!)
            }

            org(dirOffset)
            directiveOffset = null
            directiveOffsetNode = null
        }
        if (!proc.inline) {
            addLabel(proc.name, proc.getLocation())

            currentProc = Procedure(proc.name, proc.args, proc.results, proc)
            // Process local const declarations in the procedure body
            for (bodyNode in proc.body) {
                if (bodyNode is ConstValueNode) {
                    currentProc!!.addConst(bodyNode.name, bodyNode.expr)
                }
            }
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

            is LoopNode -> compileLoop(node)
            is BinaryOperNode -> compileBinaryOp(node)
            is SuffixOperNode -> compileSuffixOp(node)
            is IfNode -> compileIf(node)
//            is DoWhileNode -> compileDoWhile(this, node)
            is DataBlockNode -> compileDataBlock(node, true)
//            is FunctionCallNode -> compileInlineCall(node)
//            is UseNode -> processUse(node)
            is SaveregsNode -> processSaveregs(node)
//            is BreakNode -> processBreak(node)
            is ConstValueNode -> { /* already processed */ }
            is IdentifierNode -> errorUnexpectedExpression(node)
            else -> {
                errorUnexpectedExpression(node)
                //TODO("${node.javaClass} ${node.getLocation()} $node\n")
            }
        }
    }

    override fun compileAssembler(node: AssemblerInstrNode, comment: String?) {
        val cmd = I8085Cmd.getByName(node.instr) ?: errorWrongAssemblerInstruction(node)
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



    private fun prepareAsmArg(node: Node, type: ArgType?): AsmArg =
        when (val arg = resolveArg(node)) {
            is NumberNode -> {
                ArgNumberWithRadix(arg.value, arg.radix)
            }

            is RegisterNode -> {
                ArgReg.fromStr(arg.reg) ?: errorRegisterExpected(arg)
            }

            is RegisterPairNode -> {
                ArgRegPair.fromStr(arg.pair) ?: errorPairExpected(arg)
            }

            is IdentifierNode -> {
                if (type == ArgType.LABEL_ABS || type == ArgType.CONST) {
                    val resolver = { _: Int, labelOffset: Int -> labelOffset }
                    if (procs.containsKey(arg.name) || labels.contains(arg.name)) {
                        ArgLabel(arg.name, arg.name, arg.getLocation(), resolver)
                    } else {
                        buildArgLabel(arg.name, arg.getLocation(), false, resolver)
                    }
                } else {
                    errorUnexpectedExpression(node)
                }
            }

            is FunctionCallNode -> {
                val proc = findProc(arg)
                val assignments = mutableListOf<Pair<Node, Node>>()
                for (callArg in arg.args) {
                    if (callArg !is CallArgumentNode) {
                        errorWrongArgument(callArg)
                    }
                    if (proc.args.size < arg.args.size) {
                        errorProcedureArgumentsCount(proc, arg)
                    }
                    if (callArg.name == null && proc.args.size == 1) {
                        val procArg = proc.args[0]
                        assignments.add(Pair(procArg.expr, callArg.expr))
                        //compileAssignOp(procArg.expr, callArg.expr, callArg.getLocation())
                    } else {
                        val argName = callArg.name ?: errorWrongArgument(callArg)
                        val expr = proc.getArg(argName) ?: errorWrongArgument(callArg)
                        assignments.add(Pair(expr, callArg.expr))
                        //compileAssignOp(expr, callArg.expr, callArg.getLocation())
                    }
                }
                for (a in optimizeAssignments(assignments)) {
                    if (a != null) {
                        compileAssignOp(a.first, a.second, a.second.getLocation())
                    }
                }
                prepareAsmArg(IdentifierNode(arg.getLocation(), arg.name), type)
            }

            is MemArrayNode -> {
                if (arg.index.isHL()) {
                    ArgReg.M
                } else {
                    errorUnexpectedExpression(node)
                }
            }

            else -> errorUnexpectedExpression(node)
        }

    private fun optimizeAssignments(assignments: List<Pair<Node, Node>>): List<Pair<Node, Node>?> {
        if (assignments.size < 2) {
            return assignments
        }
        val res = mutableListOf<Pair<Node, Node>?>()
        val indexesMap = mutableMapOf<String, Int>()
        val valuesMap = mutableMapOf<String, Int>()
        for ((i, pair) in assignments.withIndex()) {
            res.add(pair)
            val reg = pair.first
            val value = resolveArg(pair.second)
            if (reg !is RegisterNode || value !is NumberNode) {
                continue
            }
            val regName = reg.reg.uppercase()
            indexesMap[regName] = i
            valuesMap[regName] = value.value
        }
        val bi = indexesMap["B"]
        val ci = indexesMap["C"]
        if (bi != null && ci != null) {
            val high = (res[bi]!!.second as NumberNode).value
            val lo = (res[ci]!!.second as NumberNode).value
            val value = NumberNode(res[bi]!!.second.getLocation(), (high shl 8) + lo, 16)
            res[bi] = Pair(RegisterPairNode(res[bi]!!.first.getLocation(), "BC"), value)
            res[ci] = null
        }
        val di = indexesMap["D"]
        val ei = indexesMap["E"]
        if (di != null && ei != null) {
            val high = (res[di]!!.second as NumberNode).value
            val lo = (res[ei]!!.second as NumberNode).value
            val value = NumberNode(res[di]!!.second.getLocation(), (high shl 8) + lo, 16)
            res[di] = Pair(RegisterPairNode(res[di]!!.first.getLocation(), "DE"), value)
            res[ei] = null
        }
        val hi = indexesMap["H"]
        val li = indexesMap["L"]
        if (hi != null && li != null) {
            val high = (res[hi]!!.second as NumberNode).value
            val lo = (res[li]!!.second as NumberNode).value
            val value = NumberNode(res[hi]!!.second.getLocation(), (high shl 8) + lo, 16)
            res[hi] = Pair(RegisterPairNode(res[li]!!.first.getLocation(), "DE"), value)
            res[li] = null
        }
        return res
    }

    private fun compileExternProc(node: ExternProcNode) {
        val name = node.proc.name
        checkVarOrProcDefined(name, node)
        externalProcs[name] = node.proc
        externalAddresses[name] = node.address!!
    }

    internal fun compileAsmPair(
        cmd: String,
        pair: RegisterPairNode,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(pair))
        compileAssembler(cmdNode, comment)
    }
    internal fun compileAsmPairNum(
        cmd: String,
        pair: RegisterPairNode,
        num: NumberNode,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(pair, num))
        compileAssembler(cmdNode, comment)
    }


    internal fun compileAsmPairLabel(
        cmd: String,
        pair: RegisterPairNode,
        dest: IdentifierNode,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(pair, dest))
        compileAssembler(cmdNode, comment)
    }

    internal fun compileAsmLabel(
        cmd: String,
        lbl: String,
        location: SourceLocation,
        comment: String? = null
    ) {
        val cmdNode = AssemblerInstrNode(location, cmd, listOf(IdentifierNode(location, lbl)))
        compileAssembler(cmdNode, comment)
    }

    private fun processSaveregs(node: SaveregsNode) {
        val regs = mutableListOf<Node>()
        for ((i, arg) in node.args.withIndex()) {
            val reg = resolveArg(arg)
            if (reg is RegisterPairNode) {
                compileAsmPair("push", reg, arg.getLocation())
                regs.add(reg)
            } else {
                errorUnexpectedArgument(arg)
            }
        }
        compileNode(node.body)
        for ((i, arg) in regs.reversed().withIndex()) {
            when (arg) {
                is RegisterPairNode -> {
                    compileAsmPair("pop", arg, arg.getLocation())
                }
                else -> {
                    internalError()
                }
            }
        }
    }

    private fun compileDataBlock(node: DataBlockNode, align: Boolean): Int {
        val dirOffset = directiveOffset
        if (dirOffset != null) {
            org(dirOffset)
            directiveOffset = null
            directiveOffsetNode = null
        }
        val size = when (node.type) {
//            DataType.STRUCTURED -> compileStructuredDataBlock(node)
            DataType.BYTE, DataType.WORD, DataType.DWORD -> compilePrimitiveDataBlock(node)
            else -> errorWrongDataType(node)
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

//                is IdentifierNode -> {
//                    bytes += compileIdentifierDataItem(node.type, item)
//                }
//
//                is BinaryOperNode -> {
//                    bytes += compileBinaryIdentifierOpDataItem(node.type, item)
//                }

                else -> errorUnexpectedArgument(item)
            }
        }
        return bytes
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

    private fun processVar(node: Node) {
        val variable = when (node) {
            is VarDeclarationNode -> {
                if (node.absolute != null) {
                    varBaseOffset = node.absolute
                    variableOffset = 0
                }
                val base = varBaseOffset ?: errorUndefinedAddress(node)
                val offset = node.absolute ?: (base + variableOffset)
                val v = Var(node.name, node.dataType, null, offset, node.structName)
                variableOffset += v.ramSize()
                v
            }
            is DataArrayDeclarationNode -> {
                if (node.absolute != null) {
                    varBaseOffset = node.absolute
                    variableOffset = 0
                }
                val base = varBaseOffset ?: errorUndefinedAddress(node)
                val size = calculateConst(node.size)
                val offset = node.absolute ?: (base + variableOffset)
                val v = Var(node.name, node.dataType, size, offset, node.structName)
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

    private fun processConst(node: ConstValueNode) {
        if (constValues.containsKey(node.name)) {
            errorConstantAlreadyDefined(node.name, node)
        }
        constValues[node.name] = node.expr
    }

    private fun processDirective(node: DirectiveNode) {
        when (node.name) {
            "org" -> processOrgDirective(node)
            "encoding" -> processEncodingDirective(node)
            "output" -> processOutputDirective(node)
            else -> errorInvalidDirective(node)
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

        if (outputStartOffset == null) {
            setBinaryStartOffset(directiveOffset!!)
            outputStartOffset = directiveOffset
        } else {
            TODO()
        }
    }

    fun getStartOffset(): Int = outputStartOffset ?: 0
}

internal fun errorPairExpected(node: Node): Nothing {
    throw CompilerException("Register pair expected", node.getLocation())
}

internal fun errorRegisterExpectedButPairFound(node: RegisterPairNode): Nothing {
    throw CompilerException("Register expected but pair found: ${node.pair}", node.getLocation())
}

internal fun errorPairExpectedButRegisterFound(node: RegisterNode): Nothing {
    throw CompilerException("Register pair expected but register found: ${node.reg}", node.getLocation())
}

internal fun errorPortsCanOnlyReadWriteToA(node: Node): Nothing {
    throw CompilerException("Ports can only be read/write to the A register.", node.getLocation())
}

internal fun errorWrongPortOffset(node: NumberNode): Nothing {
    throw CompilerException("Wrong port number (0..0xFF expected).", node.getLocation())
}

internal fun errorUndefinedAddress(node: Node): Nothing {
    throw CompilerException("Undefined variable address, 'absolute' expected.", node.getLocation())
}
