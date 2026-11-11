package ru.trolsoft.compiler.utils

import java.time.LocalDate
import java.time.LocalTime
import kotlin.system.exitProcess

actual fun printlnStderr(s: String) {
    System.err.println(s)
}

actual fun exitProcess(code: Int): Nothing {
    exitProcess(code)
}

actual fun getCurrentDate(): String {
    val now = LocalDate.now()
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val month = months[now.monthValue - 1]
    return String.format("%s %02d %04d", month, now.dayOfMonth, now.year)
}

actual fun getCurrentTime(): String {
    val now = LocalTime.now()
    return String.format("%02d:%02d:%02d", now.hour, now.minute, now.second)
}
