package ru.trolsoft.therat.lang

import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.utils.isDigit
import ru.trolsoft.compiler.utils.isLetter
import java.lang.StringBuilder

sealed class Token {
    abstract val location: SourceLocation

    abstract fun str(): String

    fun isBracket(ch: Char): Boolean = this is BracketToken && this.bracket == ch
    fun isKeyword(s: String): Boolean = this is KeywordToken && this.name == s
    fun isOperator(name: String): Boolean = this is OperatorToken && this.name == name
//    fun isExpressionToken(): Boolean =
//            this is IdentifierToken || this is NumberToken || this is RegisterToken
}

data class NewLineToken(override val location: SourceLocation) : Token() {
    override fun str(): String = "\n"
}

data class NumberToken(val value: Int, override val location: SourceLocation, val radix: Int = 10) : Token() {
    override fun str(): String {
        return when (radix) {
            10 -> value.toString()
            16 -> "0x" + value.toString(16)
            2 -> "0b" + value.toString(2)
            8 -> "0" + value.toString(8)
            else -> "<wrong number argument, radix= $radix>"
        }
    }
}

//data class HexNumberToken(val value: Int, override val location: SourceLocation) : Token() {
//    override fun str(): String = "0x" + value.toString(16)
//}
//
//data class BinaryNumberToken(val value: Int, override val location: SourceLocation) : Token() {
//    override fun str(): String = "0b" + value.toString(2)
//}
//
//data class OctalNumberToken(val value: Int, override val location: SourceLocation) : Token() {
//    override fun str(): String = "0" + value.toString(8)
//}

data class OperatorToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = name
}

data class LineCommentToken(val text: String, override val location: SourceLocation) : Token() {
    override fun str(): String = ";$text"
}

data class BlockCommentToken(val text: String, override val location: SourceLocation) : Token() {
    override fun str(): String = "/*$text*/"
}

data class CharLiteralToken(val char: Char, override val location: SourceLocation) : Token() {
    override fun str(): String = "'$char'"
}

data class StringLiteralToken(val str: String, override val location: SourceLocation) : Token() {
    override fun str(): String = "\"$str\""
}

data class BracketToken(val bracket: Char, override val location: SourceLocation) : Token() {
    override fun str(): String = bracket.toString()
}

data class CommaToken(override val location: SourceLocation) : Token() {
    override fun str(): String = ","
}

data class DotToken(override val location: SourceLocation) : Token() {
    override fun str(): String = "."
}

data class ColonToken(override val location: SourceLocation) : Token() {
    override fun str(): String = ":"
}

data class BackslashToken(override val location: SourceLocation) : Token() {
    override fun str(): String = "\\"
}

data class PreprocessorToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = "#$name"
}

data class LabelToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = "$name:"
}

data class GlobalLabelToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = "@$name:"
}

data class KeywordToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = name
}

data class DataTypeToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = name
}

data class IdentifierToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = name
}

data class RegisterToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = name
}

data class AssemblerToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = name
}



data class GroupToken(val tokens: MutableList<Token>, override val location: SourceLocation) : Token() {
    override fun str(): String = strTokens(tokens)
}

data class EmptyToken(override val location: SourceLocation) : Token() {
    override fun str(): String = ""
}

data class DirectiveToken(val name: String, override val location: SourceLocation) : Token() {
    override fun str(): String = "$$name"
}

fun strTokens(tokens: List<Token>): String {
    val sb = StringBuilder()
    for (t in tokens) {
        if (t !is NewLineToken) {
            val last = sb.lastOrNull()
            if (last != null && (isLetter(last) || isDigit(last))) {
                sb.append(' ')
            }
        }
        sb.append(t.str())
    }
    return sb.toString()
}

// # @ $ ~ ?