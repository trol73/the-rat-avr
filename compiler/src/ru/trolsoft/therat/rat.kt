package ru.trolsoft.therat

import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.generator.Endian
import ru.trolsoft.compiler.generator.GeneratorException
import ru.trolsoft.compiler.lexer.LexerException
import ru.trolsoft.compiler.lexer.SourceLocation
import ru.trolsoft.compiler.parser.ParserException
import ru.trolsoft.therat.arch.AsmCompileException
import ru.trolsoft.therat.avr.AvrRatCompiler
import ru.trolsoft.therat.preprocessor.PreprocessorException
import java.io.File
import kotlin.system.exitProcess

const val VERSION = "0.1.0"

fun printUsage() {
    val str = """
        AVR Rat compiler. Version $VERSION

        Usage: ratc [options] <source_file> [<output_file>]

        OPTIONS:
           -D<macro>=<value>      Define <macro> to <value> (or 1 if <value> omitted)
           -I<dir>                Add directory to include search path
           -dev=<path>            Set path to dev-files
           -gcc                   Produce GCC Assembler file
           -help                  Show this usage screen
    """.trimIndent()
    print(str)
}


fun compile(compiler: AvrRatCompiler, src: String) {
    try {
        compiler.compile(src)
        return
    } catch (e: CompilerException) {
        error(e.message, e.location)
        print("\n")
        e.printStackTrace()
    } catch (e: ParserException) {
        error(e.message, e.location)
        print("\n")
        e.printStackTrace()
    } catch (e: GeneratorException) {
        error(e.message, e.location)
        print("\n")
        e.printStackTrace()
    } catch (e: LexerException) {
        error(e.message!!, e.location)
    } catch (e: PreprocessorException) {
        error(e.message!!, e.location)
    } catch (e: java.io.FileNotFoundException) {
        fatal("File not found: $src")
    } catch (e: AsmCompileException) {
        if (e.message != null) {
            fatal(e.message)
        }
        print("\n")
        e.printStackTrace()
    }
    fatal("Compilation error")
}

fun getRootPath(): String {
    val jarFile = AvrRatCompiler::class.java.protectionDomain.codeSource.location.path
    val appDir = File(jarFile).parent
    return "$appDir/"
}

fun main(args : Array<String>) {
    val includePaths = mutableListOf<String>()
    val defines = mutableListOf<String>()
    val files = mutableListOf<String>()
    var gcc = false
    var showUsage = args.isEmpty()

    var devPath = getRootPath() + "devices/avr" //"/Users/trol/Projects/kotlin/rat/devices/avr"

    for (arg in args) {
        when {
            arg == "-gcc" -> gcc = true
            arg == "-help" -> showUsage = true
            arg.startsWith("-D") -> defines.add(arg.substring(2))
            arg.startsWith("-I") -> includePaths.add(arg.substring(2))
            arg.startsWith("-dev=") -> devPath = arg.substring(5)
            else -> files.add(arg)
        }
    }
    if (showUsage) {
        printUsage()
        if (args.isEmpty()) {
            return
        }
    }

    val srcFiles = mutableListOf<String>()
    val outFiles = mutableListOf<String>()
    when {
        files.isEmpty() -> fatal("No source files")
        files.size == 1 -> {
            val src = files.first()
            srcFiles.add(src)
            val ext = if (gcc) ".S" else ".hex"
            val out = src.substringBeforeLast('.', "") + ext
            outFiles.add(out)
        }
        files.size % 2 != 0 -> fatal("Wrong files list")
        else -> {
            for (i in 0 until files.size step 2) {
                val src = files[i]
                val out = files[i+1]
                srcFiles.add(src)
                outFiles.add(out)
            }
        }
    }
//    print("includePaths: $includePaths\n")
//    print("defines: $defines\n")
//    print("files: $files\n")
//    print("srcFiles: $srcFiles\n")
//    print("outFiles: $outFiles\n")


    for (i in 0 until srcFiles.size) {
        val src = srcFiles[i]
        val out = outFiles[i]

        val compiler = AvrRatCompiler(devPath)
        for (def in defines) {
            compiler.define(def)
        }
        //compiler.addAsmGenerator()
        if (gcc) {
            compiler.addGccAsmGenerator()
        } else {
            compiler.addIntelHexGenerator(Endian.LITTLE)
        }
        compile(compiler, src)
        var anyError = false
        try {
            if (gcc) {
                compiler.saveGccAsm(out)
            } else {
                compiler.saveHex(out)
                compiler.saveMap("$out.map")
            }
        } catch (e: AsmCompileException) {
            if (e.message != null) {
                fatal(e.message)
            } else {
                fatal("Compilation error")
            }
        } catch (e: CompilerException) {
            error(e.message, e.location)
            anyError = true
        }
        if (anyError) {
            fatal("Compilation error!")
        }

    }
}

fun fatal(msg: String) {
    print("$msg\n")
    exitProcess(100)
}

fun error(msg: String?, location: SourceLocation? = null) {
    if (location != null) {
        print("ERROR: ${location.str()}: $msg\n")
    } else {
        print("ERROR: $msg\n")
    }

}

/*
    //val src = "/Users/trol/Projects/kotlin/rat/samples/avr/asm-simple/asm"
    //val src = "/Users/trol/Projects/kotlin/rat/samples/avr/interrupts/interrupts"
    //val src = "/Users/trol/Projects/kotlin/rat/samples/avr/helloworld/helloworld"
    //val src = "/Users/trol/Projects/kotlin/rat/samples/avr/microbootloader/bootloader"
    //val src = "/Users/trol/Projects/kotlin/rat/samples/avr/f-counter/f-counter"
    val src = "/Users/trol/Projects/kotlin/rat/samples/avr/lcd-128x128/main"

//

    compiler.addAsmGenerator()
    compiler.addIntelHexGenerator(Endian.LITTLE)

    val asmStream = ByteArrayOutputStream()
    val hexStream = ByteArrayOutputStream()


    print("$asmStream\n$hexStream\n")

    val hexFile = FileOutputStream("$src.hex")
    hexFile.write(hexStream.toByteArray())
    hexFile.close()
    val asmFile = FileOutputStream("$src.asm")
    asmFile.write(asmStream.toByteArray())
    asmFile.close()
    compiler.saveMap("$src.map")

//    val scanner = AvrScanner()
//    val fileLoader = CachedFileLoader(scanner)
//    val cpp = Preprocessor(fileLoader)
//    val r = cpp.process("/Users/trol/Projects/java/avr-asm-plus/test-data/cpp/main.app")
//    print("${r.str()}\n")

 */