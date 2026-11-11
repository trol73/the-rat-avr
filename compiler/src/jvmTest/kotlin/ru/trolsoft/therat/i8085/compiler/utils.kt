package ru.trolsoft.therat.i8085.compiler

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.readByteArray
import kotlinx.io.readString
import ru.trolsoft.compiler.generator.Endian
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.compiler.utils.RkrCrcCalculator
import ru.trolsoft.therat.i8085.I8085RatCompiler
import ru.trolsoft.therat.i8085.I8085Scanner

private class TestFileLoaderStub(private val text: String) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = I8085Scanner()
        val buf = Buffer().apply { write(text.toByteArray()) }
        return TokenStream(scanner.scan(path, buf).toMutableList(), path)
    }
}
fun compileCode(code: String): List<String> {
    val loader = TestFileLoaderStub(code)
    val console = ConsoleReporter({ }, { })
    val compiler = I8085RatCompiler(true, "", console, loader)
    compiler.addAsmGenerator()

    compiler.compile("file.i8085")
    val out = Buffer()
    compiler.saveAsm(out)

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
    val compiler = I8085RatCompiler(true, "", console, loader)
    compiler.addIntelHexGenerator(Endian.LITTLE)
    compiler.compile("file.i8085")
    Buffer().use {
        compiler.saveHex(it)
        return it.readString().removeSuffix("\n:00000001FF\n")
    }
}

fun compileProc(procBody: String) = compileCode("proc main() {\n$procBody\n}")
fun compileHexProc(procBody: String) = compileHex("proc main() {\n$procBody\n}")

fun compileRkr(code: String, startOffset: Int, os: RawSink) {
    val loader = TestFileLoaderStub(code)
    val console = ConsoleReporter({ }, { })
    val compiler = I8085RatCompiler(true, "", console, loader)
    compiler.addBinaryGenerator(Endian.LITTLE)
    compiler.compile("file.i8085")
    val dataBuf = Buffer()
    compiler.saveBinary(dataBuf)

    val data = dataBuf.readByteArray()
    dataBuf.close()

    val crc = RkrCrcCalculator().calculate(data)

    val out = Buffer().apply {
        writeByte(0xE6.toByte())
        writeByte((startOffset shr 8).toByte())
        writeByte((startOffset and 0xff).toByte())
        val endOffset = startOffset + data.size
        writeByte((endOffset shr 8).toByte())
        writeByte((endOffset and 0xff).toByte())
        write(data)
        println(data.size)
        writeByte(0x00.toByte())
        writeByte(0xE6.toByte())
        writeByte((crc shr 8).toByte())
        writeByte((crc and 0xff).toByte())
    }
    os.write(out, out.size)
    out.close()
}