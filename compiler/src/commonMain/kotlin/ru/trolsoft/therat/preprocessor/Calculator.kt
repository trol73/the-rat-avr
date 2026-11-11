package ru.trolsoft.therat.preprocessor

import ru.trolsoft.compiler.compiler.Compiler
import ru.trolsoft.compiler.compiler.errorUndefinedIdentifier
import ru.trolsoft.compiler.compiler.errorWrongExpression
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.parser.*
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.lang.IdentifierToken
import ru.trolsoft.therat.lang.Scanner
import ru.trolsoft.therat.lang.Token

private class PpcParser(stream: TokenStream): Parser(stream) {
    override fun parseFuncCallArgument(): Node {
        val t = nextToken()
        if (t is IdentifierToken) {
            return IdentifierNode(t.location, t.name)
        }
        errorUnexpectedToken(t)
    }

    override fun parse(): AstNode {
        val node = parseExpression()
        return AstNode(listOf(node))
    }
}

private class PpcCompiler(
    val preprocessor: Preprocessor,
) : Compiler(Scanner(), "", ConsoleReporter({ }, { })) {
    override fun compile(ast: AstNode) {
    }

    override fun compileAssembler(node: AssemblerInstrNode, comment: String?) {
    }

    override fun resolveArg(arg: Node): Node = arg

    override fun parse(stream: TokenStream): AstNode = PpcParser(stream).parse()

    fun calculate(stream: TokenStream): Int {
        val ast = parse(stream)
        if (ast.size() != 1) {
            errorWrongExpression(ast)
        }
        return calculateConst(ast[0])
    }

    override fun calculateConst(node: Node): Int {
        when (node) {
            is BinaryOperNode -> {
                if (node.left is StringLiteralNode && node.right is StringLiteralNode) {
                    if (node.operator == Operator.EQ) {
                        return if (node.left.str == node.right.str) 1 else 0
                    } else if (node.operator == Operator.NOT_EQ) {
                        return if (node.left.str == node.right.str) 0 else 1
                    }
                }
            }
            is IdentifierNode -> {
                val value = preprocessor.getStringMacro(node.name) ?: errorUndefinedIdentifier(node)
                return ru.trolsoft.compiler.utils.parseNumber(value) ?: errorWrongExpression(node)
            }
        }
        return super.calculateConst(node)
    }

}

internal fun checkExpr(ppc: Preprocessor, expr: MutableList<Token>, path: String): Int {
    val stream = TokenStream(expr, path)
    val compiler = PpcCompiler(ppc)
    return compiler.calculate(stream)
}