package ru.trolsoft.therat.arch.i8085

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.internalError
import ru.trolsoft.compiler.generator.BinaryOutput
import ru.trolsoft.compiler.generator.Endian
import ru.trolsoft.therat.arch.ArgNumber
import ru.trolsoft.therat.arch.AsmArg
import ru.trolsoft.therat.arch.AsmCompileException
import ru.trolsoft.therat.arch.i8085.I8085Cmd.*
import ru.trolsoft.therat.arch.i8085.ArgReg.*
import ru.trolsoft.therat.arch.i8085.ArgRegPair.*

class AssemblerTest {

    @Test fun `simple commands`() {
        compile(NOP) shouldBe 0x00
        compile(CMA) shouldBe 0x2F
        compile(CMC) shouldBe 0x3F
        compile(DAA) shouldBe 0x27
        compile(EI) shouldBe 0xFB
        compile(DI) shouldBe 0xF3
        compile(HLT) shouldBe 0x76
        compile(PCHL) shouldBe 0xE9
        compile(RAL) shouldBe 0x17
        compile(RAR) shouldBe 0x1F
        compile(RLC) shouldBe 0x07
        compile(RRC) shouldBe 0x0F
        compile(RET) shouldBe 0xC9
        compile(RZ) shouldBe 0xC8
        compile(RNZ) shouldBe 0xC0
        compile(RC) shouldBe 0xD8
        compile(RNC) shouldBe 0xD0
        compile(RP) shouldBe 0xF0
        compile(RM) shouldBe 0xF8
        compile(RPE) shouldBe 0xE8
        compile(RPO) shouldBe 0xE0
        compile(SPHL) shouldBe 0xF9
        compile(STC) shouldBe 0x37
        compile(XCHG) shouldBe 0xEB
        compile(XTHL) shouldBe 0xE3
        compile(SIM) shouldBe 0x30
        compile(RIM) shouldBe 0x20
    }

    @Test fun `single reg argument`() {
        compile(ADD, A) shouldBe 0x87
        compile(ADD, B) shouldBe 0x80
        compile(ADD, C) shouldBe 0x81
        compile(ADD, D) shouldBe 0x82
        compile(ADD, E) shouldBe 0x83
        compile(ADD, H) shouldBe 0x84
        compile(ADD, L) shouldBe 0x85
        compile(ADD, M) shouldBe 0x86

        compile(ADC, A) shouldBe 0x8F
        compile(ADC, B) shouldBe 0x88
        compile(ADC, C) shouldBe 0x89
        compile(ADC, D) shouldBe 0x8A
        compile(ADC, E) shouldBe 0x8B
        compile(ADC, H) shouldBe 0x8C
        compile(ADC, L) shouldBe 0x8D
        compile(ADC, M) shouldBe 0x8E

        compile(ANA, A) shouldBe 0xA7
        compile(ANA, B) shouldBe 0xA0
        compile(ANA, C) shouldBe 0xA1
        compile(ANA, D) shouldBe 0xA2
        compile(ANA, E) shouldBe 0xA3
        compile(ANA, H) shouldBe 0xA4
        compile(ANA, L) shouldBe 0xA5
        compile(ANA, M) shouldBe 0xA6

        compile(CMP, A) shouldBe 0xBF
        compile(CMP, B) shouldBe 0xB8
        compile(CMP, C) shouldBe 0xB9
        compile(CMP, D) shouldBe 0xBA
        compile(CMP, E) shouldBe 0xBB
        compile(CMP, H) shouldBe 0xBC
        compile(CMP, L) shouldBe 0xBD
        compile(CMP, M) shouldBe 0xBE

        compile(ORA, A) shouldBe 0xB7
        compile(ORA, B) shouldBe 0xB0
        compile(ORA, C) shouldBe 0xB1
        compile(ORA, D) shouldBe 0xB2
        compile(ORA, E) shouldBe 0xB3
        compile(ORA, H) shouldBe 0xB4
        compile(ORA, L) shouldBe 0xB5
        compile(ORA, M) shouldBe 0xB6

        compile(SUB, A) shouldBe 0x97
        compile(SUB, B) shouldBe 0x90
        compile(SUB, C) shouldBe 0x91
        compile(SUB, D) shouldBe 0x92
        compile(SUB, E) shouldBe 0x93
        compile(SUB, H) shouldBe 0x94
        compile(SUB, L) shouldBe 0x95
        compile(SUB, M) shouldBe 0x96

        compile(SBB, A) shouldBe 0x9F
        compile(SBB, B) shouldBe 0x98
        compile(SBB, C) shouldBe 0x99
        compile(SBB, D) shouldBe 0x9A
        compile(SBB, E) shouldBe 0x9B
        compile(SBB, H) shouldBe 0x9C
        compile(SBB, L) shouldBe 0x9D
        compile(SBB, M) shouldBe 0x9E

        compile(XRA, A) shouldBe 0xAF
        compile(XRA, B) shouldBe 0xA8
        compile(XRA, C) shouldBe 0xA9
        compile(XRA, D) shouldBe 0xAA
        compile(XRA, E) shouldBe 0xAB
        compile(XRA, H) shouldBe 0xAC
        compile(XRA, L) shouldBe 0xAD
        compile(XRA, M) shouldBe 0xAE

        compile(INR, A) shouldBe 0x3C
        compile(INR, B) shouldBe 0x04
        compile(INR, C) shouldBe 0x0C
        compile(INR, D) shouldBe 0x14
        compile(INR, E) shouldBe 0x1C
        compile(INR, H) shouldBe 0x24
        compile(INR, L) shouldBe 0x2C
        compile(INR, M) shouldBe 0x34

        compile(DCR, A) shouldBe 0x3D
        compile(DCR, B) shouldBe 0x05
        compile(DCR, C) shouldBe 0x0D
        compile(DCR, D) shouldBe 0x15
        compile(DCR, E) shouldBe 0x1D
        compile(DCR, H) shouldBe 0x25
        compile(DCR, L) shouldBe 0x2D
        compile(DCR, M) shouldBe 0x35
    }

