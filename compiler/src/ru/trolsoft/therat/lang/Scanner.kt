package ru.trolsoft.therat.lang

import ru.trolsoft.compiler.lexer.LexerException
import ru.trolsoft.compiler.lexer.LexerInput
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.utils.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

private const val operators = "+-=!<>|&*/^%~"


open class Scanner {
    private var firstTokenInLine: Boolean = true
    private var assemblerLine: Boolean = false

    fun scan(fileName: String): List<Token> {
        val f = File(fileName)
        return scan(f.absolutePath, FileInputStream(f))
    }

    fun scan(fileName: String, stream: InputStream): List<Token> {
        val result = mutableListOf<Token>()
        val input = LexerInput(stream, fileName)
        firstTokenInLine = true
        var token: Token?
        while (true) {
            token = nextToken(input)
            if (token == null) {
                break
            }
            result.add(token)
            firstTokenInLine = token is NewLineToken
        }
        return result
    }

    private fun nextToken(input: LexerInput): Token? {
        val loc = input.getLocation()
        val ch = input.read()
        when {
            ch == LexerInput.EOF -> return null
            isLineEnd(ch) -> return processNewLine(input, loc)
            isDigit(ch) -> return processDigit(input, ch)
            ch == ';' -> return processLineComment(input)
            isOperator(ch) -> return processOperator(input, ch)
            ch == '\'' -> return  processSingleQuote(input)
            ch == '"' -> return  processDoubleQuote(input)
            isBracket(ch) -> return processBracket(input, ch)
            ch == ',' -> return processCommaToken(input)
            ch == '.' -> return processDotToken(input)
            ch == ':' -> return processColonToken(input)
            ch == '#' -> return processPreprocessorDirective(input, loc)
            ch == '@' -> return processGlobalLabel(input)
            ch == '\\' -> return processBackslash(input)
            isLetter(ch) -> return processIdentifier(input, loc)
            isWhitespace(ch) -> {
                skipWhitespace(input)
                return nextToken(input)
            }
            ch == '$' && firstTokenInLine -> return processDirective(input)
        }
        throw LexerException("Unexpected token", input.getLocation())
    }


    private fun skipWhitespace(input: LexerInput) {
        while (true) {
            val ch = input.read()
            if (!isWhitespace(ch)) {
                input.backup()
                input.extractText()
                return
            }
        }
    }

    private fun processNewLine(input: LexerInput, loc: SourceLocation): Token {
        input.readWhile { isLineEnd(it) || isWhitespace(it)}
        input.extractText()
        assemblerLine = false
        return NewLineToken(loc)
    }

    private fun processDigit(input: LexerInput, ch: Char): Token {
        if (ch == '0') {
            val second = input.read()
            val base: Int
            base = if (second == 'x' || second == 'X') {
                16
            } else if (second == 'b' || second == 'B') {
                2
            } else if (isDigit(second)) {
                8
            } else if (isLetter(second) && second != '_') {
                val loc = input.getLocation()
                throw LexerException("wrong number", loc)
            } else {
                input.backup()
                val loc = input.getLocation()
                val value = input.extractText().toInt()
                return NumberToken(value, loc)
            }
            input.readWhile { isNumberChar(it, base) || it == '_' }
            val loc = input.getLocation()
            val text = input.extractText().replace("_", "")
            when (base) {
                2 -> return NumberToken(text.substring(2).toLong(2).toInt(), loc, 2)
                8 -> return NumberToken(text.substring(1).toLong(8).toInt(), loc, 8)
                16 -> return NumberToken(text.substring(2).toLong(16).toInt(), loc, 16)
            }
        }
        input.readWhile { isDigit(it) }
        val loc = input.getLocation()
        val text = input.extractText().replace("_", "")
        return NumberToken(text.toInt(), loc)
    }

    private fun processLineComment(input: LexerInput): LineCommentToken {
        val loc = input.getLocation()
        input.extractText()
        input.readWhile { !isLineEnd(it) }
        return LineCommentToken(input.extractText(), loc)
    }

