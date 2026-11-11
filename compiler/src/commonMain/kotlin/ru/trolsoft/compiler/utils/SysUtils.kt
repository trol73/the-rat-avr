package ru.trolsoft.compiler.utils

expect fun getCurrentDate(): String

expect fun getCurrentTime(): String

expect fun exitProcess(code: Int): Nothing

expect fun printlnStderr(s: String)
