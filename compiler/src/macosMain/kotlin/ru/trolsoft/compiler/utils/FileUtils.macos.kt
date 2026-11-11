package ru.trolsoft.compiler.utils

import kotlinx.cinterop.*
import platform.Foundation.NSProcessInfo
import platform.posix.*

actual val FILE_SEPARATOR: String get() = "/"

@OptIn(ExperimentalForeignApi::class)
actual fun getRootPath(): String {
    val processInfo = NSProcessInfo.processInfo
    val arguments = processInfo.arguments
    if (arguments.isNotEmpty()) {
        val firstArg = arguments[0] as? String
        if (firstArg != null) {
            val path = firstArg.substringBeforeLast('/')
            return "$path/"
        }
    }
    return "./"
}

@OptIn(ExperimentalForeignApi::class)
actual fun fileExists(path: String): Boolean {
    val fd = open(path, O_RDONLY, 0)
    if (fd >= 0) {
        close(fd)
        return true
    }
    return false
}
