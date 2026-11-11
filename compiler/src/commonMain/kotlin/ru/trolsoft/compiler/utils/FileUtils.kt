package ru.trolsoft.compiler.utils

import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

fun String.resolvePath(): String =
    try {
        SystemFileSystem.resolve(Path(this)).toString()
    } catch (_: FileNotFoundException) {
        this
    }

expect fun getRootPath(): String

expect fun fileExists(path: String): Boolean

expect val FILE_SEPARATOR: String

