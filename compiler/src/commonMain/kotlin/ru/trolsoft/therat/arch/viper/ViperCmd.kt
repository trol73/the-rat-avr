package ru.trolsoft.therat.arch.viper

import ru.trolsoft.therat.arch.*

enum class ArgType {
    CONST,
    REG,
    PAIR,
    LABEL_ABS,
}

enum class ArgReg : AsmArg {
    R0, R1, R2, R3, R4, R5, R6, R7;

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
    RX, RY, RZ;

    companion object {
        fun fromStr(s: String): ArgRegPair? =
            try {
                ArgRegPair.valueOf(s.uppercase())
            } catch (_: Exception) {
                null
            }
    }
}

enum class ViperCmd(
    val arg1: ArgType? = null,
    val arg2: ArgType? = null
) : AsmCmd {
    NOP {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0x00, out, *args)
    },
    MOV(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val rd = checkReg(args[0])
            val rs = checkReg(args[1])
            if (rd == rs) {
                errorWrongRegistersCombination()
            }
            out.addByte((rd shl 3) or rs)
        }
    },
    ADD(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileLowRegOrR0WithR0Cmd(0x40, out, *args)
    },
    ADC(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileLowRegOrR0WithR0Cmd(0x44,out, *args)
    },
    SUB(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileLowRegWithR0Cmd(0x48, out, *args)
    },
    SBC(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileLowRegOrR0WithR0Cmd(0x4C, out, *args)
    },
    AND(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileLowRegOrR0WithR0Cmd(0x50, out, *args)
    },
    OR(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileLowRegWithR0Cmd(0x54, out, *args)
    },
    XOR(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileLowRegOrR0WithR0Cmd(0x58, out, *args)
    },
    CMP(ArgType.REG, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileLowRegWithR0Cmd(0x5C, out, *args)
    },
    ADDI(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileImmCmd(0xC1, out, *args)

        override fun size(vararg args: AsmArg): Int = 2
    },
    SUBI(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileImmCmd(0x48, out, *args)

        override fun size(vararg args: AsmArg): Int = 2
    },
    SBCI(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileImmCmd(0xC7, out, *args)

        override fun size(vararg args: AsmArg): Int = 2
    },
    ANDI(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileImmCmd(0xD1, out, *args)

        override fun size(vararg args: AsmArg): Int = 2
    },
    ORI(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileImmCmd(0x54, out, *args)

        override fun size(vararg args: AsmArg): Int = 2
    },
    CMPI(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileImmCmd(0x5C, out, *args)

        override fun size(vararg args: AsmArg): Int = 2
    },
    XORI(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileImmCmd(0xC4, out, *args)

        override fun size(vararg args: AsmArg): Int = 2
    },
    INC(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val arg = args[0]
            if (arg is ArgRegPair) {
                out.addByte(0xE8 or arg.ordinal*2)
            } else {
                val rd = checkReg(arg)
                out.addByte(0x60 or rd)
            }
        }
    },
    DEC(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val arg = args[0]
            if (arg is ArgRegPair) {
                out.addByte(0xE9 or arg.ordinal*2)
            } else {
                val rd = checkReg(arg)
                out.addByte(0x68 or rd)
            }
        }
    },
    PUSH(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val rs = checkReg(args[0])
            out.addByte(0x70 or rs)
        }
    },
    POP(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val rd = checkReg(args[0])
            out.addByte(0x78 or rd)
        }
    },
    LOAD(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val rd = checkReg(args[0])
            out.addByte(0x98 or rd)
        }
    },
    STORE(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val rs = checkReg(args[0])
            out.addByte(0xA0 or rs)
        }
    },
    NOT(ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val rd = checkReg(args[0])
            out.addByte(0xA8 or rd)
        }
    },
    MOVI(ArgType.REG, ArgType.CONST) {
        // Формат: 10 000 RRR + v8, opcode = 0x80 | Rd
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val rd = checkReg(args[0])
            val value = checkByte(args[1])
            out.addByte(0x80 or rd)
            out.addByte(value)
        }

        override fun size(vararg args: AsmArg): Int = 2
    },
    LOADI(ArgType.REG, ArgType.CONST) {
        // Формат: 10 001 RRR + v8, opcode = 0x88 | Rd
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val rd = checkReg(args[0])
            val value = checkByte(args[1])
            out.addByte(0x88 or rd)
            out.addByte(value)
        }

        override fun size(vararg args: AsmArg): Int = 2
    },
    STOREI(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val rs = checkReg(args[0])
            val value = checkByte(args[1])
            out.addByte(0x90 or rs)
            out.addByte(value)
        }

        override fun size(vararg args: AsmArg): Int = 2
    },
    RET {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0xC6, out, *args)
    },
    HALT {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0xEE, out, *args)
    },
    BREAK {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0xEF, out, *args)
    },
    SEC {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0xF0, out, *args)
    },
    CLC {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0xF1, out, *args)
    },
    LPC {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0xF2, out, *args)
    },
    LPCP {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0xF3, out, *args)
    },
    IJMP {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0xC8, out, *args)
    },
    ICALL {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileSimpleCmd(0xC9, out, *args)
    },
    ROL {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkOnlyR0(*args)
            out.addByte(0x44)
        }
    },
    ROR {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkOnlyR0(*args)
            out.addByte(0xC2)
        }
    },
    LSL {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkOnlyR0(*args)
            out.addByte(0x40)
        }
    },
    LSR {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkOnlyR0(*args)
            out.addByte(0xC5)
        }
    },
    CALL(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xCB, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    CALLNZ(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xE0, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    CALLZ(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xE1, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    CALLNC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xE2, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    CALLC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xE3, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    CALLVS(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xE4, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    CALLVC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xE5, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    CALLP(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xE6, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    CALLM(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xE7, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    JMP(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xCA, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    JZ(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xD9, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    JNZ(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xD8, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    JC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xDB, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    JNC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xDA, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    JVC(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xDD, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    JVS(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xDC, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    JM(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xDF, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    JP(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileJmpCallCmd(0xDE, out, *args)

        override fun size(vararg args: AsmArg): Int = 3
    },
    RJMPB(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileReducedJmp(0xCD, out, *args)

        override fun size(vararg args: AsmArg): Int = 2
    },
    RJMPF(ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compileReducedJmp(0xCC, out, *args)

        override fun size(vararg args: AsmArg): Int = 2
    },
    JPC(ArgType.CONST, ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compilePinJmp(0xCE, out, *args)

        override fun size(vararg args: AsmArg): Int = 4
    },
    JPS(ArgType.CONST, ArgType.LABEL_ABS) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
            compilePinJmp(0xCF, out, *args)

        override fun size(vararg args: AsmArg): Int = 4
    },
    SYSCALL(ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val value = checkByte(args[0])
            out.addByte(0xD0)
            out.addByte(value)
        }

        override fun size(vararg args: AsmArg): Int = 2
    },
    IN(ArgType.REG, ArgType.CONST) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val reg = checkReg(args[0])
            val value = checkByte(args[1])
            out.addByte(0xB0 or reg)
            out.addByte(value)
        }

        override fun size(vararg args: AsmArg): Int = 2
    },
    OUT(ArgType.CONST, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val value = checkByte(args[0])
            val reg = checkReg(args[1])
            out.addByte(0xB8 or reg)
            out.addByte(value)
        }

        override fun size(vararg args: AsmArg): Int = 2
    },
    OUTI(ArgType.CONST, ArgType.REG) {
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val port = checkByte(args[0])
            val value = checkByte(args[1])
            out.addByte(0xC0)
            out.addByte(port)
            out.addByte(value)
        }

        override fun size(vararg args: AsmArg): Int = 3
    };

    override fun size(vararg args: AsmArg): Int = 1

    override fun str(vararg args: AsmArg): String {
        val name = this.toString().lowercase()
        return if (args.isEmpty()) {
            name
        } else if (args.size == 1) {
            "$name\t${args[0]}"
        } else {
            "$name\t${args[0]}, ${args[1]}"
        }
    }

    protected fun compileSimpleCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(0, *args)
        out.addByte(code)
    }

    protected fun compileLowRegOrR0WithR0Cmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(2, *args)
        val rd = checkFirstR0toR3(args[0])
        checkSecondR0(args[1])
        out.addByte(code or rd)
    }

    protected fun compileLowRegWithR0Cmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(2, *args)
        val rd = checkFirstR1toR3(args[0])
        checkSecondR0(args[1])
        out.addByte(code or rd)
    }

    protected fun compileJmpCallCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(1, *args)
        val addr = checkWord(args[0])
        out.addByte(code)
        out.addWord(addr)
    }

    protected fun compileReducedJmp(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(1, *args)
        val addr = checkByte(args[0])
        out.addByte(code)
        out.addByte(addr)
    }

    protected fun compilePinJmp(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(2, *args)
        val pin = checkNumber(args[0], 40, 1)
        val addr = checkWord(args[1])
        out.addByte(code)
        out.addByte(pin)
        out.addWord(addr)
    }




    protected fun checkReg(arg: AsmArg): Int {
        if (arg !is ArgReg) {
            throw AsmCompileException("R0..R7 expected but $arg found")
        }
        return arg.ordinal
    }

    protected fun checkFirstR1toR3(arg: AsmArg): Int {
        if (arg !is ArgReg || arg.ordinal !in 1..3) {
            throw AsmCompileException("R1..R3 expected as first argument")
        }
        return arg.ordinal
    }

    protected fun checkFirstR0toR3(arg: AsmArg): Int {
        if (arg !is ArgReg || arg.ordinal !in 0..3) {
            throw AsmCompileException("R0..R3 expected as first argument")
        }
        return arg.ordinal
    }

    protected fun checkOnlyR0(vararg args: AsmArg) {
        if (args.size != 1 || args.first() != ArgReg.R0) {
            throw AsmCompileException("R0 expected as argument")
        }
    }

    protected fun checkFirstR0(arg: AsmArg) {
        if (arg !is ArgReg || arg.ordinal != 0) {
            throw AsmCompileException("R0 expected as first argument")
        }
    }

    protected fun checkSecondR0(arg: AsmArg) {
        if (arg !is ArgReg || arg.ordinal != 0) {
            throw AsmCompileException("R0 expected as second argument")
        }
    }

    protected fun compileImmCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(2, *args)
        checkFirstR0(args[0])
        val value = checkByte(args[1])
        out.addByte(code)
        out.addByte(value)
    }

    protected fun checkByte(arg: AsmArg): Int {
        val value = when (arg) {
            is ArgNumber -> arg.value
            is DeferredNumberArg -> {
                arg.resolved ?: throw AsmCompileException("Argument unresolved: $arg")
            }
            is ArgLabel -> {
                arg.resolved ?: throw AsmCompileException("Label unresolved: $arg")
            }
            else -> throw AsmCompileException("Number expected")
        }
        if (value !in 0..255) {
            throw AsmCompileException("Byte value expected (0..255): $value")
        }
        return value
    }


    companion object {
        private val map: Map<String, ViperCmd> = buildMap()

        private fun buildMap(): Map<String, ViperCmd> {
            val map = mutableMapOf<String, ViperCmd>()
            for (v in ViperCmd.entries) {
                map[v.name.lowercase()] = v
            }
            return map.toMap()
        }

        fun getByName(name: String): ViperCmd? {
            return map[name.lowercase()]
        }
    }
}

private fun errorWrongRegistersCombination(): Nothing =
    throw AsmCompileException("Wrong registers combination")