    @Test fun `commands with single num argument`() {
        compile(ADI, 0x12.toArg()) shouldBe 0xC612
        compile(ADI, 0xEF.toArg()) shouldBe 0xC6EF

        compile(ACI, 0x32.toArg()) shouldBe 0xCE32
        compile(ACI, 0xFE.toArg()) shouldBe 0xCEFE

        compile(ANI, 0x52.toArg()) shouldBe 0xE652
        compile(ANI, 0xBE.toArg()) shouldBe 0xE6BE

        compile(CPI, 0x12.toArg()) shouldBe 0xFE12
        compile(CPI, 0xBF.toArg()) shouldBe 0xFEBF

        compile(ORI, 0x52.toArg()) shouldBe 0xF652
        compile(ORI, 0xF3.toArg()) shouldBe 0xF6F3

        compile(SUI, 0xE2.toArg()) shouldBe 0xD6E2
        compile(SUI, 0xDD.toArg()) shouldBe 0xD6DD

        compile(SBI, 0x00.toArg()) shouldBe 0xDE00
        compile(SBI, 0xFF.toArg()) shouldBe 0xDEFF

        compile(XRI, 0x45.toArg()) shouldBe 0xEE45
        compile(XRI, 0x20.toArg()) shouldBe 0xEE20

        compile(RST, 0.toArg()) shouldBe 0xC7
        compile(RST, 1.toArg()) shouldBe 0xCF
        compile(RST, 2.toArg()) shouldBe 0xD7
        compile(RST, 3.toArg()) shouldBe 0xDF
        compile(RST, 4.toArg()) shouldBe 0xE7
        compile(RST, 5.toArg()) shouldBe 0xEF
        compile(RST, 6.toArg()) shouldBe 0xF7
        compile(RST, 7.toArg()) shouldBe 0xFF

        compile(RST, 7.toArg()) shouldBe 0xFF

        compile(IN, 0x12.toArg()) shouldBe 0xDB12
        compile(OUT, 0x34.toArg()) shouldBe 0xD334

        compile(LHLD, 0x1234.toArg()) shouldBe 0x2A3412
        compile(SHLD, 0x1234.toArg()) shouldBe 0x223412

        compile(STA, 0x1234.toArg()) shouldBe 0x323412
        compile(LDA, 0x1234.toArg()) shouldBe 0x3A3412
    }


