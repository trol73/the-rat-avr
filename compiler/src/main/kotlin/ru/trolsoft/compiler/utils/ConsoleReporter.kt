package ru.trolsoft.compiler.utils

import ru.trolsoft.compiler.lexer.SourceLocation

class ConsoleReporter(
    val stdout: Printer,
    val stderr: Printer,
    val exitProcessOnFatal: Boolean = false
) {
    fun warning(msg: String, location: SourceLocation? = null) {
        if (location == null) {
            stderr.println("WARNING: $msg")
        } else {
            stderr.println("WARNING: ${location.str()}: $msg")
        }
    }

    fun error(msg: String, location: SourceLocation? = null) {
        if (location == null) {
            stderr.println("ERROR: $msg")
        } else {
            stderr.println("ERROR: ${location.str()}: $msg")
        }
    }

    fun fatal(msg: String) {
        stderr.println(msg)
        if (exitProcessOnFatal) {
            exitProcess(100)
        }
    }

    fun println(msg: String) {
        stdout.println(msg)
    }
}