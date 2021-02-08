package ru.trolsoft.compiler.generator

import ru.trolsoft.therat.arch.AsmArg
import ru.trolsoft.therat.arch.AsmCmd
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.therat.arch.DeferredNumberArg
import java.io.FileOutputStream
import java.io.OutputStream


abstract class Generator {
    abstract fun addCommand(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg)
    abstract fun addLabel(name: String, location: SourceLocation)
    abstract fun write(os: OutputStream)
    abstract fun org(offset: Int)
    abstract fun addByte(b: Int, location: SourceLocation)
    abstract fun addWord(w: Int, location: SourceLocation)
    abstract fun addDword(dw: Int, location: SourceLocation)
    abstract fun addByte(b: DeferredNumberArg, location: SourceLocation)
    abstract fun addWord(w: DeferredNumberArg, location: SourceLocation)
    abstract fun addDword(dw: DeferredNumberArg, location: SourceLocation)

    open fun addCommand(location: SourceLocation, cmd: AsmCmd, comment: String?, vararg args: AsmArg) = addCommand(location, cmd, *args)
    abstract fun addValidation(location: SourceLocation, startLabel: String, endLabel: String, maxSize: Int)
    abstract fun validate()

    abstract fun resolveLabels()

    fun write(path: String) {
        val os = FileOutputStream(path)
        write(os)
        os.close()
    }

}

data class Label (
        val name: String,
        val offset: Int,
        val location: SourceLocation
)

class GeneratorException(message: String, val location: SourceLocation?, cause: Throwable? = null):
        Exception(message, cause)

