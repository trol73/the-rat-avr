package ru.trolsoft.therat.preprocessor

import ru.trolsoft.therat.lang.*
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.utils.Stack
import java.io.File
import java.io.FileNotFoundException

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

open class Preprocessor(private val fileLoader: FileLoader) {

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

    fun process(filePath: String): TokenStream {
        conditionStack.clear()
        conditionStack.push(State(active = true, parentActive = true))
        return processFile(filePath)
    }

    fun define(name: String, body: List<Token>?) {
        val loc = SourceLocation("<cmd>", 0, 0)
        defines[name] = Macro(name, loc, body, null)
    }

    private fun processFile(filePath: String): TokenStream {
        val src = fileLoader.loadSource(filePath)

        while (!src.eof()) {
            val t = src.nextToken()
            processToken(t, src)
        }
        src.reset()
        return src
    }


    private fun processToken(t: Token, src: TokenStream) {
        val active = checkIfCondition()
        if (t is PreprocessorToken) {
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
                    // elif if
                    // error warning
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
        } else if (!active) {
            src.removeLast()
        } else if (t is IdentifierToken) {
            val def = defines[t.name]
            if (def != null) {
                val state = src.saveState()
                replaceDefine(src, t, def)
                src.restoreState(state)
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
        val t = forceNextMarkedToken(src, directiveToken)
        when (t) {
            is StringLiteralToken -> {
                includeFile(src, t)
            }
            is OperatorToken -> {
                if (t.name != "<") {
                    errorWrongIncludeSyntax(t)
                }
                src.unmarkAll()
            } else -> {
                errorWrongIncludeSyntax(t)
            }
        }
    }


    private fun processDefineDirective(src: TokenStream, directiveToken: Token) {
        src.removeLast()
        val name = forceNextMarkedToken(src, directiveToken)
        if (name !is IdentifierToken) {
            errorIdentifierExpected(name)
        }
        val token = forceNextMarkedToken(src, name)
        if (token.isBracket('(') && token.isNextAfter(name)) {
            val args = readMacroArgsDefinition(src, token)
            val body = readMacroBody(src, src.nextMarkedToken())
            defineMacro(name, body, args)
        } else  {
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
        val res = parentActive && checkExpr(expr, directiveToken.location.fileName) != 0
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
        val res = state.parentActive && checkExpr(expr, directiveToken.location.fileName) != 0
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
        removeNewLine(src)
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


    private fun readMacroBody(src: TokenStream, firstToken: Token): List<Token>? {
        if (firstToken is NewLineToken) {
            return null
        }
        var token = firstToken
        var backslash = false
        val result: MutableList<Token> = mutableListOf()
        while (!src.eof()) {
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
                    result.add(token)
                    backslash = false
                }
            }
            token = src.nextMarkedToken()
        }
        return result
    }

    private fun defineMacro(name: IdentifierToken, body: List<Token>? = null, args: List<String>? = null) {
        defines[name.name] = Macro(name.name, name.location, body, args)
    }


    private fun includeFile(src: TokenStream, token: StringLiteralToken) {
        val filePath = resolveIncludePath(src.path, token.str)
        try {
            val stream = processFile(filePath)
            src.replaceMarked(stream)
        } catch (e: FileNotFoundException) {
            throw PreprocessorException("File not found: \"${token.str}\"", token.location, e)
        }
    }

    private fun replaceDefine(src: TokenStream, t: IdentifierToken, def: Macro) {
        if (def.body == null) {
            return
        }
        src.markLast()
        if (def.args != null && def.args.isNotEmpty()) {
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
                for (i in 0 until newBody.size) {
                    val bodyToken = newBody[i]
                    if (bodyToken is IdentifierToken && bodyToken.name in def.args) {
                        val argIndex = def.args.indexOf(bodyToken.name)
                        newBody[i] = GroupToken(args[argIndex].toMutableList(), bodyToken.location)
                    }
                }
                src.replaceMarked(newBody)
            } else {
                processToken(openBracket, src)
            }
        } else {
            src.replaceMarked(def.body.toMutableList())
        }
    }

    private fun warningDefineNotFound(name: IdentifierToken) {
        warning("${name.name} undefined\n", name.location)
    }

    open fun warning(msg: String, location: SourceLocation) {
        print("WARNING: ${location.str()} $msg\n")
    }

    private fun checkIfCondition(): Boolean = conditionStack.peek()!!.active

    fun getStringMacro(name: String): String? {
        val m = defines[name] ?: return null
        if (m.body == null) {
            return ""
        }
        if (m.body.size != 1) {
            errorOneValueExpected(m.body[1])
        }
        val node = m.body[0]
        when (node) {
            is StringLiteralToken -> return node.str
        }
        return node.str()
    }

    fun getMacroLocation(name: String): SourceLocation? {
        val m = defines[name] ?: return null
        return m.location
    }


}

private fun parentPath(path: String, backTo: Int = 1): String {
    var f = File(path)
    for (i in 1..backTo) {
        f = f.parentFile
    }
    return f.absolutePath
}

private fun resolveIncludePath(currentPath: String, includedName: String): String {
    var backTo = 1
    var includePath = includedName
    while (includePath.startsWith("../") || includePath.startsWith("..\\")) {
        includePath = includePath.substring(3)
        backTo++
    }
    return parentPath(currentPath, backTo) + File.separatorChar + includePath
}

private fun Token.isNextAfter(prev: IdentifierToken): Boolean =
    location.fileName == prev.location.fileName &&
            location.line == prev.location.line &&
            location.column == prev.location.column + prev.name.length
