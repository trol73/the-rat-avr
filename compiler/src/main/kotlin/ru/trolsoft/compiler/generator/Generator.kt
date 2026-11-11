package ru.trolsoft.compiler.generator

import kotlinx.io.RawSink
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import ru.trolsoft.therat.arch.AsmArg
import ru.trolsoft.therat.arch.AsmCmd
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.therat.arch.DeferredNumberArg


abstract class Generator {
    abstract fun addCommand(location: SourceLocation, cmd: AsmCmd, vararg args: AsmArg)
    abstract fun addLabel(name: String, location: SourceLocation)
    abstract fun write(sink: RawSink)
    abstract fun org(offset: Int)
    abstract fun addByte(b: Int, location: SourceLocation)
    abstract fun addWord(w: Int, location: SourceLocation)
    abstract fun addDword(dw: Int, location: SourceLocation)
    abstract fun addByte(b: DeferredNumberArg, location: SourceLocation)
    abstract fun addWord(w: DeferredNumberArg, location: SourceLocation)
    abstract fun addDword(dw: DeferredNumberArg, location: SourceLocation)

    open fun addCommand(location: SourceLocation, cmd: AsmCmd, comment: String?, vararg args: AsmArg) =
        addCommand(location, cmd, *args)

    abstract fun addValidation(location: SourceLocation, startLabel: String, endLabel: String, maxSize: Int)
    abstract fun validate()

    abstract fun resolveLabels()

    fun write(path: String) {
        SystemFileSystem.sink(Path(path)).use {
            write(it)
        }
    }

    open fun done() {}

    open fun startProc(name: String, location: SourceLocation) {}
    open fun endProc(name: String, location: SourceLocation) {}

}

data class Label(
    val name: String,
    val offset: Int,
    val location: SourceLocation
)

class GeneratorException(message: String, val location: SourceLocation?, cause: Throwable? = null) :
    Exception(message, cause)
