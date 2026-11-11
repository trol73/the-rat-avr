package ru.trolsoft.therat.arch.avr

private val instructions = setOf(
    "fmulsu", "pop", "brpl", "brhs", "sen", "out", "sbi", "lds", "lat", "clt", "mul", "spm",
    "bld", "sec", "adc", "andi", "brcc", "jmp", "sev", "subi", "sez", "com", "ld", "rcall",
    "brge", "neg", "clr", "asr", "add", "muls", "bset", "sbc", "swap", "sts", "eor", "brlt",
    "seh", "sbrc", "sbic", "nop", "rjmp", "sei", "fmul", "brvs", "breq", "sbiw", "in", "cbi",
    "cls", "las", "brbc", "ret", "brts", "brcs", "rol", "call", "push", "dec", "tst", "brlo",
    "wdr", "brmi", "ses", "and", "sub", "inc", "cli", "std", "mulsu", "brid", "brhc", "mov",
    "fmuls", "brie", "cbr", "lsl", "cpc", "clh", "icall", "adiw", "ser", "bst", "eijmp", "movw",
    "xch", "sleep", "sbr", "sbci", "cp", "reti", "clz", "brvc", "lpm", "brtc", "lsr", "clc",
    "lac", "brbs", "brne", "ldd", "st", "clv", "brsh", "break", "set", "cpi", "ijmp", "ror",
    "sbrs", "sbis", "or", "bclr", "eicall", "cpse", "ldi", "cln", "elpm", "ori"
)

private val registers: Set<String> = buildRegistersSet()
private val registersUpper: Set<String> = buildRegistersUpperSet()
private val registersEven: Set<String> = buildRegistersEvenSet()
private val registersPairLow = setOf("r26", "r28", "r30", "xl", "yl", "zl")
private val registersPairHigh = setOf("r27", "r29", "r31", "xh", "yh", "zh")
private val registersPartsXYZ = setOf("xl", "xh", "yl", "yh", "zl", "zh")

//fun isAvrInstruction(s: String): Boolean = instructions.contains(s.lowercase())
fun isAvrInstruction(s: String): Boolean = instructions.contains(s)

fun isAvrRegister(s: String): Boolean = registers.contains(s.lowercase())

fun isAvrUpperRegister(s: String): Boolean = registersUpper.contains(s.lowercase())

fun isAvrEvenRegister(s: String): Boolean = registersEven.contains(s.lowercase())

fun isAvrPairName(s: String): Boolean = s == "X" || s == "Y" || s == "Z"

fun isAvrPairLow(s: String): Boolean = registersPairLow.contains(s.lowercase())
fun isAvrPairHigh(s: String): Boolean = registersPairHigh.contains(s.lowercase())

fun resolvePartOfXYZ(s: String) =
    when (s) {
        "xl" -> "r26"
        "xh" -> "r27"
        "yl" -> "r28"
        "yh" -> "r29"
        "zl" -> "r30"
        "zh" -> "r31"
        else -> s
    }

fun isPartOfXYZ(s: String): Boolean = registersPartsXYZ.contains(s)


private fun buildRegistersSet(): Set<String> {
    return HashSet<String>().apply {
        for (i in 0..31) {
            add("r$i")
        }
        add("xh")
        add("xl")
        add("yh")
        add("yl")
        add("zh")
        add("zl")
    }.toSet()
}

private fun buildRegistersUpperSet(): Set<String> {
    return HashSet<String>().apply {
        for (i in 16..31) {
            add("r$i")
        }
        add("xh")
        add("xl")
        add("yh")
        add("yl")
        add("zh")
        add("zl")
    }.toSet()
}

private fun buildRegistersEvenSet(): Set<String> {
    return HashSet<String>().apply {
        for (i in 0..31 step 2) {
            add("r$i")
        }
        add("xl")
        add("yl")
        add("zl")
    }.toSet()
}