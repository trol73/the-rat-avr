package ru.trolsoft.compiler.lexer

import ru.trolsoft.therat.lang.Scanner
import kotlinx.io.files.Path

private val files = mutableMapOf<String, TokenStream>()

private fun normalizePath(path: String) = Path(path).toString()

fun interface FileLoader {
    fun loadSource(path: String): TokenStream
}

class CachedFileLoader(private val scanner: Scanner) : FileLoader {

    override fun loadSource(path: String): TokenStream {
        val filePath = normalizePath(path)

        val result = files[filePath]
        return if (result != null) {
            TokenStream(result)
        } else {
            val list = TokenStream(scanner.scan(filePath).toMutableList(), filePath)
            files[filePath] = list
            list.copyOf()
        }
    }
}

fun clearFilesCache() {
    files.clear()
}