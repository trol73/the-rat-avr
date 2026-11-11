package ru.trolsoft.therat.i8085.decompiler

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.Buffer
import kotlinx.io.writeString
import ru.trolsoft.compiler.utils.parseNumber
import ru.trolsoft.therat.VERSION
import ru.trolsoft.compiler.utils.exitProcess

fun usage() {
    val str = """
        The Rat 8080/8085 Decompiler. Version $VERSION
        
        Usage: 8085-decompiler [options] <source_file> [<output_file>]

        OPTIONS:
           -offset=<value>                      Define ROM offset
           -ep=<val1[,val2,...]>                Define entrypoints
           -ignore=<val1[,val2,...]>            Define ignore points
           -ignore=<from1-to1[,from2-to2,...]>  Define ignore points intervals
           -oz=true|false                       Optimize A=0 ax XRA A
           -oincdec=true|false                  Use inc/drc instead of add 1/sub 1
           -ochains=true|false                  Simplify C-- chains
    """.trimIndent()
    println(str)
}

fun error(msg: String): Nothing {
    println(msg)
    exitProcess(1)
}
fun main(args : Array<String>) {
    var src: String? = null
    var dest: String? = null
    var romOffset: Int? = null
    val entryPoints = mutableListOf<Int>()
    val ignorePoints = mutableListOf<Int>()
    var oz = true
    var ochains = true
    var oincdec = true
    for (i in 0 until args.size) {
        val arg = args[i]
        when {
            arg.startsWith("-offset=", true) -> {
                romOffset = parseNumber(arg.substring(8))
            }

            arg.startsWith("-ep=", true) -> {
                entryPoints.addAll(parseList(arg.substring(4)))
            }

            arg.startsWith("-ignore=", true) -> {
                ignorePoints.addAll(parseListWithRegions(arg.substring(8)))
            }

            arg.startsWith("-oz=", true) -> {
                oz = parseBool(arg.substring(3))
            }
            arg.startsWith("-oincdec=", true) -> {
                oincdec = parseBool(arg.substring(9))
            }
            arg.startsWith("-ochains=", true) -> {
                ochains = parseBool(arg.substring(9))
            }
            src == null -> {
                src = arg
            }
            dest == null -> {
                dest = arg
            }
            else -> {
                error("Unexpected argument: $arg")
            }
        }
    }
    if (src == null) {
        usage()
        return
    }
    val d = I8085Decompiler()
    val options = Options(
        romOffset = romOffset,
        entryPoints = entryPoints,
        ignorePoints = ignorePoints,
        optimizeZero = oz,
        optimizeIncDec = oincdec,
        ratChains = ochains
    )
    val output = d.loadFile(src, options) //romOffset, if (entryPoints.isEmpty()) null else entryPoints, ignorePoints)
    if (dest == null) {
        d.printResult(output)
    } else {
        val buf = Buffer()
        for (s in output) {
            buf.writeString(s)
            buf.writeString("\n")
            //writer..println(s)
        }
        val sink = SystemFileSystem.sink(Path(dest))
        sink.write(buf, buf.size)
        sink.flush()
        sink.close()
        buf.close()
    }
}

fun parseList(s: String) = s.split(",").map { parseNumber(it) ?: error("Invalid number: $it") }.toList()

fun parseListWithRegions(s: String) =
    s.split(",").flatMap {
        if (it.contains('-')) {
            val fromTo = it.split('-')
            val from = parseNumber(fromTo[0]) ?: error("Invalid number: ${fromTo[0]}")
            val to = parseNumber(fromTo[1]) ?: error("Invalid number: ${fromTo[1]}")
            from..to
        } else {
            val v = parseNumber(it) ?: error("Invalid number: $it")
            v..v
        }
    }.toList()

fun parseBool(s: String): Boolean = when (s.lowercase()) {
    "true" -> true
    "false" -> false
    else -> error("Invalid value: $s")
}