    @Test fun `mov command`() {
        compile(MOV, A, A) shouldBe 0x7F
        compile(MOV, A, B) shouldBe 0x78
        compile(MOV, A, C) shouldBe 0x79
        compile(MOV, A, D) shouldBe 0x7A
        compile(MOV, A, E) shouldBe 0x7B
        compile(MOV, A, H) shouldBe 0x7C
        compile(MOV, A, L) shouldBe 0x7D
        compile(MOV, A, M) shouldBe 0x7E

        compile(MOV, B, A) shouldBe 0x47
        compile(MOV, B, B) shouldBe 0x40
        compile(MOV, B, C) shouldBe 0x41
        compile(MOV, B, D) shouldBe 0x42
        compile(MOV, B, E) shouldBe 0x43
        compile(MOV, B, H) shouldBe 0x44
        compile(MOV, B, L) shouldBe 0x45
        compile(MOV, B, M) shouldBe 0x46

        compile(MOV, C, A) shouldBe 0x4F
        compile(MOV, C, B) shouldBe 0x48
        compile(MOV, C, C) shouldBe 0x49
        compile(MOV, C, D) shouldBe 0x4A
        compile(MOV, C, E) shouldBe 0x4B
        compile(MOV, C, H) shouldBe 0x4C
        compile(MOV, C, L) shouldBe 0x4D
        compile(MOV, C, M) shouldBe 0x4E

        compile(MOV, D, A) shouldBe 0x57
        compile(MOV, D, B) shouldBe 0x50
        compile(MOV, D, C) shouldBe 0x51
        compile(MOV, D, D) shouldBe 0x52
        compile(MOV, D, E) shouldBe 0x53
        compile(MOV, D, H) shouldBe 0x54
        compile(MOV, D, L) shouldBe 0x55
        compile(MOV, D, M) shouldBe 0x56

        compile(MOV, E, A) shouldBe 0x5F
        compile(MOV, E, B) shouldBe 0x58
        compile(MOV, E, C) shouldBe 0x59
        compile(MOV, E, D) shouldBe 0x5A
        compile(MOV, E, E) shouldBe 0x5B
        compile(MOV, E, H) shouldBe 0x5C
        compile(MOV, E, L) shouldBe 0x5D
        compile(MOV, E, M) shouldBe 0x5E

        compile(MOV, H, A) shouldBe 0x67
        compile(MOV, H, B) shouldBe 0x60
        compile(MOV, H, C) shouldBe 0x61
        compile(MOV, H, D) shouldBe 0x62
        compile(MOV, H, E) shouldBe 0x63
        compile(MOV, H, H) shouldBe 0x64
        compile(MOV, H, L) shouldBe 0x65
        compile(MOV, H, M) shouldBe 0x66

        compile(MOV, L, A) shouldBe 0x6F
        compile(MOV, L, B) shouldBe 0x68
        compile(MOV, L, C) shouldBe 0x69
        compile(MOV, L, D) shouldBe 0x6A
        compile(MOV, L, E) shouldBe 0x6B
        compile(MOV, L, H) shouldBe 0x6C
        compile(MOV, L, L) shouldBe 0x6D
        compile(MOV, L, M) shouldBe 0x6E

        compile(MOV, M, A) shouldBe 0x77
        compile(MOV, M, B) shouldBe 0x70
        compile(MOV, M, C) shouldBe 0x71
        compile(MOV, M, D) shouldBe 0x72
        compile(MOV, M, E) shouldBe 0x73
        compile(MOV, M, H) shouldBe 0x74
        compile(MOV, M, L) shouldBe 0x75
        compile(MOV, M, M) shouldBe 0x76
    }

    @Test fun `mvi command`() {
        compile(MVI, A, 0x12.toArg()) shouldBe 0x3E12
        compile(MVI, B, 0x34.toArg()) shouldBe 0x0634
        compile(MVI, C, 0x56.toArg()) shouldBe 0x0E56
        compile(MVI, D, 0x78.toArg()) shouldBe 0x1678
        compile(MVI, E, 0x9A.toArg()) shouldBe 0x1E9A
        compile(MVI, H, 0xBC.toArg()) shouldBe 0x26BC
        compile(MVI, L, 0xDE.toArg()) shouldBe 0x2EDE
        compile(MVI, M, 0xF0.toArg()) shouldBe 0x36F0
    }

    @Test fun `jumps and calls`() {
        compile(CALL, 0x1234.toArg()) shouldBe 0xCD3412
        compile(CZ, 0x1234.toArg()) shouldBe 0xCC3412
        compile(CNZ, 0x1234.toArg()) shouldBe 0xC43412
        compile(CC, 0x1234.toArg()) shouldBe 0xDC3412
        compile(CNC, 0x1234.toArg()) shouldBe 0xD43412
        compile(CP, 0x1234.toArg()) shouldBe 0xF43412
        compile(CM, 0x1234.toArg()) shouldBe 0xFc3412
        compile(CPE, 0x1234.toArg()) shouldBe 0xEC3412
        compile(CPO, 0x1234.toArg()) shouldBe 0xE43412

        compile(JMP, 0x1234.toArg()) shouldBe 0xC33412
        compile(JZ, 0x1234.toArg()) shouldBe 0xCA3412
        compile(JNZ, 0x1234.toArg()) shouldBe 0xC23412
        compile(JC, 0x1234.toArg()) shouldBe 0xDA3412
        compile(JNC, 0x1234.toArg()) shouldBe 0xD23412
        compile(JP, 0x1234.toArg()) shouldBe 0xF23412
        compile(JM, 0x1234.toArg()) shouldBe 0xFA3412
        compile(JPE, 0x1234.toArg()) shouldBe 0xEA3412
        compile(JPO, 0x1234.toArg()) shouldBe 0xE23412
    }

