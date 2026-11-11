package ru.trolsoft.therat.avr.compiler

import ru.trolsoft.compiler.parser.DoWhileNode
import ru.trolsoft.compiler.parser.GotoNode
import ru.trolsoft.compiler.parser.IfNode
import ru.trolsoft.therat.avr.AvrRatCompiler


internal fun AvrRatCompiler.compileDoWhile(node: DoWhileNode) {
// do {
//      ...
// } while (expr)

//start:
//    ...
//    if (expr) goto start

    val startLabel = addProcLabel("do_while", node.getLocation(), true)
    compileBody(node)
    val condLoc = node.cond.getLocation()
    val ifNode = IfNode(condLoc, node.cond, GotoNode(condLoc, startLabel), null)
    compileIf(ifNode)
}


private fun AvrRatCompiler.compileBody(node: DoWhileNode) {
    compileNode(node.body)
}
