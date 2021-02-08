package ru.trolsoft.compiler.lexer

import ru.trolsoft.therat.lang.Scanner
import java.io.File

private val files = mutableMapOf<String, TokenStream>()

private fun normalizePath(path: String) = File(path).absolutePath

interface FileLoader {
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