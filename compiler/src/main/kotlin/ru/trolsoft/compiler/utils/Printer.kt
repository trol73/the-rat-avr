package ru.trolsoft.compiler.utils

fun interface Printer {
    fun println(s: String)
    fun println() = println("")
}