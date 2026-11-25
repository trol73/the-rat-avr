package ru.trolsoft.therat.arch.viper

private val instructions = setOf(
    "mov", "movi", "nop",
    "add", "adc", "sub", "sbc", "and", "or", "xor", "cmp",
    "subi", "ori", "cmpi", "addi", "xori", "sbci", "andi",
    "inc", "dec", "push", "pop",
    "loadi", "storei", "load", "store", "not", "in", "out",
    "ror", "lsr", "ret", "rol", "lsl",
    "ijmp", "icall", "jmp", "call", "rjmpf", "rjmpb", "jpc", "jps",
    "jnz", "jz", "jnc", "jc", "jvs", "jvc", "jp", "jm",
    "callnz", "callz", "callnc", "callc", "callvs", "callvc", "callp", "callm",
    "incx", "decx", "incy", "decy", "incz", "decz",
    "halt", "break", "sec", "clc", "lpc", "lpcp",
    "syscall"
)

private val registers = setOf("R0", "R1", "R2", "R3", "R4", "R5", "R6", "R7")

private val registerPairs = setOf("RX", "RY", "RZ")

fun isViperInstruction(s: String) = instructions.contains(s.lowercase())

fun isViperRegister(s: String) = registers.contains(s.uppercase())

fun isViperPair(s: String) = registerPairs.contains(s.uppercase())

