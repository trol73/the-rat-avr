package ru.trolsoft.therat.arch.avr

import ru.trolsoft.therat.arch.*


enum class ArgReg : AsmArg {
    R0, R1, R2, R3, R4, R5, R6, R7, R8, R9,
    R10, R11, R12, R13, R14, R15, R16, R17, R18, R19,
    R20, R21, R22, R23, R24, R25, R26, R27, R28, R29,
    R30, R31;

    companion object {
        fun fromStr(s: String): ArgReg? {
            when (s) {
                "XL" -> return R26
                "XH" -> return R27
                "YL" -> return R28
                "YH" -> return R29
                "ZL" -> return R30
                "ZH" -> return R31
            }
            return try {
                valueOf(s.toUpperCase())
            } catch (e: Exception) {
                null
            }
        }
    }

}

enum class ArgRegPair(
        private val str: String,
        val high: ArgReg,
        val low: ArgReg,
        val prefix: Char? = null,
        val suffix: Char? = null) : AsmArg {

    X("X", ArgReg.R27, ArgReg.R26),
    Y("Y", ArgReg.R29, ArgReg.R28),
    Z("Z", ArgReg.R31, ArgReg.R30),

    X_PLUS("X+", X, suffix = '+'),
    Y_PLUS("Y+", Y, suffix = '+'),
    Z_PLUS("Z+", Z, suffix = '+'),
    X_MINUS("X-", X, suffix = '-'),
    Y_MINUS("Y-", Y, suffix = '-'),
    Z_MINUS("Z-", Z, suffix = '-'),

    MINUS_X("-X", X, prefix = '-'),
    MINUS_Y("-Y", Y, prefix ='-'),
    MINUS_Z("-Z", Z, prefix ='-'),
    PLUS_X("+X", X, prefix ='+'),
    PLUS_Y("+Y", Y, prefix ='+'),
    PLUS_Z("+Z", Z, prefix ='+'),

    R1_R0("R1:R0", ArgReg.R1, ArgReg.R0),
    R3_R2("R3:R2", ArgReg.R3, ArgReg.R2),
    R5_R4("R5:R4", ArgReg.R5, ArgReg.R4),
    R7_R6("R7:R6", ArgReg.R7, ArgReg.R6),
    R9_R8("R9:R8", ArgReg.R9, ArgReg.R8),
    R11_R10("R11:R10", ArgReg.R11, ArgReg.R10),
    R13_R12("R13:R12", ArgReg.R13, ArgReg.R12),
    R15_R14("R15:R14", ArgReg.R15, ArgReg.R14),
    R17_R16("R17:R16", ArgReg.R17, ArgReg.R16),
    R19_R18("R19:R18", ArgReg.R19, ArgReg.R18),
    R21_R20("R21:R20", ArgReg.R21, ArgReg.R20),
    R23_R22("R23:R22", ArgReg.R23, ArgReg.R22),
    R25_R24("R25:R24", ArgReg.R25, ArgReg.R24),
    R27_R26("R27:R26", ArgReg.R27, ArgReg.R26),
    R29_R28("R29:R28", ArgReg.R29, ArgReg.R28),
    R31_R30("R31:R30", ArgReg.R31, ArgReg.R30);

    constructor(str: String, src: ArgRegPair, prefix: Char? = null, suffix: Char? = null) :
            this(str, src.high, src.low, prefix, suffix)

    override fun toString(): String = str

    companion object {
        fun getPairWithHighReg(highNum: Int): ArgRegPair {
            return when (highNum) {
                1 -> R1_R0
                3 -> R3_R2
                5 -> R5_R4
                7 -> R7_R6
                9 -> R9_R8
                11 -> R11_R10
                13 -> R13_R12
                15 -> R15_R14
                17 -> R17_R16
                19 -> R19_R18
                21 -> R21_R20
                23 -> R23_R22
                25 -> R25_R24
                27 -> R27_R26
                29 -> R29_R28
                31 -> R31_R30
                else -> throw AsmCompileException("Wrong pair (high=$highNum)")
            }
        }

    }
}


