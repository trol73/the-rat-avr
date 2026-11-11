package ru.trolsoft.therat

import kotlinx.io.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import ru.trolsoft.compiler.compiler.CompilerException
import ru.trolsoft.compiler.generator.Endian
import ru.trolsoft.compiler.generator.GeneratorException
import ru.trolsoft.compiler.generator.ListingRec
import ru.trolsoft.compiler.lexer.LexerException
import ru.trolsoft.compiler.lexer.clearFilesCache
import ru.trolsoft.compiler.parser.ParserException
import ru.trolsoft.compiler.utils.ConsoleReporter
import ru.trolsoft.compiler.utils.Printer
import ru.trolsoft.compiler.utils.RkrOutputStream
import ru.trolsoft.compiler.utils.resolvePath
import ru.trolsoft.compiler.utils.getRootPath
import ru.trolsoft.compiler.utils.FILE_SEPARATOR
import ru.trolsoft.compiler.utils.printlnStderr
import ru.trolsoft.compiler.utils.exitProcess
import ru.trolsoft.therat.arch.AsmCompileException
import ru.trolsoft.therat.avr.AvrRatCompiler
import ru.trolsoft.therat.i8085.I8085RatCompiler
import ru.trolsoft.therat.preprocessor.PreprocessorException


const val VERSION = "0.3"

fun printUsage() {
    val str = """
        The Rat compiler. Version $VERSION

        Usage: ratc [options] <source_file> [<output_file>]

        OPTIONS:
           -D<macro>=<value>      Define <macro> to <value> (or 1 if <value> omitted)
           -I<dir>                Add directory to include search path
           -dev=<path>            Set path to dev-files (for AVR only)
           -enc=<path>            Set path to encoding-files
           -arch=avr|8080|8085    Set CPU architecture 
           -gcc                   Produce GCC Assembler file (for AVR only)
           -list                  Produce assembler listing
           -help                  Show this usage screen
    """.trimIndent()
    println(str)
}

val systemPrinter = Printer { s -> println(s) }
val systemErrPrinter = Printer { s -> printlnStderr(s) }

val systemConsole = ConsoleReporter(systemPrinter, systemErrPrinter, exitProcessOnFatal = true)

fun compile(
    compiler: ru.trolsoft.compiler.compiler.Compiler,
    src: String,
    console: ConsoleReporter
): Boolean {
    try {
        compiler.compile(src)
        return true
    } catch (e: CompilerException) {
        console.error(e.message!!, e.location)
        console.stderr.println()
    } catch (e: ParserException) {
        console.error(e.message!!, e.location)
        console.stderr.println()
    } catch (e: GeneratorException) {
        console.error(e.message!!, e.location)
        console.stderr.println()
    } catch (e: LexerException) {
        console.error(e.message!!, e.location)
    } catch (e: PreprocessorException) {
        console.error(e.message!!, e.location)
    } catch (e: IOException) {
        console.fatal(e.message ?: "File not found")
    } catch (e: AsmCompileException) {
        console.fatal(e.message!!)
        console.stderr.println()
        e.printStackTrace()
    } catch (e: Throwable) {
        console.fatal(e.message!!)
        console.stderr.println()
        e.printStackTrace()
    }
    console.fatal("Compilation error")
    return false
}

enum class Arch {
    AVR, I8080, I8085,
    WRONG
}
data class CmdOptions(
    val includePaths: MutableList<String> = mutableListOf(),
    val defines: MutableList<String> = mutableListOf(),
    val files: MutableList<String> = mutableListOf(),

    var devPath: String = "${getRootPath()}devices${FILE_SEPARATOR}avr",
    var encodingPath: String = "${getRootPath()}encodings",

    var gcc: Boolean = false,
    var codeList: Boolean = false,
    var showUsage: Boolean,
    var arch: Arch? = null
)

fun main(args : Array<String>) {
    val options = CmdOptions(
        showUsage = args.isEmpty()
    )

    val rootPath = getRootPath()
    loadConfig("$rootPath.ratc", options)

    for (arg in args) {
        processCmdArg(arg, options)
    }
    if (options.arch == Arch.WRONG) {
        fatal(systemPrinter, "Wrong architecture")
    }
    if (options.files.isNotEmpty()) {
        val firstFilePath = Path(options.files.first())
        firstFilePath.parent?.let { srcRoot ->
            loadConfig("$srcRoot/.ratc", options)
        }
    }
    if (options.showUsage) {
        printUsage()
        if (args.isEmpty()) {
            return
        }
    }

    if (options.codeList) {
        val list = mutableListOf<ListingRec>()
        val codeListHandler: (ListingRec) -> Unit = { rec: ListingRec -> list.add(rec) }
        if (compileFiles(options.arch, options.files, options.gcc, options.devPath, options.encodingPath,
                options.defines, options.includePaths, systemConsole, codeListHandler)) {
            val listingFileName = getListingFilePath(options.gcc, options.files)
            writeCodeListFile(listingFileName, list)
        }
    } else {
        compileFiles(
            options.arch, options.files, options.gcc,
            options.devPath, options.encodingPath, options.defines, options.includePaths,
            systemConsole
        )
    }
}