    private fun processOperator(input: LexerInput, ch: Char): Token? {
        val loc = input.getLocation()

        fun processSlashOperator(): Token {
            val ch1 = input.read()
            when (ch1) {
                '*' -> {
                    while (true) {
                        input.readWhile { it != '*' }
                        val ch2 = input.read()
                        if (ch2 == LexerInput.EOF) {
                            throw LexerException("Unclosed comment block", loc)
                        } else if (ch2 == '*' && input.read() == '/') {
                            val text = input.extractText()
                            return BlockCommentToken(text.substring(2, text.length-2), loc)
                        }
                    }
                }
                '=' -> return OperatorToken("/=", loc)
                else -> {
                    input.backup()
                    return OperatorToken(input.extractText(), loc)
                }
            }
        }


        if (ch == '/') {
            return processSlashOperator()
        }
        val ch2 = input.read()
        if (ch == ch2) {
            if (ch == '>' || ch == '<') {
                val ch3 = input.read()
                if (ch3 != '=') {
                    input.backup()

                }
                return OperatorToken(input.extractText(), loc)
            } else if (ch == '|' || ch == '&' || ch == '+' || ch == '-' || ch == '=') {
                return OperatorToken(input.extractText(), loc)
            }
            input.backup()
            return OperatorToken(input.extractText(), loc)
        } else if (ch2 == '=') {
            return OperatorToken(input.extractText(), loc)
        } else if (ch == '-' && ch2 == '>') {
            return OperatorToken(input.extractText(), loc)
        }
        input.backup()
        return OperatorToken(input.extractText(), loc)
    }

    private fun processSingleQuote(input: LexerInput): CharLiteralToken {

        fun checkIncorrectLiteral(ch: Char) {
            if (ch == '\n' || ch == '\r') {
                input.backup()
                input.extractText()
                throw LexerException("Incorrect character literal", input.getLocation())
            } else if (ch == '\t') {
                throw LexerException("Incorrect character literal", input.getLocation())
            }
        }


        val ch = input.read()
        if (ch == '\'') {
            throw LexerException("Empty character literal", input.getLocation())
        }
        checkIncorrectLiteral(ch)
        val ch2 = input.read()
        checkIncorrectLiteral(ch2)
        if (ch != '\\') {
            if (ch2 != '\'') {
                throw LexerException("Incorrect character literal", input.getLocation())
            }
            val loc = input.getLocation()
            input.extractText()
            return CharLiteralToken(ch, loc)
        } else {
            if (input.read() != '\'') {
                throw LexerException("Incorrect character literal", input.getLocation())
            }
            val loc = input.getLocation()
            input.extractText()
            when (ch2) {
                'n' -> return CharLiteralToken('\n', loc)
                'r' -> return CharLiteralToken('\r', loc)
                't' -> return CharLiteralToken('\t', loc)
                '\\' -> return CharLiteralToken('\\', loc)
                '\'' -> return CharLiteralToken('\'', loc)
            }
            throw LexerException("Illegal escape", loc)
        }
    }


    private fun processDoubleQuote(input: LexerInput): StringLiteralToken {
        val result = StringBuilder()
        while (true) {
            val ch = input.read()
            if (ch == '\\') {
                val ch2 = input.read()
                when (ch2) {
                    '\\' -> result.append('\\')
                    'n' -> result.append('\n')
                    'r' -> result.append('\r')
                    't' -> result.append('\t')
                    '"' -> result.append('"')
                    'x' -> {
                        val ch3 = input.read()
                        if (!isHexDigit(ch3)) {
                            input.backup()
                            input.extractText()
                            throw LexerException("Illegal escape", input.getLocation())
                        }
                        val ch4 = input.read()
                        if (!isHexDigit(ch4)) {
                            input.backup()
                            input.extractText()
                            throw LexerException("Illegal escape", input.getLocation())
                        }
                        result.append((ch3.toString() + ch4).toInt(16).toChar())
                    }
                    else -> {
                        input.backup()
                        input.extractText()
                        throw LexerException("Illegal escape", input.getLocation())
                    }
                }
            } else if (ch == '"') {
                val loc = input.getLocation()
                input.extractText()
                return StringLiteralToken(result.toString(), loc)
            } else if (ch == '\r' || ch == '\n' || ch == LexerInput.EOF) {
                input.backup()
                input.extractText()
                throw LexerException("Incorrect string literal", input.getLocation())
            } else {
                result.append(ch)
            }
        }
    }


