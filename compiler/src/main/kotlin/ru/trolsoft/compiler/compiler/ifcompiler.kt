package ru.trolsoft.compiler.compiler

import ru.trolsoft.compiler.parser.*

// if (a && b) goto lbl        -> if (!a) goto @end    if (b) goto lbl     @end:
// if (a || b) goto lbl        -> if (a) goto lbl      if (b) goto lbl
//
// if (!(a && b)) goto lbl = if (!a || !b) goto lbl
// if (!(a || b)) goto lbl = if (!a && !b) goto lbl


fun decomposeIf(node: IfNode, labelPrefix: String, result: MutableList<Node>) {
    val cond = node.cond
    val label = (node.thenBlock as GotoNode).label
    when {
        cond is BinaryOperNode && cond.operator == Operator.LOGICAL_AND -> {
            // if (a && b) goto lbl        -> if (!a) goto @end    if (b) goto lbl     @end:
            val labelEnd = "$labelPrefix@end"
            val a = buildIfGotoNode(cond.left, labelEnd, true)
            val b = buildIfGotoNode(cond.right, label)
            decomposeIf(a, "$labelPrefix@and1", result)
            decomposeIf(b, "$labelPrefix@and2", result)
            result.add(LabelNode(cond.getLocation(), labelEnd))
            return
        }
        cond is BinaryOperNode && cond.operator == Operator.LOGICAL_OR -> {
            // if (a || b) goto lbl        -> if (a) goto lbl      if (b) goto lbl
            val a = buildIfGotoNode(cond.left, label)
            val b = buildIfGotoNode(cond.right, label)
            decomposeIf(a, "$labelPrefix@or1", result)
            decomposeIf(b, "$labelPrefix@or2", result)
            return
        }
        cond is PrefixOperNode && cond.operator == Operator.LOGICAL_NOT -> {
            val expr = cond.expression
            when {
                expr is BinaryOperNode && expr.operator == Operator.LOGICAL_AND -> {
                    // if (!(a && b)) goto lbl = if (!a || !b) goto lbl
                    // if (!(a && b)) goto lbl     -> if (!a) goto lbl     if (!b) goto lbl
                    val a = buildIfGotoNode(expr.left, label, true)
                    val b = buildIfGotoNode(expr.right, label, true)
                    decomposeIf(a, "$labelPrefix@not_and1", result)
                    decomposeIf(b, "$labelPrefix@not_and2", result)
                    return
                }
                expr is BinaryOperNode && expr.operator == Operator.LOGICAL_OR -> {
                    // if (!(a || b)) goto lbl = if (!a && !b) goto lbl
                    // if (!(a || b)) goto lbl     -> if (a) goto @end     if (!b) goto lbl    @end:
                    val labelEnd = "$labelPrefix@end"
                    val a = buildIfGotoNode(expr.left, labelEnd)
                    val b = buildIfGotoNode(expr.right, label, true)
                    decomposeIf(a, "$labelPrefix@not_or1", result)
                    decomposeIf(b, "$labelPrefix@not_or2", result)
                    result.add(LabelNode(cond.getLocation(), labelEnd))
                    return
                }
                expr is PrefixOperNode && expr.operator == Operator.LOGICAL_NOT -> {
                    // if (!(!a)) goto lbl = if (a) goto lbl
                    val a = buildIfGotoNode(expr.expression, label)
                    decomposeIf(a, labelPrefix, result)
                    return
                }
            }
        }
    }
    result.add(node)
}


private fun buildIfGotoNode(expr: Node, label: String, inverse: Boolean = false): IfNode {
    val loc = expr.getLocation()
    val gotoNode = GotoNode(loc, label)
    val cond = if (inverse)
        PrefixOperNode(loc, Operator.LOGICAL_NOT, expr)
    else
        expr
    return IfNode(loc, cond, gotoNode, null)
}
