package ru.trolsoft.therat.avr.compile

import ru.trolsoft.compiler.parser.DoWhileNode
import ru.trolsoft.compiler.parser.GotoNode
import ru.trolsoft.compiler.parser.IfNode
import ru.trolsoft.therat.avr.AvrRatCompiler


internal fun compileDoWhile(compiler: AvrRatCompiler, node: DoWhileNode) {
// do {
//      ...
// } while (expr)

//start:
//    ...
//    if (expr) goto start

    val startLabel = compiler.addProcLabel("do_while", node.getLocation(), true)
    compileBody(compiler, node)
    val condLoc = node.cond.getLocation()
    val ifNode = IfNode(condLoc, node.cond, GotoNode(condLoc, startLabel), null)
    compileIf(compiler, ifNode)
}


private fun compileBody(compiler: AvrRatCompiler, node: DoWhileNode) {
    // TODO push block
    compiler.compileNode(node.body)
    // TODO pop block
}