    @Test fun `pair commands`() {
        compile(DAD, BC) shouldBe 0x09
        compile(DAD, DE) shouldBe 0x19
        compile(DAD, HL) shouldBe 0x29
        compile(DAD, SP) shouldBe 0x39

        compile(INX, BC) shouldBe 0x03
        compile(INX, DE) shouldBe 0x13
        compile(INX, HL) shouldBe 0x23
        compile(INX, SP) shouldBe 0x33

        compile(DCX, BC) shouldBe 0x0B
        compile(DCX, DE) shouldBe 0x1B
        compile(DCX, HL) shouldBe 0x2B
        compile(DCX, SP) shouldBe 0x3B

        compile(LDAX, BC) shouldBe 0x0A
        compile(LDAX, DE) shouldBe 0x1A

        compile(STAX, BC) shouldBe 0x02
        compile(STAX, DE) shouldBe 0x12

        compile(PUSH, BC) shouldBe 0xC5
        compile(PUSH, DE) shouldBe 0xD5
        compile(PUSH, HL) shouldBe 0xE5
        compile(PUSH, PSW) shouldBe 0xF5

        compile(POP, BC) shouldBe 0xC1
        compile(POP, DE) shouldBe 0xD1
        compile(POP, HL) shouldBe 0xE1
        compile(POP, PSW) shouldBe 0xF1

        compile(LXI, BC, 0x1234.toArg()) shouldBe 0x013412
        compile(LXI, DE, 0x1234.toArg()) shouldBe 0x113412
        compile(LXI, HL, 0x1234.toArg()) shouldBe 0x213412
        compile(LXI, SP, 0x1234.toArg()) shouldBe 0x313412
    }

    @Test fun `wrong pair commands`() {
        shouldThrow<AsmCompileException> {
            compile(DAD, PSW)
        }.message shouldBe "Unexpected pair: PSW"

        shouldThrow<AsmCompileException> {
            compile(INX, PSW)
        }.message shouldBe "Unexpected pair: PSW"

        shouldThrow<AsmCompileException> {
            compile(DCX, PSW)
        }.message shouldBe "Unexpected pair: PSW"

        shouldThrow<AsmCompileException> {
            compile(LDAX, HL)
        }.message shouldBe "Unexpected pair: HL"
        shouldThrow<AsmCompileException> {
            compile(LDAX, SP)
        }.message shouldBe "Unexpected pair: SP"
        shouldThrow<AsmCompileException> {
            compile(LDAX, PSW)
        }.message shouldBe "Unexpected pair: PSW"

        shouldThrow<AsmCompileException> {
            compile(STAX, HL)
        }.message shouldBe "Unexpected pair: HL"
        shouldThrow<AsmCompileException> {
            compile(STAX, SP)
        }.message shouldBe "Unexpected pair: SP"
        shouldThrow<AsmCompileException> {
            compile(STAX, PSW)
        }.message shouldBe "Unexpected pair: PSW"

        shouldThrow<AsmCompileException> {
            compile(PUSH, SP)
        }.message shouldBe "Unexpected pair: SP"

        shouldThrow<AsmCompileException> {
            compile(POP, SP)
        }.message shouldBe "Unexpected pair: SP"

        shouldThrow<AsmCompileException> {
            compile(LXI, PSW, 0x1234.toArg())
        }.message shouldBe "Unexpected pair: PSW"
    }

    private fun compile(cmd: I8085Cmd, vararg args: AsmArg): Int {
        val out =  BinaryOutput(Endian.LITTLE)
        out.gotoAddress(0)
        val sz = cmd.size(*args)
        out.reserve(sz)
        out.allocatePages()
        out.gotoAddress(0)
        cmd.compile(out, *args)
        val page = out.pages[0]
        sz shouldBeGreaterThan 0
        sz shouldBeLessThanOrEqual 4
        return when (sz) {
            4 -> (page[0] shl 24) + (page[1] shl 16) + (page[2] shl 8) + page[3]
            3 -> (page[0] shl 16) + (page[1] shl 8) + page[2]
            2 -> (page[0] shl 8) + page[1]
            1 -> page[0]
            else -> internalError()
        }
    }

    private fun Int.toArg(): ArgNumber = ArgNumber(this)
}