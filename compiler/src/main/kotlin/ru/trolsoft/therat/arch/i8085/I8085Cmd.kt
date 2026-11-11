package ru.trolsoft.therat.arch.i8085

import ru.trolsoft.therat.arch.*

enum class ArgType {
    CONST,
    REG,
    PAIR,
    LABEL_ABS,
}

enum class ArgReg : AsmArg {
    A, B, C, D, E, H, L, M;

    companion object {
        fun fromStr(s: String): ArgReg? =
            try {
                valueOf(s.uppercase())
            } catch (_: Exception) {
                null
            }
    }
}

enum class ArgRegPair : AsmArg {
    BC, DE, HL, SP, PSW;

    companion object {
        fun fromStr(s: String): ArgRegPair? =
            try {
                ArgRegPair.valueOf(s.uppercase())
            } catch (_: Exception) {
                null
            }
    }
}
enum class I8085Cmd(val arg1: ArgType? = null, val arg2: ArgType? = null) : AsmCmd {
    CALL(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xCD, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    CC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xDC, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    CNC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xD4, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    CZ(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xCC, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    CNZ(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xC4, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    CP(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xF4, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    CM(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xFC, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    CPE(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xEC, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    CPO(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xE4, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    JMP(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xC3, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    JC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xDA, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    JNC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xD2, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    JZ(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xCA, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    JNZ(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xC2, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    JP(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xF2, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    JM(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xFA, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    JPE(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xEA, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    JPO(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0xE2, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    LDA(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0x3A, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    STA(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0x32, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    LHLD(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0x2A, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },
    SHLD(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileAbsolute(0x22, out, *args)
        override fun size(vararg args: AsmArg): Int = 3
    },

    CMA {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x2F, out, *args)
    },
    CMC {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x3F, out, *args)
    },
    DAA {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x27, out, *args)
    },
    DI {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xF3, out, *args)
    },
    EI {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xFB, out, *args)
    },
    HLT {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x76, out, *args)
    },
    NOP {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x00, out, *args)
    },
    PCHL {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xE9, out, *args)
    },
    RLC {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x07, out, *args)
    },
    RRC {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x0F, out, *args)
    },
    RAL {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x17, out, *args)
    },
    RAR {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x1F, out, *args)
    },
    RET {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xC9, out, *args)
    },
    RC {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xD8, out, *args)
    },
    RNC {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xD0, out, *args)
    },
    RZ {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xC8, out, *args)
    },
    RNZ {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xC0, out, *args)
    },
    RP {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xF0, out, *args)
    },
    RM {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xF8, out, *args)
    },
    RPE {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xE8, out, *args)
    },
    RPO {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xE0, out, *args)
    },
    SPHL {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xF9, out, *args)
    },
    STC {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x37, out, *args)
    },
    XCHG {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xEB, out, *args)
    },
    XTHL {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0xE3, out, *args)
    },
    RST(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val num = checkNumber(args[0], 7) shl 3
            out.addByte(0xC7 + num)
        }
    },
    RIM {   // 8085 only!
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x20, out, *args)
    },
    SIM {   // 8085 only!
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileSimpleCmd(0x30, out, *args)
    },

    MOV(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val dst = registerCode(args[0]) shl 3
            val src = registerCode(args[1])
            out.addByte(0x40 + dst + src)
        }
    },

    ADC(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0x88, out, 0, *args)
    },
    ADD(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0x80, out, 0, *args)
    },
    ANA(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0xA0, out, 0, *args)
    },
    CMP(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0xB8, out, 0, *args)
    },
    ORA(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0xB0, out, 0, *args)
    },
    INR(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0x04, out, 3, *args)
    },
    DCR(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0x05, out, 3, *args)
    },
    SUB(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0x90, out, 0, *args)
    },
    SBB(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0x98, out, 0, *args)
    },
    XRA(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileReg(0xA8, out, 0, *args)
    },

    MVI(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val dst = registerCode(args[0]) shl 3
            val d8 = checkByte(args[1])
            out.addByte(0x06 + dst)
            out.addByte(d8)
        }
        override fun size(vararg args: AsmArg): Int = 2
    },
    LXI(ArgType.PAIR, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val dst = registerPairCode(args[0]) shl 4
            val pairName = (args[0] as ArgRegPair).name
            if (pairName == "PSW") {
                throw AsmCompileException("Unexpected pair: $pairName")
            }
            val d16 = checkWord(args[1])
            out.addByte(0x01 + dst)
            out.addWord(d16)
        }
        override fun size(vararg args: AsmArg): Int = 3
    },
    ACI(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xCE, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },
    ADI(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xC6, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },
    ANI(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xE6, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },
    ORI(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xF6, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },
    XRI(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xEE, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },
    CPI(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xFE, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },
    SBI(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xDE, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },
    SUI(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xD6, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },
    IN(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xDB, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },
    OUT(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compileImmediate(0xD3, out, *args)
        override fun size(vararg args: AsmArg): Int = 2
    },

    DAD(ArgType.PAIR) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compilePair(0x09, out, "BCDEHLSP", *args)
    },
    DCX(ArgType.PAIR) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compilePair(0x0B, out, "BCDEHLSP", *args)
    },
    INX(ArgType.PAIR) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compilePair(0x03, out, "BCDEHLSP", *args)
    },
    POP(ArgType.PAIR) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compilePair(0xC1, out, "BCDEHLPSW", *args)
    },
    PUSH(ArgType.PAIR) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compilePair(0xC5, out, "BCDEHLPSW", *args)
    },
    LDAX(ArgType.PAIR) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compilePair(0x0A, out, "BCDE", *args)
    },
    STAX(ArgType.PAIR) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) = compilePair(0x02, out, "BCDE", *args)
    };


    override fun size(vararg args: AsmArg): Int = 1

    override fun str(vararg args: AsmArg): String {
        val name = this.toString().lowercase()
        if (args.isEmpty()) {
            return name
        } else if (args.size == 1) {
            return "$name\t${args[0]}"
        }
        return "$name\t${args[0]}, ${args[1]}"
    }

    protected fun compileSimpleCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(0, *args)
        out.addByte(code)
    }

    protected fun compileAbsolute(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(1, *args)
        val value = checkWord(args[0])
        out.addByte(code)
        out.addWord(value)
    }

    protected fun compileReg(code: Int, out: CompilerOutput, pos: Int, vararg args: AsmArg) {
        checkArgsSize(1, *args)
        val reg = registerCode(args[0]) shl pos
        out.addByte(code + reg)
    }

    protected fun compilePair(code: Int, out: CompilerOutput, available: String, vararg  args: AsmArg) {
        checkArgsSize(1, *args)
        val pair = registerPairCode(args[0]) shl 4
        val name = (args[0] as ArgRegPair).name
        if (!available.contains(name)) {
            throw AsmCompileException("Unexpected pair: $name")
        }
        out.addByte(code + pair)
    }


    protected fun compileImmediate(code: Int, out: CompilerOutput, vararg  args: AsmArg) {
        checkArgsSize(1, *args)
        val d8 = checkByte(args[0])
        out.addByte(code)
        out.addByte(d8)
    }


    protected fun checkArgsSize(number: Int, vararg args: AsmArg)  {
        if (args.size < number) {
            throw AsmCompileException("Argument expected")
        } else if (args.size > number) {
            throw AsmCompileException("Garbage after instruction: ${args[number]}")
        }
    }

    protected fun checkNumber(arg: AsmArg, max: Int, min: Int = 0): Int {
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

    protected fun checkByte(arg: AsmArg): Int = checkNumber(arg, 0xff)
    protected fun checkWord(arg: AsmArg): Int = checkNumber(arg, 0xffff)

    protected fun registerCode(arg: AsmArg): Int {
        if (arg is ArgReg) {
            return when (arg.name) {
                "B" -> 0b000
                "C" -> 0b001
                "D" -> 0b010
                "E" -> 0b011
                "H" -> 0b100
                "L" -> 0b101
                "M" -> 0b110
                "A" -> 0b111
                else -> throw AsmCompileException("Register expected: ${arg.name}")
            }
        }
        throw AsmCompileException("Register expected: $arg")
    }

    protected fun registerPairCode(arg: AsmArg): Int {
        if (arg is ArgRegPair) {
            return when (arg.name) {
                "BC" -> 0b00
                "DE" -> 0b01
                "HL" -> 0b10
                "SP" -> 0b11
                "PSW" -> 0b11
                else -> throw AsmCompileException("Register pair expected: ${arg.name}")
            }
        }
        throw AsmCompileException("Register pair expected: $arg")
    }


    companion object {
        private val map: Map<String, I8085Cmd> = buildMap()

        private fun buildMap(): Map<String, I8085Cmd> {
            val map = mutableMapOf<String, I8085Cmd>()
            for (v in I8085Cmd.entries) {
                map[v.name.lowercase()] = v
            }
            return map.toMap()
        }

        fun getByName(name: String): I8085Cmd? {
            return map[name.lowercase()]
        }
    }
}
private fun checkRange(v: Int, min: Int, max: Int) {
    if (v !in min..max) {
        throw AsmCompileException("Operand out of range ($min <= s <= $max): $v")
    }
}

