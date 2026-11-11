package ru.trolsoft.compiler.utils

import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.GetModuleFileNameW
import platform.windows.MAX_PATH

actual val FILE_SEPARATOR: String get() = "\\"

@OptIn(ExperimentalForeignApi::class)
actual fun getRootPath(): String {
    return memScoped {
        val buf = allocArray<UShortVar>(MAX_PATH)
        val len = GetModuleFileNameW(null, buf, MAX_PATH.toUInt())
        
        if (len > 0U) {
            val exePath = buf.toKString()
            val normalized = exePath.replace('/', '\\')
            val path = normalized.substringBeforeLast('\\', "")
            return "$path\\"
        }
        ".\\"
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
