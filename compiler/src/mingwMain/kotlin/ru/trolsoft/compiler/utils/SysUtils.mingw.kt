package ru.trolsoft.compiler.utils

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual fun printlnStderr(s: String) {
    val msg = "$s\n"
    fputs(msg, stderr)
}

actual fun exitProcess(code: Int): Nothing {
    exit(code)
    @Suppress("UNREACHABLE_CODE")
    throw RuntimeException("exit failed")
}

@OptIn(ExperimentalForeignApi::class)
actual fun getCurrentDate(): String {
    return memScoped {
        val tmPtr = localtime(null)
        check(tmPtr != null) { "localtime() returned null" }
        val tm = tmPtr.pointed
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val month = months[tm.tm_mon]
        val day = tm.tm_mday
        val year = tm.tm_year + 1900
        val dayStr = if (day < 10) " $day" else "$day"
        "$month$dayStr $year"
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun getCurrentTime(): String {
    return memScoped {
        val tmPtr = localtime(null)
        check(tmPtr != null) { "localtime() returned null" }
        val tm = tmPtr.pointed
        val hour = tm.tm_hour
        val min = tm.tm_min
        val sec = tm.tm_sec
        val h = if (hour < 10) "0$hour" else "$hour"
        val m = if (min < 10) "0$min" else "$min"
        val s = if (sec < 10) "0$sec" else "$sec"
        "$h:$m:$s"
    }
}