    private fun processBracket(input: LexerInput, ch: Char): BracketToken {
        val loc = input.getLocation()
        input.extractText()
        return BracketToken(ch, loc)
    }

    private fun processCommaToken(input: LexerInput): CommaToken {
        val loc = input.getLocation()
        input.extractText()
        return CommaToken(loc)
    }

    private fun processDotToken(input: LexerInput): DotToken {
        val loc = input.getLocation()
        input.extractText()
        return DotToken(loc)
    }

    private fun processColonToken(input: LexerInput): ColonToken {
        val loc = input.getLocation()
        input.extractText()
        return ColonToken(loc)
    }

    private fun processBackslash(input: LexerInput): BackslashToken {
        val loc = input.getLocation()
        input.extractText()
        return BackslashToken(loc)
    }


    private fun processPreprocessorDirective(input: LexerInput, loc: SourceLocation): PreprocessorToken {
        input.readWhile { isLetter(it) }
        val name = input.extractText().substring(1)
        if (!isPreprocessorDirective(name)) {
            throw LexerException("Invalid preprocessor directive: $name", loc)
        }
        return PreprocessorToken(name, loc)
    }

    private fun processIdentifier(input: LexerInput, loc: SourceLocation): Token {
        input.readWhile { isLetter(it) || isDigit(it) }
        val str = input.extractText()
        if (firstTokenInLine) {
            val ch = input.read()
            if (ch == ':') {
                input.extractText()
                return LabelToken(str, loc)
            } else if (ch != LexerInput.EOF) {
                input.backup()
            }
        }
        return when {
            isKeyword(str) -> {
                if (checkKeyword(str, firstTokenInLine, assemblerLine)) {
                    KeywordToken(str, loc)
                } else {
                    IdentifierToken(str, loc)
                }
//                if (!assemblerLine) {
//                    KeywordToken(str, loc)
//                } else {
//                    IdentifierToken(str, loc)
//                }
            }
            isDataType(str) -> DataTypeToken(str, loc)
            isRegister(str) -> RegisterToken(str, loc)
            isAssemblerInstruction(str) -> {
                assemblerLine = true
                AssemblerToken(str, loc)
            }
            else -> IdentifierToken(str, loc)
        }
    }

    private fun processGlobalLabel(input: LexerInput): GlobalLabelToken {
        val loc = input.getLocation()
        if (!isLetter(input.read())) {
            throw LexerException("Unexpected char", loc)
        }
        input.readWhile { isLetter(it) || isDigit(it) }
        val name = input.extractText().substring(1)
        val ch = input.read()
        if (ch != ':') {
            throw LexerException("Unexpected char", loc)
        }
        return GlobalLabelToken(name, loc)
    }

    private fun processDirective(input: LexerInput): DirectiveToken {
        val loc = input.getLocation()
        if (!isLetter(input.read())) {
            throw LexerException("Unexpected char", loc)
        }
        input.readWhile { isLetter(it) || isDigit(it) }

        val name = input.extractText().substring(1)
        return DirectiveToken(name, loc)
    }



    open fun isKeyword(s: String): Boolean = false
    open fun checkKeyword(name: String, firstLineToken: Boolean, assemblerLine: Boolean): Boolean = !assemblerLine
    open fun isPreprocessorDirective(s: String): Boolean = false
    open fun isDataType(s: String): Boolean = false
    open fun isRegister(s: String): Boolean = false
    open fun isAssemblerInstruction(s: String): Boolean = false
}

private fun isOperator(ch: Char): Boolean = operators.contains(ch)

private fun isBracket(ch: Char): Boolean = ch == '(' || ch == ')' || ch == '{' || ch == '}' || ch == '[' || ch == ']'