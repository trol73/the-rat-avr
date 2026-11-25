package ru.trolsoft.therat.arch


internal fun checkArgsSize(number: Int, vararg args: AsmArg) {
    if (args.size < number) {
        throw AsmCompileException("Argument expected")
    } else if (args.size > number) {
        throw AsmCompileException("Garbage after instruction: ${args[number]}")
    }
}

internal fun checkNumber(arg: AsmArg, max: Int, min: Int = 0): Int {
    if (arg is ArgLabel) {
        val resolved = arg.resolved ?: throw AsmCompileException("Label unresolved: $arg")
        return checkNumber(ArgNumber(resolved), max, min)
    }
    return when (arg) {
        is ArgNumber -> {
            checkRange(arg.value, min, max)
            arg.value
        }
        is DeferredNumberArg -> {
            val resolved = arg.resolved ?: throw AsmCompileException("Argument unresolved: $arg")
            checkNumber(ArgNumber(resolved), max, min)
        }
        else ->
            throw AsmCompileException("Number expected: $arg")
    }
}

internal fun checkRange(v: Int, min: Int, max: Int) {
    if (v !in min..max) {
        throw AsmCompileException("Operand out of range ($min <= s <= $max): $v")
    }
}
internal fun checkByte(arg: AsmArg): Int = checkNumber(arg, 0xff)
internal  fun checkWord(arg: AsmArg): Int = checkNumber(arg, 0xffff)