private fun loadConfig(path: String, options: CmdOptions) {
    val cfg = loadPropertiesFile(path)
    if (cfg != null) {
        for (arg in cfg) {
            processCmdArg(arg, options)
        }
    }
}

private fun processCmdArg(arg: String, options: CmdOptions) {
    when {
        arg == "-gcc" -> options.gcc = true
        arg == "-help" -> options.showUsage = true
        arg == "-list" -> options.codeList = true
        arg.startsWith("-D") -> options.defines.add(arg.substring(2))
        arg.startsWith("-I") -> options.includePaths.add(arg.substring(2))
        arg.startsWith("-dev=") -> options.devPath = arg.substring(5)
        arg.startsWith("-enc=") -> options.encodingPath = arg.substring(5)
        arg.startsWith("-arch=") -> options.arch = parseArch(arg.substring(6))
        else -> options.files.add(arg)
    }
}

private fun parseArch(s: String?): Arch? {
    if (s == null) {
        return null
    }
    for (a in Arch.entries) {
        if (s.equals(a.name, true)) {
            return a
        }
    }
    return Arch.WRONG
}

private fun getListingFilePath(gcc: Boolean, files: List<String>): String {
    return if (!gcc) {
        "${files[0]}.lss"
    } else {
        val firstOut = files[1]
        val buildSep = "${FILE_SEPARATOR}build${FILE_SEPARATOR}"
        if (firstOut.contains(buildSep)) {
            firstOut.substringBeforeLast(buildSep, firstOut) + buildSep + "avr-rat.lss"
        } else {
            firstOut.substringBeforeLast(FILE_SEPARATOR, firstOut) + FILE_SEPARATOR + "avr-rat.lss"
        }
    }
}

private fun writeCodeListFile(listingFilePath: String, list: List<ListingRec>) {
    SystemFileSystem.sink(Path(listingFilePath)).use {
        val buffered = it.buffered()
        for (rec in list) {
            buffered.writeString("$rec\n")
        }
        buffered.close()
    }
}

@Suppress("unused")
fun compileProject(
    arch: Arch?,
    src: String,
    devPath: String,
    encodingsPath: String,
    defines: List<String>,
    includePaths: List<String>,
    console: ConsoleReporter,
    codeListHandler: ((ListingRec) -> Unit)? = null
) {
    clearFilesCache()
    val files = listOf(src)
    compileFiles(
        arch, files, false, devPath, encodingsPath,
        defines, includePaths, console, codeListHandler)
}

fun compileFiles(
    arch: Arch?,
    files: List<String>,
    gcc: Boolean,
    devPath: String,
    encodingsPath: String,
    defines: List<String>,
    includePaths: List<String>,
    console: ConsoleReporter,
    codeListHandler: ((ListingRec) -> Unit)? = null
): Boolean {
    clearFilesCache()
    val srcFiles = mutableListOf<String>()
    val outFiles = mutableListOf<String>()
    var outputFileDefined = true
    when {
        files.isEmpty() -> {
            console.fatal("No source files")
            return false
        }
        files.size == 1 -> {
            val src = files.first().resolvePath()
            srcFiles.add(src)
            val detectedArch = arch ?: detectArchFromSourceFilenames(srcFiles)
            val ext = when (detectedArch) {
                Arch.AVR -> if (gcc) ".S" else ".hex"
                Arch.I8080, Arch.I8085 -> ".rkr"
                else -> ".bin"
            }
            val out = src.substringBeforeLast('.', "") + ext
            outputFileDefined = false
            outFiles.add(out)
        }
        files.size % 2 != 0 -> {
            console.fatal("Wrong files list")
            return false
        }
        else -> {
            for (i in files.indices step 2) {
                val src = files[i].resolvePath()
                val out = files[i + 1].resolvePath()
                srcFiles.add(src)
                outFiles.add(out)
            }
        }
    }

    val detectedArch = arch ?: detectArchFromSourceFilenames(srcFiles)
    if (detectedArch == null) {
        console.fatal("Unknown architecture, use '-arch' parameter")
        return false
    } else if (detectedArch == Arch.WRONG) {
        console.fatal("Invalid architecture")
        return false
    }

    for (i in srcFiles.indices) {
        val src = srcFiles[i].resolvePath()
        val out = outFiles[i].resolvePath()

        try {
            val res = when (detectedArch) {
                Arch.AVR -> compileAvr(src, out, outputFileDefined, gcc, devPath, encodingsPath, defines, includePaths, console, codeListHandler)
                Arch.I8080, Arch.I8085 -> compileIntel808x(src, out, outputFileDefined, arch == Arch.I8085, encodingsPath, defines, includePaths, console, codeListHandler)
            }
            if (res == false) {
                return false
            }
        } catch (e: NotImplementedError) {
            console.error(e.message!!)
            console.fatal("Compilation error! Unimplemented operation")
            return false
        } catch (e: AsmCompileException) {
            e.printStackTrace()
            console.fatal(e.message ?: "Compilation error")
            return false
        } catch (e: CompilerException) {
            console.error(e.message!!, e.location)
            console.fatal("Compilation error!")
            return false
        } catch (e: ParserException) {
            console.error(e.message!!, e.location)
            console.fatal("Syntax error!")
            return false
        } catch (e: GeneratorException) {
            console.error(e.message!!, e.location)
            console.fatal("Compilation error!")
            return false
        }
    }
    return true
}

