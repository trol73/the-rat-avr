package ru.trolsoft.therat.avr.compiler.bus

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.parser.IdentifierNode
import ru.trolsoft.therat.arch.avr.AvrDevice
import ru.trolsoft.therat.avr.AvrRatCompiler
import ru.trolsoft.therat.avr.BusDeclarationNode
import ru.trolsoft.therat.avr.BusRangeNode
import ru.trolsoft.therat.avr.Pin

internal fun parseBusDefinition(compiler: AvrRatCompiler, node: BusDeclarationNode, dev: AvrDevice) {
    val pins = mutableListOf<Pin>()
    for (n in node.pins) {
        when (n) {
            is IdentifierNode -> {
                val pin = compilePin(compiler, n, dev) ?: errorInvalidPin(n)
                pins.add(pin)
            }
            is BusRangeNode -> {
                val pinFrom = compilePin(compiler, n.from, dev)
                val pinTo = compilePin(compiler, n.to, dev)
                if (pinFrom == null || pinTo == null || pinFrom == pinTo || pinFrom.port != pinTo.port) {
                    errorWrongRange(n)
                }
                if (pinFrom.pin < pinTo.pin) {
                    for (i in pinFrom.pin..pinTo.pin) {
                        pins.add(Pin(pinFrom.port, i))
                    }
                } else {
                    for (i in pinFrom.pin downTo pinTo.pin) {
                        pins.add(Pin(pinFrom.port, i))
                    }
                }
            }
            else -> {
                internalError()
            }
        }
    }
    if (node.size >= 0 && node.size != pins.size) {
        errorWrongBusSize(node, pins.size)
    }
    compiler.buses[node.name] = pins
}


private fun compilePin(compiler: AvrRatCompiler, node: IdentifierNode, dev: AvrDevice): Pin? {
    val name = node.name
    if (name.length != 2) {
        return compiler.pins[name]
    }
    val portChar = name[0]
    val numChar = name[1]
    if (numChar !in '0'..'7') {
        return compiler.pins[name]
    }
    if (!dev.hasPort(portChar)) {
        throw CompilerException("Device ${dev.name} doesn't have port $portChar", node.getLocation())
    }
    val pinBit = numChar.toString().toInt()
    return Pin(portChar, pinBit)
}

private fun errorWrongRange(node: BusRangeNode): Nothing {
    throw CompilerException("Wrong pins range", node.getLocation())
}

private fun errorInvalidPin(node: IdentifierNode): Nothing {
    throw CompilerException("Invalid device pin: ${node.name}", node.getLocation())
}

private fun errorWrongBusSize(node: BusDeclarationNode, size: Int): Nothing {
    throw CompilerException("Invalid bus size: ${node.name}, expected ${node.size} but found $size", node.getLocation())
}
