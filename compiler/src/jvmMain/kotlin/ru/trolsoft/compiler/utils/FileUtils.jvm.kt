package ru.trolsoft.compiler.utils

import java.nio.file.FileSystems

val actualFileSeparator: String = FileSystems.getDefault().separator

actual val FILE_SEPARATOR: String get() = actualFileSeparator

actual fun getRootPath(): String {
    val jarFile = ru.trolsoft.therat.avr.AvrRatCompiler::class.java.protectionDomain.codeSource.location.path
    val appDir = java.nio.file.Paths.get(jarFile).parent?.toString() ?: ""
    return "$appDir/"
}

actual fun fileExists(path: String): Boolean {
    return java.io.File(path).exists()
}