private fun compileAvr(
    src: String,
    out: String,
    outDefined: Boolean,
    gcc: Boolean,
    devPath: String,
    encodingsPath: String,
    defines: List<String>,
    includePaths: List<String>,
    console: ConsoleReporter,
    codeListHandler: ((ListingRec) -> Unit)? = null
): Boolean {
    val compiler = AvrRatCompiler(devPath, encodingsPath, console)
    compiler.addIncludePaths(includePaths)
    for (def in defines) {
        compiler.define(def)
    }
    if (gcc) {
        compiler.addGccAsmGenerator()
    } else {
        compiler.addIntelHexGenerator(Endian.LITTLE)
    }
    compiler.addAsmListingGenerator(codeListHandler)
    if (!compile(compiler, src, console)) {
        return false
    }
    val outDirectiveName = compiler.getOutputName()
    val outPath = if (outDirectiveName != null && !outDefined) {
        Path(src).parent?.toString() + FILE_SEPARATOR + outDirectiveName
    } else {
        out
    }
    if (gcc) {
        compiler.saveGccAsm(outPath)
    } else {
        compiler.saveHex(outPath)
        val mapBasePath = if (outPath.lowercase().endsWith(".hex")) outPath.substring(0, outPath.length-4) else outPath
        compiler.saveMap("$mapBasePath.map")
        val freeSize = compiler.getFreeFlashSize()
        if (freeSize >= 0) {
            console.stdout.println("Program size: ${compiler.getOutputSize()} (free: $freeSize)")
        } else {
            console.stdout.println("Program size: ${compiler.getOutputSize()}")
        }
        console.stdout.println("RAM size: ${compiler.getRamSize()}")
    }
    return true
}

fun compileIntel808x(
    src: String,
    out: String,
    outDefined: Boolean,
    is8085: Boolean,
    encodingPath: String,
    defines: List<String>,
    includePaths: List<String>,
    console: ConsoleReporter,
    codeListHandler: ((ListingRec) -> Unit)?
) {
    val compiler = I8085RatCompiler(is8085, encodingPath, console)
    compiler.addIncludePaths(includePaths)
    for (def in defines) {
        compiler.define(def)
    }
    compiler.addBinaryGenerator(Endian.LITTLE, 0, 0)
    compiler.addAsmListingGenerator(codeListHandler)
    compiler.compile(src)
    val outDirectiveName = compiler.getOutputName()
    val outPath = if (outDirectiveName != null && !outDefined) {
        Path(src).parent?.toString() + FILE_SEPARATOR + outDirectiveName
    } else {
        out
    }
    val ext = outPath.substringAfterLast('.').lowercase()
    when (ext) {
        "rkr", "gam" -> {
            val buf = Buffer()
            compiler.saveBinary(buf)
            val rkr = RkrOutputStream(buf)
            rkr.writeTo(SystemFileSystem.sink(Path(outPath)), compiler.getStartOffset())
        }
        "hex" -> {
            compiler.saveHex(outPath)
        }
        else -> {
            compiler.saveBinary(outPath)
        }
    }
}


private fun detectArchFromSourceFilenames(files: List<String>): Arch? {
    var res: Arch? = null
    for (f in files) {
        val fileArch = when (f.substringAfterLast('.').lowercase()) {
            "art" -> Arch.AVR
            "8080" -> Arch.I8080
            "8085" -> Arch.I8085
            else -> null
        }
        if (res == null) {
            res = fileArch
        } else if (res != fileArch && fileArch != null) {
            return null
        }
    }
    return res
}

fun fatal(stderr: Printer, msg: String) {
    stderr.println(msg)
    if (stderr == systemErrPrinter) {
        exitProcess(100)
    }
}

private fun loadPropertiesFile(path: String): List<String>? {
    val filePath = Path(path)
    if (!SystemFileSystem.exists(filePath)) {
        return null
    }
    val source = SystemFileSystem.source(filePath)
    val buffered = source.buffered()
    val content = buffered.readString()
    buffered.close()
    return content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
}
