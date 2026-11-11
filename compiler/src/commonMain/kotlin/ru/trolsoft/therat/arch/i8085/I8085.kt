package ru.trolsoft.therat.arch.i8085

private val instructions = setOf(
    "call", "cc", "cm", "cnc", "cnz", "cp", "cpe", "cpo", "cz", "jc", "jm", "jmp", "jnc", "jnz", "jp", "jpe", "jpo",
    "jz", "lda", "lhld", "shld", "sta", "cma", "cmc", "daa", "di", "ei", "hlt", "nop", "pchl", "ral", "rar", "rc",
    "ret", "rim", "rlc", "rm", "rnc", "rnz", "rp", "rpe", "rpo", "rrc", "rz", "sim", "sphl", "stc", "xchg", "xthl",
    "mov", "adc", "add", "ana", "cmp", "dcr", "inr", "ora", "sbb", "sub", "xra", "aci", "adi", "ani", "cpi", "lxi",
    "mvi", "ori", "sbi", "sui", "xri", "in", "out", "dad", "dcx", "inx", "pop", "push", "ldax", "stax", "rst"
)

private val registers = setOf("A", "B", "C", "D", "E", "H", "L", "M")
private val registerPairs = setOf("BC", "DE", "HL", "SP", "PSW")

fun is8085Instruction(s: String) = instructions.contains(s.lowercase())

fun is8085Register(s: String) = registers.contains(s.uppercase())

fun is8085Pair(s: String) = registerPairs.contains(s.uppercase())
