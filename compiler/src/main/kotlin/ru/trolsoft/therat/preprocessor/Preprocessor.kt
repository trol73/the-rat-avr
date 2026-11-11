package ru.trolsoft.therat.preprocessor

import kotlinx.io.IOException
import ru.trolsoft.compiler.utils.fileExists
import ru.trolsoft.compiler.utils.getCurrentDate
import ru.trolsoft.compiler.utils.getCurrentTime
import ru.trolsoft.therat.lang.*
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.utils.Stack
import kotlinx.io.files.Path
import ru.trolsoft.compiler.utils.FILE_SEPARATOR
import ru.trolsoft.compiler.utils.ConsoleReporter


open class Preprocessor(
    private val fileLoader: FileLoader,
    private val console: ConsoleReporter
) {

    data class Macro(
        val name: String,
        val location: SourceLocation,
        val body: List<Token>?,
        val args: List<String>?)

    private data class State(
        var active: Boolean,
        val parentActive: Boolean,
        var foundElse: Boolean = false,
        var foundActive: Boolean = active)

    private val defines = HashMap<String, Macro>()
    private val conditionStack = Stack<State>()
    private val includePaths = mutableListOf<String>()
    private var counter: Int = 0

    fun addIncludePath(path: String) = includePaths.add(path)
    fun addIncludePaths(paths: List<String>) = includePaths.addAll(paths)

    fun process(filePath: String): TokenStream {
        conditionStack.clear()
        conditionStack.push(State(active = true, parentActive = true))
        return processFile(filePath)
    }


    private fun processFile(filePath: String): TokenStream {
        val src = fileLoader.loadSource(filePath)

        while (!src.eof()) {
            val t = src.nextToken()
            processToken(t, src, filePath)
        }
        src.reset()
        return src
    }


    private fun processToken(t: Token, src: TokenStream, filePath: String) {
        val active = checkIfCondition()
        when {
            t is PreprocessorToken -> processPreprocessorDirective(active, t, src)
            !active -> src.removeLast()
            t is IdentifierToken && !processBuiltinDefines(src, t, filePath) -> {
                defines[t.name]?.let { def ->
                    val state = src.saveState()
                    replaceDefine(src, t, def, filePath)
                    src.restoreState(state)
                }
            }
        }
    }

    private fun processPreprocessorDirective(
        active: Boolean,
        t: PreprocessorToken,
        src: TokenStream
    ) {
        if (active) {
            when (t.name) {
                "include" -> processIncludeDirective(src, t)
                "define" -> processDefineDirective(src, t)
                "undef" -> processUndefDirective(src, t)
                "ifdef" -> processIfdefDirective(src, t, false)
                "ifndef" -> processIfdefDirective(src, t, true)
                "if" -> processIfDirective(src, t)
                "else" -> processElseDirective(src, t)
                "elif" -> processElifDirective(src, t)
                "endif" -> processEndifDirective(src, t)
                "error" -> processErrorDirective(src, t)
                "warning" -> processWarningDirective(src, t)
            }
        } else {
            when (t.name) {
                "ifdef" -> processIfdefDirective(src, t, false)
                "if" -> processIfDirective(src, t)
                "ifndef" -> processIfdefDirective(src, t, true)
                "else" -> processElseDirective(src, t)
                "elif" -> processElifDirective(src, t)
                "endif" -> processEndifDirective(src, t)
                else -> src.removeLast()
            }
        }
    }

    private fun processBuiltinDefines(src: TokenStream, t: IdentifierToken, filePath: String): Boolean =
        when (t.name) {
            "__COUNTER__" -> {
                src.replaceLast(NumberToken(counter++, t.location))
                true
            }
            "__FILE__" -> {
                src.replaceLast(StringLiteralToken(filePath, t.location))
                true
            }
            "__LINE__" -> {
                src.replaceLast(NumberToken(t.location.line, t.location))
                true
            }
            "__DATE__" -> {
                src.replaceLast(StringLiteralToken(getCurrentDate(), t.location))
                true
            }
            "__TIME__" -> {
                src.replaceLast(StringLiteralToken(getCurrentTime(), t.location))
                true
            }
            else -> false
        }

    private fun TokenStream.replaceLast(to: Token) {
        val state = saveState()
        markLast()
        replaceMarked(mutableListOf(to))
        restoreState(state)
    }

    private fun processBuiltinInBody(body: MutableList<Token>, line: Int) {
        for (i in body.indices) {
            val token = body[i]
            if (token is IdentifierToken && token.name == "__LINE__") {
                body[i] = NumberToken(line, token.location)
            }
        }
    }

    private fun forceNextMarkedToken(src: TokenStream, prevToken: Token): Token {
        if (src.eof()) {
            errorUnexpectedEof(prevToken)
        }
        return src.nextMarkedToken()
    }

    private fun processIncludeDirective(src: TokenStream, directiveToken: Token) {
        src.markLast()
        when (val t = forceNextMarkedToken(src, directiveToken)) {
            is StringLiteralToken -> {
                includeFile(src, t.str, t.location)
            }
            is OperatorToken -> {
                if (t.name != "<") {
                    errorWrongIncludeSyntax(t)
                }
                src.unmarkAll() // игнорируем директиву, чтобы передавать далее препроцессору gcc
            } else -> {
                errorWrongIncludeSyntax(t)
            }
        }
    }

    private fun processErrorDirective(src: TokenStream, directiveToken: Token) {
        src.removeLast()
        val message = readDirectiveMessage(src)
        throw PreprocessorException("#error $message", directiveToken.location)
    }

    private fun processWarningDirective(src: TokenStream, directiveToken: Token) {
        src.removeLast()
        val message = readDirectiveMessage(src)
        console.warning("#warning $message", directiveToken.location)
        removeNewLine(src)
    }

    private fun readDirectiveMessage(src: TokenStream): String {
        val tokens = mutableListOf<Token>()
        while (!src.eof()) {
            val t = src.nextMarkedToken()
            if (t is NewLineToken) {
                break
            }
            tokens.add(t)
        }
        src.removeMarked()
        return tokens.joinToString(" ") { it.str() }
    }


    private fun processDefineDirective(src: TokenStream, directiveToken: Token) {
        src.removeLast()
        val name = forceNextMarkedToken(src, directiveToken)
        if (name !is IdentifierToken) {
            errorIdentifierExpected(name)
        }
        val token = if (src.eof()) null else src.nextMarkedToken()
        if (token != null && token.isBracket('(') && token.isNextAfter(name)) {
            val args = readMacroArgsDefinition(src, token)
            val body = readMacroBody(src, src.nextMarkedToken())
            defineMacro(name, body, args)
        } else {
            val body = readMacroBody(src, token)
            defineMacro(name, body)
        }
        src.removeMarked()
    }

    private fun processUndefDirective(src: TokenStream, directiveToken: Token) {
        src.removeLast()
        val name = forceNextMarkedToken(src, directiveToken)
        if (name !is IdentifierToken) {
            errorIdentifierExpected(name)
        }
        if (defines.remove(name.name) == null) {
            warningDefineNotFound(name)
        }
        src.removeMarked()
        removeNewLine(src)
    }

    private fun processIfdefDirective(src: TokenStream, directiveToken: Token, inverse: Boolean) {
        src.removeLast()
        val name = forceNextMarkedToken(src, directiveToken)
        if (name !is IdentifierToken) {
            errorIdentifierExpected(name)
        }
        src.removeMarked()
        val defined = defines.containsKey(name.name)
        val parentActive = checkIfCondition()
        val newCond = State(parentActive && (defined xor inverse), parentActive)
        conditionStack.push(newCond)
        removeNewLine(src)
    }

    private fun processIfDirective(src: TokenStream, directiveToken: Token, inverse: Boolean = false) {
        src.removeLast()
        val expr = readExpression(src, directiveToken)
        val parentActive = checkIfCondition()
        val res = parentActive && checkExpr(this, expr, directiveToken.location.fileName) != 0
        val newCond = State(parentActive && (res xor inverse), parentActive)
        conditionStack.push(newCond)
        removeNewLine(src)
    }

    private fun processElseDirective(src: TokenStream, directiveToken: Token) {
        src.removeLast()
        if (conditionStack.size() <= 1) {
            errorIfNotFound(directiveToken)
        }
        val state = conditionStack.peek()!!
        if (state.foundElse) {
            errorElseAfterElse(directiveToken)
        }
        state.active = state.parentActive && !state.foundActive
        state.foundElse = true
        state.foundActive = state.foundActive || state.active
        removeNewLine(src)
    }

    private fun processElifDirective(src: TokenStream, directiveToken: Token) {
        src.removeLast()
        if (conditionStack.size() <= 1) {
            errorIfNotFound(directiveToken)
        }
        val state = conditionStack.peek()!!
        if (state.foundElse) {
            errorElseAfterElse(directiveToken)
        }
        val expr = readExpression(src, directiveToken)
        val res = state.parentActive && checkExpr(this, expr, directiveToken.location.fileName) != 0
        state.active = state.parentActive && !state.foundActive && res
        state.foundActive = state.foundActive || state.active
        removeNewLine(src)
    }

    private fun readExpression(src: TokenStream, directiveToken: Token): MutableList<Token> {
        val expr = mutableListOf<Token>()
        while (!src.eof()) {
            val t = forceNextMarkedToken(src, directiveToken)
            if (t is NewLineToken) {
                break
            }
            if (t is IdentifierToken) {
                val macro = defines[t.name]
                if (macro?.body != null) {
                    expr.addAll(macro.body)
                } else {
                    expr.add(t)
                }
            } else {
                expr.add(t)
            }
        }
        src.removeMarked()
        return expr
    }

    private fun processEndifDirective(src: TokenStream, directiveToken: Token) {
        src.removeLast()
        if (conditionStack.size() <= 1) {
            errorIfNotFound(directiveToken)
        }
        conditionStack.pop()
        removeUntilNewLine(src)
    }


    private fun readMacroArgsDefinition(src: TokenStream, openBracket: Token): List<String> {
        val args = mutableListOf<String>()
        var allowedName = true
        var allowedComma = false
        var allowedEnd = true

        var token = openBracket
        while (!src.eof()) {
            token = src.nextMarkedToken()
            when {
                token.isBracket(')') -> {
                    if (!allowedEnd) {
                        errorUnexpectedBracket(token)
                    }
                    return args
                }
                token is NewLineToken -> errorMissingCloseBracket(openBracket)
                token is CommaToken -> {
                    if (!allowedComma) {
                        errorWrongMacroSyntax(token)
                    }
                    allowedComma = false
                    allowedName = true
                    allowedEnd = false
                }
                token is IdentifierToken -> {
                    if (!allowedName) {
                        errorWrongMacroSyntax(token)
                    }
                    args.add(token.name)
                    allowedName = false
                    allowedComma = true
                    allowedEnd = true
                }
            }
        }
        errorUnexpectedEof(token)
    }


    private fun readMacroBody(src: TokenStream, firstToken: Token?): List<Token>? {
        if (firstToken is NewLineToken) {
            return null
        }
        var token = firstToken
        var backslash = false
        val result = mutableListOf<Token>()
        var hasToken = token != null
        while (!src.eof() || hasToken) {
            when (token) {
                is LineCommentToken -> {
                    return result
                }
                is NewLineToken -> {
                    if (!backslash) {
                        return result
                    }
                    result.add(token)
                    backslash = false
                }
                is BackslashToken -> {
                    backslash = true
                }
                else -> {
                    result.add(token!!)
                    backslash = false
                }
            }
            if (src.eof()) {
                break
            }
            token = src.nextMarkedToken()
            hasToken = true
        }
        return result
    }

    private fun defineMacro(name: IdentifierToken, body: List<Token>? = null, args: List<String>? = null) {
        checkCircularDefine(name.name, body)
        defines[name.name] = Macro(name.name, name.location, body, args)
    }

    fun define(name: String, body: List<Token>?) {
        val loc = SourceLocation("<cmd>", 0, 0)
        checkCircularDefine(name, body)
        defines[name] = Macro(name, loc, body, null)
    }

    private fun checkCircularDefine(name: String, body: List<Token>?) {
        if (body != null) {
            for (t in body) {
                if (t is IdentifierToken) {
                    if (t.name == name) {
                        errorCircularDefines(t)
                    }
                    val def = defines[t.name]
                    if (def != null) {
                        checkCircularDefine(name, def.body)
                    }
                }
            }
        }
    }


    private fun includeFile(src: TokenStream, fileName: String, location: SourceLocation) {
        val relativePath = resolveIncludePath(src.path, fileName)
        if (tryIncludeFile(relativePath, src)) {
            return
        }
        val filePath = findInIncludePaths(fileName)
        if (filePath != null && tryIncludeFile(filePath, src)) {
            return
        }

        throw PreprocessorException("File not found: \"$fileName\"", location)
    }

    private fun tryIncludeFile(relativePath: String, src: TokenStream): Boolean {
        try {
            val stream = processFile(relativePath)
            src.replaceMarked(stream)
            return true
        } catch (_: IOException) {}
        return false
    }

    private fun findInIncludePaths(fileName: String): String? {
        for (path in includePaths) {
            val candidate = path + FILE_SEPARATOR + fileName
            if (fileExists(candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun replaceDefine(src: TokenStream, t: IdentifierToken, def: Macro, filePath: String) {
        if (def.body == null) {
            return
        }
        src.markLast()
        val body = def.body.toMutableList()
        processBuiltinInBody(body, t.location.line)
        if (def.args.isNullOrEmpty()) {
            src.replaceMarked(body)
            return
        }

        val openBracket = src.nextMarkedToken()
        if (openBracket.isBracket('(')) {
            val args = mutableListOf<List<Token>>()

            extractCallArguments(
                    maxArgs = def.args.size,
                    openBracket = openBracket,
                    nextToken = { src.nextMarkedToken() },
                    onArgReady = { args.add(it.toMutableList()); },
                    onError = { token, msg -> throw PreprocessorException(msg, token.location) }
            )
            if (args.size < def.args.size) {
                errorTooFewArgumentsProvided(t)
            }
            val newBody = def.body.toMutableList()
            for ((i, element) in newBody.withIndex()) {
                val bodyToken = element
                if (bodyToken is IdentifierToken && bodyToken.name in def.args) {
                    val argIndex = def.args.indexOf(bodyToken.name)
                    newBody[i] = GroupToken(args[argIndex].toMutableList(), bodyToken.location)
                }
            }
            src.replaceMarked(newBody)
        } else {
            processToken(openBracket, src, filePath)
        }
    }

    private fun warningDefineNotFound(name: IdentifierToken) {
        console.warning("${name.name} undefined", name.location)
    }

    open fun warning(msg: String, location: SourceLocation) {
        console.warning(msg, location)
    }

    private fun checkIfCondition(): Boolean = conditionStack.peek()!!.active

    fun getStringMacro(name: String): String? {
        if (name == "__COUNTER__") {
            return counter.toString()
        }
        val m = defines[name] ?: return null
        if (m.body == null) {
            return ""
        }
        if (m.body.size != 1) {
            var res = ""
            for (token in m.body) {
                res += token.str()
            }
            return res
        }
        val node = m.body[0]
        when (node) {
            is StringLiteralToken -> return node.str
            else -> {

            }
        }
        return node.str()
    }

    fun getMacroLocation(name: String): SourceLocation? {
        val m = defines[name] ?: return null
        return m.location
    }


}

private fun parentPath(path: String, backTo: Int = 1): String {
    var p = Path(path)
    repeat(backTo) {
        val parent = p.parent
        if (parent != null) {
            p = parent
        }
    }
    return p.toString()
}

private fun resolveIncludePath(currentPath: String, includedName: String): String {
    var backTo = 1
    var includePath = includedName
    while (includePath.startsWith("../") || includePath.startsWith("..\\")) {
        includePath = includePath.substring(3)
        backTo++
    }
    return parentPath(currentPath, backTo) + FILE_SEPARATOR + includePath
}

private fun Token.isNextAfter(prev: IdentifierToken): Boolean =
    location.fileName == prev.location.fileName &&
            location.line == prev.location.line &&
            location.column == prev.location.column + prev.name.length

private fun removeNewLine(stream: TokenStream) {
    if (!stream.eof()) {
        val t = stream.nextToken()
        if (t is NewLineToken) {
            stream.removeLast()
        } else {
            stream.unreadLast()
        }
    }
}

private fun removeUntilNewLine(stream: TokenStream) {
    while (!stream.eof()) {
        val t = stream.nextToken()
        stream.removeLast()
        if (t is NewLineToken) {
            break
        }
    }
}