class ArgOffsetPair(
        val pair: ArgRegPair,
        val offset: Int
) : AsmArg {
    override fun toString(): String = "$pair+$offset"
}

enum class ArgType {
    CONST,
    REG,
    PAIR,
    LABEL_ABS,
    LABEL_REL,
    PORT,
//    MEM,
    ADDR
}


enum class AvrCmd(val arg1: ArgType? = null, val arg2: ArgType? = null) : AsmCmd {
    NOP {  // 0000 0000 0000 0000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x0000, out, *args)
    },
    SEC {  // 1001 0100 0000 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9408, out, *args)
    },
    CLC {  // 1001 0100 1000 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9488, out, *args)
    },
    SEN {  // 1001 0100 0010 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9428, out, *args)
    },
    CLN {  // 1001 0100 1010 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x94a8, out, *args)
    },
    SEZ {  // 1001 0100 0001 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9418, out, *args)
    },
    CLZ {  // 1001 0100 1001 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9498, out, *args)
    },
    SEI {  // 1001 0100 0111 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9478, out, *args)
    },
    CLI {  // 1001 0100 1111 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x94f8, out, *args)
    },
    SES {  // 1001 0100 0100 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9448, out, *args)
    },
    CLS {  // 1001 0100 1100 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x94c8, out, *args)
    },
    SEV {  // 1001 0100 0011 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9438, out, *args)
    },
    CLV {  // 1001 0100 1011 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x94b8, out, *args)
    },
    SET {  // 1001 0100 0110 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9468, out, *args)
    },
    CLT {  // 1001 0100 1110 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x94e8, out, *args)
    },
    SEH {  // 1001 0100 0101 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9458, out, *args)
    },
    CLH {  // 1001 0100 1101 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x94d8, out, *args)
    },
    SLEEP { // 1001 0101 1000 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9588, out, *args)
    },
    WDR {  // 1001 0101 1010 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x95a8, out, *args)
    },
    IJMP {  // 1001 0100 0000 1001
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9409, out, *args)
    },
    EIJMP { // 1001 0100 0001 1001
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9419, out, *args)
    },
    ICALL { // 1001 0101 0000 1001
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9509, out, *args)

    },
    EICALL { // 1001 0101 0001 1001
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9519, out, *args)
    },
    RET {  // 1001 0101 0000 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9508, out, *args)
    },
    RETI {  // 1001 0101 0001 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9518, out, *args)
    },
    SPM {  // 1001 0101 1110 1000
        // TODO в справочнике написано про SPM Z++
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x95e8, out, *args)
    },
    ESPM {  // 1001 0101 1111 1000
        // TODO в справочнике отсутсвует
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x95f8, out, *args)
    },
    BREAK { // 1001 0101 1001 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSimpleCmd(0x9598, out, *args)
    },
    LPM(ArgType.REG, ArgType.PAIR) {
        // 1001 0101 1100 1000      LPM
        // 1001	000d dddd 0100      LPM Rd, Z
        // 1001 000d dddd 0101      LPM Rd, Z+
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            if (args.isEmpty()) {
                out.addWord(0x95c8)
            } else {
                if (args.size != 2) {
                    errorWrongSyntax()
                }
                val regCode = checkAnyReg(args[0]) shl 4
                val code = when (args[1]) {
                    ArgRegPair.Z -> 0x9004 or regCode
                    ArgRegPair.Z_PLUS -> 0x9005 or regCode
                    else -> throw AsmCompileException("Z or Z+ expected as second argument")
                }
                out.addWord(code)
            }
        }
    },
    ELPM(ArgType.REG, ArgType.PAIR) {
        // 1001 0101 1101 1000      ELPM
        // 1001 000d dddd 0110      ELPM Rd, Z
        // 1001 000d dddd 0111      ELPM Rd, Z+
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            if (args.isEmpty()) {
                out.addWord(0x95d8)
            } else {
                if (args.size != 2) {
                    errorWrongSyntax()
                }
                val regCode = checkAnyReg(args[0]) shl 4
                val code = when (args[1]) {
                    ArgRegPair.Z -> 0x9006 or regCode
                    ArgRegPair.Z_PLUS -> 0x9007 or regCode
                    else -> throw AsmCompileException("Z or Z+ expected as second argument")
                }
                out.addWord(code)
            }
        }
    },
    BSET(ArgType.CONST) {  // s 1001 0100 0sss 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            val arg = checkOneNumArg(7, *args) shl 4
            out.addWord(0x9408 or arg)
        }
    },
    BCLR(ArgType.CONST) {  // s 1001 0100 1sss 1000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            val arg = checkOneNumArg(7, *args) shl 4
            out.addWord(0x9488 or arg)
        }
    },
    SER(ArgType.REG) { // Rd       1110 1111 dddd 1111
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            if (args.isEmpty()) {
                throw AsmCompileException("Argument expected")
            }
            if (args.size > 1) {
                throw AsmCompileException("Garbage after instruction: ${args[1]}")
            }
            val regCode = checkHighReg(args[0]) shl 4
            out.addWord(0xef0f or regCode)
        }
    },
    COM(ArgType.REG) { // Rd  1001 010d dddd 0000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x9400, out, *args)
    },
    NEG(ArgType.REG) { // Rd       1001 010d dddd 0001
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x9401, out, *args)
    },
    INC(ArgType.REG) { // Rd       1001 010d dddd 0011
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x9403, out, *args)
    },
    DEC(ArgType.REG) { // Rd       1001 010d dddd 1010
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x940a, out, *args)
    },
    LSR(ArgType.REG) { // Rd       1001 010d dddd 0110
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x9406, out, *args)
    },
    ROR(ArgType.REG) { // Rd       1001 010d dddd 0111
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x9407, out, *args)
    },
    ASR(ArgType.REG) { // Rd       1001 010d dddd 0101
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x9405, out, *args)
    },
    SWAP(ArgType.REG) { // Rd       1001 010d dddd 0010
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x9402, out, *args)
    },
    PUSH(ArgType.REG) { // Rr       1001 001r rrrr 1111
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x920f, out, *args)
    },
    POP(ArgType.REG) { // Rd       1001 000d dddd 1111
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileSingleRegCmd(0x900f, out, *args)
    },
    TST(ArgType.REG) {  // Rd       0010 00dd dddd dddd
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            AND.compile(out, args[0], args[0])
        }

    },
    CLR(ArgType.REG) {  // Rd       0010 01dd dddd dddd
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            EOR.compile(out, args[0], args[0])
        }
    },
    LSL(ArgType.REG) {  // Rd       0000 11dd dddd dddd
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            ADD.compile(out, args[0], args[0])
        }
    },
    ROL(ArgType.REG) {  // Rd       0001 11dd dddd dddd
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            ADC.compile(out, args[0], args[0])
        }
    },

    BREQ(ArgType.LABEL_REL) { // k        1111 00kk kkkk k001
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf001, out, *args)

    },
    BRNE(ArgType.LABEL_REL) { // k        1111 01kk kkkk k001
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf401, out, *args)
    },
    BRCS(ArgType.LABEL_REL) { // k        1111 00kk kkkk k000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf000, out, *args)
    },
    BRCC(ArgType.LABEL_REL) { // k        1111 01kk kkkk k000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf400, out, *args)
    },
    BRSH(ArgType.LABEL_REL) { // k        1111 01kk kkkk k000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf400, out, *args)
    },
    BRLO(ArgType.LABEL_REL) { // k        1111 00kk kkkk k000
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf000, out, *args)
    },
    BRMI(ArgType.LABEL_REL) { // k        1111 00kk kkkk k010
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf002, out, *args)
    },
    BRPL(ArgType.LABEL_REL) { // k        1111 01kk kkkk k010
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf402, out, *args)
    },
    BRGE(ArgType.LABEL_REL) { // k        1111 01kk kkkk k100
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf404, out, *args)
    },
    BRLT(ArgType.LABEL_REL) { // k        1111 00kk kkkk k100
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf004, out, *args)
    },
    BRHS(ArgType.LABEL_REL) { // k        1111 00kk kkkk k101
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf005, out, *args)
    },
    BRHC(ArgType.LABEL_REL) { // k        1111 01kk kkkk k101
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf405, out, *args)
    },
    BRTS(ArgType.LABEL_REL) { // k        1111 00kk kkkk k110
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf006, out, *args)
    },
    BRTC(ArgType.LABEL_REL) { // k        1111 01kk kkkk k110
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf406, out, *args)
    },
    BRVS(ArgType.LABEL_REL) { // k        1111 00kk kkkk k011
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf003, out, *args)
    },
    BRVC(ArgType.LABEL_REL) { // k        1111 01kk kkkk k011
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf403, out, *args)
    },
    BRIE(ArgType.LABEL_REL) { // k        1111 00kk kkkk k111
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf007, out, *args)
    },
    BRID(ArgType.LABEL_REL) { // k        1111 01kk kkkk k111
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compile7bitJump(0xf407, out, *args)
    },
    RJMP(ArgType.LABEL_REL) { // k        1100 kkkk kkkk kkkk
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val valCode = checkNumber(args[0], 2047, -2048) and 0xfff
            out.addWord(0xc000 or valCode)
        }
    },
    JMP(ArgType.LABEL_ABS) {  // k        1001 010k kkkk 110k + 16k
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val value = checkNumber(args[0], (1 shl 22) - 1)
            val hi = value shr 16 and 0xffff
            val rd = (hi shl 3 and 0x1f0) or (hi and 0x1)
            out.addWord(0x940c or rd)
            out.addWord(value and 0xffff)
        }

        override fun size(vararg args: AsmArg): Int = 4
    },
    RCALL(ArgType.LABEL_REL) { // k        1101 kkkk kkkk kkkk
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val valCode = checkNumber(args[0], 2047, -2048) and 0xfff
            out.addWord(0xd000 or valCode)
        }
    },
    CALL(ArgType.LABEL_ABS) { // k        1001 010k kkkk 111k + 16k
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(1, *args)
            val value = checkNumber(args[0], (1 shl 22) - 1)
            val hi = value shr 16 and 0xffff
            val rd = hi shl 3 and 0x1f0 or (hi and 0x1)
            out.addWord(0x940e or rd)
            out.addWord(value and 0xffff)
        }

        override fun size(vararg args: AsmArg): Int = 4
    },
    BRBS(ArgType.CONST, ArgType.LABEL_REL) { // s, k     1111 00kk kkkk ksss
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val s = checkNumber(args[0], 7)
            val k = checkNumber(args[1], 63, -64) and 0x7f
            out.addWord(0xf000 or (k shl 3) or s)
        }
    },
    BRBC(ArgType.CONST, ArgType.LABEL_REL) { // s, k     1111 01kk kkkk ksss
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val s = checkNumber(args[0], 7)
            val k = checkNumber(args[1], 63, -64) and 0x7f
            out.addWord(0xf400 or (k shl 3) or s)
        }
    },
    ADD(ArgType.REG, ArgType.REG) {  // Rd, Rr   0000 11rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x0c00, out, *args)
    },
    ADC(ArgType.REG, ArgType.REG) {  // Rd, Rr   0001 11rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x1c00, out, *args)
    },
    SUB(ArgType.REG, ArgType.REG) {  // Rd, Rr   0001 10rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x1800, out, *args)
    },
    SBC(ArgType.REG, ArgType.REG) {  // Rd, Rr   0000 10rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x0800, out, *args)
    },
    AND(ArgType.REG, ArgType.REG) {  // Rd, Rr   0010 00rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x2000, out, *args)
    },
    OR(ArgType.REG, ArgType.REG) {   // Rd, Rr   0010 10rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x2800, out, *args)
    },
    EOR(ArgType.REG, ArgType.REG) {  // Rd, Rr   0010 01rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x2400, out, *args)
    },
    CP(ArgType.REG, ArgType.REG) {   // Rd, Rr   0001 01rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x1400, out, *args)
    },
    CPC(ArgType.REG, ArgType.REG) {  // Rd, Rr   0000 01rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x0400, out, *args)
    },
    CPSE(ArgType.REG, ArgType.REG) { // Rd, Rr   0001 00rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x1000, out, *args)
    },
    MOV(ArgType.REG, ArgType.REG) {  // Rd, Rr   0010 11rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x2c00, out, *args)
    },
    MUL(ArgType.REG, ArgType.REG) {  // Rd, Rr   1001 11rd dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileTwoRegsCmd(0x9c00, out, *args)
    },
    MOVW(ArgType.REG, ArgType.REG) {  // [rdh:]Rd, [rrl:]Rr   0000 0001 dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val reg1Code = checkAnyRegPair(args[0]) shl 4
            val reg2Code = checkAnyRegPair(args[1])
            out.addWord(0x0100 or reg1Code or reg2Code)
        }
    },
    MULS(ArgType.REG, ArgType.REG) {  // Rd, Rr   0000 0010 dddd rrrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val reg1Code = checkHighReg(args[0]) shl 4
            val reg2Code = checkHighReg(args[1])
            out.addWord(0x0200 or reg1Code or reg2Code)
        }
    },
    MULSU(ArgType.REG, ArgType.REG) {  // Rd, Rr   0000 0011 0ddd 0rrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val reg1Code = checkHighReg7(args[0]) shl 4
            val reg2Code = checkHighReg7(args[1])
            out.addWord(0x0300 or reg1Code or reg2Code)
        }
    },
    FMUL(ArgType.REG, ArgType.REG) {   // Rd, Rr   0000 0011 0ddd 1rrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val reg1Code = checkHighReg7(args[0]) shl 4
            val reg2Code = checkHighReg7(args[1])
            out.addWord(0x0308 or reg1Code or reg2Code)
        }
    },
    FMULS(ArgType.REG, ArgType.REG) {  // Rd, Rr   0000 0011 1ddd 0rrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val reg1Code = checkHighReg7(args[0]) shl 4
            val reg2Code = checkHighReg7(args[1])
            out.addWord(0x0380 or reg1Code or reg2Code)
        }
    },
    FMULSU(ArgType.REG, ArgType.REG) { // Rd, Rr   0000 0011 1ddd 1rrr
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val reg1Code = checkHighReg7(args[0]) shl 4
            val reg2Code = checkHighReg7(args[1])
            out.addWord(0x0388 or reg1Code or reg2Code)
        }
    },

    ADIW(ArgType.REG, ArgType.CONST) {     // Rd, K    1001 0110 KKdd KKKK
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileWordToConstCmd(0x9600, out, *args)
    },
    SBIW(ArgType.REG, ArgType.CONST) {     // Rd, K    1001 0111 KKdd KKKK
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileWordToConstCmd(0x9700, out, *args)
    },
    SUBI(ArgType.REG, ArgType.CONST) {     // Rd, K    0101 KKKK dddd KKKK
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegImmCmd(0x5000, out, *args)
    },
    SBCI(ArgType.REG, ArgType.CONST) {     // Rd, K    0100 KKKK dddd KKKK
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegImmCmd(0x4000, out, *args)
    },
    ANDI(ArgType.REG, ArgType.CONST) {     // Rd, K    0111 KKKK dddd KKKK
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegImmCmd(0x7000, out, *args)
    },
    ORI(ArgType.REG, ArgType.CONST) {      // Rd, K    0110 KKKK dddd KKKK
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegImmCmd(0x6000, out, *args)
    },
    SBR(ArgType.REG, ArgType.CONST) {      // Rd, K    0110 KKKK dddd KKKK
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegImmCmd(0x6000, out, *args)
    },
    CPI(ArgType.REG, ArgType.CONST) {      // Rd, K    0011 KKKK dddd KKKK
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegImmCmd(0x3000, out, *args)
    },
    LDI {      // Rd, K    1110 KKKK dddd KKKK
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegImmCmd(0xe000, out, *args)
    },
    CBR(ArgType.REG, ArgType.CONST) {      // Rd, K    0111 KKKK dddd KKKK  (~K)
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val i = checkNumber(args[1], 0xff)
            ANDI.compile(out, args[0], ArgNumber(i.inv() and 0xff))
        }
    },
    SBRC(ArgType.REG, ArgType.CONST) {     // Rr, b    1111 110r rrrr 0bbb
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegBitCmd(0xfc00, out, *args)
    },
    SBRS(ArgType.REG, ArgType.CONST) {     // Rr, b    1111 111r rrrr 0bbb
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegBitCmd(0xfe00, out, *args)
    },
    BST(ArgType.REG, ArgType.CONST) {      // Rd, b    1111 101d dddd 0bbb
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegBitCmd(0xfa00, out, *args)
    },
    BLD(ArgType.REG, ArgType.CONST) {      // Rd, b    1111 100d dddd 0bbb
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileRegBitCmd(0xf800, out, *args)
    },
    IN(ArgType.REG, ArgType.PORT) {       // Rd, P    1011 0PPd dddd PPPP
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val regCode = checkAnyReg(args[0]) shl 4
            val port = checkNumber(args[1], 63)
            val portCode = port and 0x30 shl 5 or (port and 0x0f)
            out.addWord(0xb000 or regCode or portCode)
        }
    },
    OUT(ArgType.PORT, ArgType.REG) {      // P, Rr    1011 1PPr rrrr PPPP
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val port = checkNumber(args[0], 63)
            val portCode = port and 0x30 shl 5 or (port and 0x0f)
            val regCode = checkAnyReg(args[1]) shl 4
            out.addWord(0xb800 or regCode or portCode)
        }
    },
    SBIC(ArgType.PORT, ArgType.CONST) {     // P, b     1001 1001 PPPP Pbbb
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileIoBitCmd(0x9900, out, *args)
    },
    SBIS(ArgType.PORT, ArgType.CONST) {     // P, b     1001 1011 PPPP Pbbb
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileIoBitCmd(0x9b00, out, *args)
    },
    SBI(ArgType.PORT, ArgType.CONST) {      // P, b     1001 1010 PPPP Pbbb
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileIoBitCmd(0x9a00, out, *args)
    },
    CBI(ArgType.PORT, ArgType.CONST) {      // P, b     1001 1000 PPPP Pbbb
        override fun compile(out: CompilerOutput, vararg args: AsmArg) =
                compileIoBitCmd(0x9800, out, *args)
    },
    LDS(ArgType.REG, ArgType.ADDR) {      // Rd, k    1001 000d dddd 0000 + 16k
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            // TODO для ATTiny10 формат команды другой
            checkArgsSize(2, *args)
            val regCode = checkAnyReg(args[0]) shl 4
            val address = checkNumber(args[1], 0xffff)
            out.addWord(0x9000 or regCode)
            out.addWord(address)
        }

        override fun size(vararg args: AsmArg): Int = 4
    },
    STS(ArgType.ADDR, ArgType.REG) {      // k, Rr    1001 001d dddd 0000 + 16k
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            // TODO для ATTiny10 формат команды другой
            checkArgsSize(2, *args)
            val addr = checkNumber(args[0], 0xffff)
            val regCode = checkAnyReg(args[1]) shl 4
            out.addWord(0x9200 or regCode)
            out.addWord(addr)
        }

        override fun size(vararg args: AsmArg): Int = 4
    },
    LD(ArgType.REG, ArgType.PAIR) {
        // Rd, X    1001 000d dddd 1100
        // Rd, X+   1001 000d dddd 1101
        // Rd, -X   1001 000d dddd 1110
        // Rd, Y    1000 000d dddd 1000
        // Rd, Y+   1001 000d dddd 1001
        // Rd, -Y   1001 000d dddd 1010
        // Rd, Z    1000 000d dddd 0000
        // Rd, Z+   1001 000d dddd 0001
        // Rd, -Z   1001 000d dddd 0010
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val regCode = checkAnyReg(args[0]) shl 4
            val code = when (args[1]) {
                ArgRegPair.X -> 0x900c
                ArgRegPair.X_PLUS -> 0x900d
                ArgRegPair.MINUS_X -> 0x900e
                ArgRegPair.Y -> 0x8008
                ArgRegPair.Y_PLUS -> 0x9009
                ArgRegPair.MINUS_Y -> 0x900a
                ArgRegPair.Z -> 0x8000
                ArgRegPair.Z_PLUS -> 0x9001
                ArgRegPair.MINUS_Z -> 0x9002
                else -> errorWrongSyntax()
            }
            out.addWord(code or regCode)
        }
    },
    ST(ArgType.PAIR, ArgType.REG) {
        // X, Rr    1001 001d dddd 1100
        // X+, Rr   1001 001d dddd 1101
        // -X, Rr   1001 001d dddd 1110
        // Y, Rr    1000 001d dddd 1000
        // Y+, Rr   1001 001d dddd 1001
        // -Y, Rr   1001 001d dddd 1010
        // Z, Rr    1000 001d dddd 0000
        // Z+, Rr   1001 001d dddd 0001
        // -Z, Rr   1001 001d dddd 0010
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val code = when (args[0]) {
                ArgRegPair.X -> 0x920c
                ArgRegPair.X_PLUS -> 0x920d
                ArgRegPair.MINUS_X -> 0x920e
                ArgRegPair.Y -> 0x8208
                ArgRegPair.Y_PLUS -> 0x9209
                ArgRegPair.MINUS_Y -> 0x920a
                ArgRegPair.Z -> 0x8200
                ArgRegPair.Z_PLUS -> 0x9201
                ArgRegPair.MINUS_Z -> 0x9202
                else -> errorWrongSyntax()
            }
            val regCode = checkAnyReg(args[1]) shl 4
            out.addWord(code or regCode)
        }
    },
    LDD(ArgType.REG, ArgType.PAIR) {
        // Rd, Y+q  10q0 qq0d dddd 1qqq
        // Rd, Z+q  10q0 qq0d dddd 0qqq
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val regCode = checkAnyReg(args[0]) shl 4
            val pair = args[1] as? ArgOffsetPair ?: errorWrongSyntax()
            val offset = pair.offset
            checkRange(offset, 0, 63)
            val code = when (pair.pair) {
                ArgRegPair.Y -> 0x8008
                ArgRegPair.Z -> 0x8000
                else -> errorWrongSyntax()
            }
            val offsetCode = (offset and 0x20 shl 8) or (offset and 0x18 shl 7) or (offset and 0x7)
            out.addWord(code or regCode or offsetCode)
        }
    },
    STD(ArgType.PAIR, ArgType.REG) {
        // Y+q, Rr  10q0 qq1r rrrr 1qqq
        // Z+q, Rr  10q0 qq1r rrrr 0qqq
        override fun compile(out: CompilerOutput, vararg args: AsmArg) {
            checkArgsSize(2, *args)
            val pair = args[0] as? ArgOffsetPair ?: errorWrongSyntax()
            val regCode = checkAnyReg(args[1]) shl 4
            val offset = pair.offset
            checkRange(offset, 0, 63)
            val code = when (pair.pair) {
                ArgRegPair.Y -> 0x8208
                ArgRegPair.Z -> 0x8200
                else -> errorWrongSyntax()
            }
            val offsetCode = (offset and 0x20 shl 8) or (offset and 0x18 shl 7) or (offset and 0x7)
            out.addWord(code or regCode or offsetCode)
        }
    };

    override fun size(vararg args: AsmArg): Int {
        return 2
    }

    override fun str(vararg args: AsmArg): String {
        assert(args.size <= 2)
        val name = this.toString().toLowerCase()
        if (args.isEmpty()) {
            return name
        } else if (args.size == 1) {
            return "$name\t${args[0]}"
        }
        return "$name\t${args[0]}, ${args[1]}"
    }


    protected fun checkAnyReg(arg: AsmArg): Int {
        if (arg !is ArgReg) {
            throw AsmCompileException("R0..R31 expected but $arg found")
        }
        return arg.ordinal
    }

    protected fun checkAnyRegPair(arg: AsmArg): Int {
        if (arg !is ArgRegPair) {
            throw AsmCompileException("Register pair expected, but $arg found")
        }
        return arg.low.ordinal / 2
    }

    protected fun checkHighReg(arg: AsmArg): Int {
        if (arg !is ArgReg || arg.ordinal < 16) {
            throw AsmCompileException("r16..r31 expected but $arg found")
        }
        return arg.ordinal - 16
    }

    protected fun checkHighReg7(arg: AsmArg): Int {
        if (arg !is ArgReg || arg.ordinal < 16 || arg.ordinal > 23) {
            throw AsmCompileException("r16..r31 expected but $arg found")
        }
        return arg.ordinal - 16
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
                return checkNumber(ArgNumber(resolved), max, min)
            }
            else ->
                throw AsmCompileException("Number expected: $arg")
        }
    }

    protected fun checkArgsSize(number: Int, vararg args: AsmArg)  {
        if (args.size < number) {
            throw AsmCompileException("Argument expected")
        } else if (args.size > number) {
            throw AsmCompileException("Garbage after instruction: ${args[number]}")
        }
    }

    protected fun checkOneNumArg(max: Int, vararg args: AsmArg): Int {
        checkArgsSize(1, *args)
        return checkNumber(args[0], max)
    }

    protected fun compileSimpleCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(0, *args)
        out.addWord(code)
    }

    protected fun compileSingleRegCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(1, *args)
        val regCode = checkAnyReg(args[0]) shl 4
        out.addWord(code or regCode)
    }

    protected fun compileTwoRegsCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(2, *args)
        // Rd, Rr       .... ..rd dddd rrrr
        val regCode1 = checkAnyReg(args[0]) shl 4
        val reg2 = checkAnyReg(args[1])
        val regCode2 = ((reg2 and 0x10) shl 5) or (reg2 and 0x0f)
        out.addWord(code or regCode1 or regCode2)
    }

    protected fun compile7bitJump(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        checkArgsSize(1, *args)
        val valCode = checkNumber(args[0], 63, -64) and 0x7f shl 3
        out.addWord(code or valCode)
    }

    protected fun compileWordToConstCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        // Rd, K    .... .... KKdd KKKK
        checkArgsSize(2, *args)
        val pair = args[0] as? ArgRegPair ?: throw AsmCompileException("Register pair expected but ${args[0]} found")
        if (pair.low.ordinal < ArgReg.R24.ordinal) {
            throw AsmCompileException("Register pair must be R25:R24, R27:R26, R29:R28, R31:R30")
        }
        val pairCode = pair.low.ordinal - 24 shr 1 shl 4
        val value = checkNumber(args[1], 63)
        val valCode = (value and 0x30 shl 2) or (value and 0xf)
        out.addWord(code or pairCode or valCode)
    }

    protected fun compileRegImmCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        // Rd, K    .... KKKK dddd KKKK
        checkArgsSize(2, *args)
        val regCode = checkHighReg(args[0]) shl 4
        val i = checkNumber(args[1], 0xff, -128) and 0xff
        val valCode = (i and 0xf0 shl 4) or (i and 0xf)
        out.addWord(code or regCode or valCode)
    }

    protected fun compileRegBitCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        // Rr, b    .... ...r rrrr 0bbb
        checkArgsSize(2, *args)
        val regCode = checkAnyReg(args[0]) shl 4
        val bit = checkNumber(args[1], 7)
        out.addWord(code or regCode or bit)
    }

    protected fun compileIoBitCmd(code: Int, out: CompilerOutput, vararg args: AsmArg) {
        // P, b     .... .... PPPP Pbbb
        checkArgsSize(2, *args)
        val portCode = checkNumber(args[0], 31) shl 3
        val bitCode = checkNumber(args[1], 7)
        out.addWord(code or portCode or bitCode)
    }

    companion object {
        private val map: Map<String, AvrCmd> = buildMap()

        private fun buildMap(): Map<String, AvrCmd> {
            val map = mutableMapOf<String, AvrCmd>()
            for (v in values()) {
                map[v.name.toLowerCase()] = v
            }
            return map.toMap()
        }

        fun getByName(name: String): AvrCmd? {
            return map[name.toLowerCase()]
        }
    }

}

private fun checkRange(v: Int, min: Int, max: Int) {
    if (v < min || v > max) {
        throw AsmCompileException("Operand out of range ($min <= s <= $max): $v")
    }
}

private fun errorWrongSyntax(): Nothing = throw AsmCompileException("Wrong syntax")
