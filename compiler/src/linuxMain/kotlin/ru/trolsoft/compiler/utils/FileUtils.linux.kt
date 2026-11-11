package ru.trolsoft.compiler.utils

import kotlinx.cinterop.*
import platform.posix.*

actual val FILE_SEPARATOR: String get() = "/"

@OptIn(ExperimentalForeignApi::class)
actual fun getRootPath(): String {
    return memScoped {
        val buf = allocArray<ByteVar>(PATH_MAX)
        val len = readlink("/proc/self/exe", buf, PATH_MAX.convert())
        check(len >= 0) { "readlink(/proc/self/exe) failed" }
        val exe = buf.toKString()
        exe.substringBeforeLast('/', "") + "/"
    }
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
