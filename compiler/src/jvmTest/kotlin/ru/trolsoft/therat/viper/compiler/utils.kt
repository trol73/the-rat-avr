package ru.trolsoft.therat.viper.compiler

import kotlinx.io.Buffer
import kotlinx.io.readString
import ru.trolsoft.compiler.generator.Endian
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.therat.viper.ViperRatCompiler
import ru.trolsoft.therat.viper.ViperScanner

private class TestFileLoaderStub(private val text: String) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = ViperScanner()
        val buf = Buffer().apply { write(text.toByteArray()) }
        return TokenStream(scanner.scan(path, buf).toMutableList(), path)
    }
}

fun compileCode(code: String): List<String> {
    val loader = TestFileLoaderStub(code)
    val console = ConsoleReporter({ }, { })
    val out = Buffer()
    ViperRatCompiler("", console, loader).apply {
        addAsmGenerator()
        compile("file.viper")
        saveAsm(out)
    }

    val lines = out.readString().split("\n")
    val res = mutableListOf<String>()
    for (s in lines) {
        if (res.isEmpty() && s == "main:") {
            continue
        }
        if (s.isEmpty()) {
            continue
        }
        var cleaned = s.replace('\t', ' ').trim()
        while (cleaned.contains("  ")) {
            cleaned = cleaned.replace("  ", " ")
        }
        res.add(cleaned)
    }

    out.close()
    return res
}

fun compileHex(code: String): String {
    val loader = TestFileLoaderStub(code)
    val console = ConsoleReporter({ }, { })
    val compiler = ViperRatCompiler("", console, loader).apply {
        addIntelHexGenerator(Endian.BIG)
        compile("file.viper")
    }
    Buffer().use {
        compiler.saveHex(it)
        val withCrs = it.readString().removeSuffix("\n:00000001FF\n").substring(9)
        return withCrs.substring(0, withCrs.length-2)
    }
}

fun compileProc(procBody: String) = compileCode("proc main() {\n$procBody\n}")
fun compileHexProc(procBody: String) = compileHex("proc main() {\n$procBody\n}")
