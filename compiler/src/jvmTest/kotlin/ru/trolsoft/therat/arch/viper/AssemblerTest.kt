package ru.trolsoft.therat.arch.viper

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.generator.BinaryOutput
import ru.trolsoft.compiler.generator.Endian
import ru.trolsoft.therat.arch.ArgNumber
import ru.trolsoft.therat.arch.AsmArg
import ru.trolsoft.therat.arch.viper.ViperCmd.*
import ru.trolsoft.therat.arch.viper.ArgReg.*
import ru.trolsoft.therat.arch.viper.ArgRegPair.*

class ViperCompilerTest {
    @Test fun `simple commands`() {
        compile(NOP)   shouldBe 0x00
        compile(RET)   shouldBe 0xC6
        compile(HALT)  shouldBe 0xEE
        compile(BREAK) shouldBe 0xEF
        compile(SEC)   shouldBe 0xF0
        compile(CLC)   shouldBe 0xF1
        compile(LPC)   shouldBe 0xF2
        compile(LPCP)  shouldBe 0xF3
        compile(IJMP)  shouldBe 0xC8
        compile(ICALL) shouldBe 0xC9
    }

    @Test fun `move rd, rs`() {
        compile(MOV, R0, R1) shouldBe 0x01
        compile(MOV, R0, R2) shouldBe 0x02
        compile(MOV, R0, R3) shouldBe 0x03
        compile(MOV, R0, R4) shouldBe 0x04
        compile(MOV, R0, R5) shouldBe 0x05
        compile(MOV, R0, R6) shouldBe 0x06
        compile(MOV, R0, R7) shouldBe 0x07

        compile(MOV, R1, R0) shouldBe 0x08
        compile(MOV, R1, R2) shouldBe 0x0A
        compile(MOV, R1, R3) shouldBe 0x0B
        compile(MOV, R1, R4) shouldBe 0x0C
        compile(MOV, R1, R5) shouldBe 0x0D
        compile(MOV, R1, R6) shouldBe 0x0E
        compile(MOV, R1, R7) shouldBe 0x0F

        compile(MOV, R2, R0) shouldBe 0x10
        compile(MOV, R2, R1) shouldBe 0x11
        compile(MOV, R2, R3) shouldBe 0x13
        compile(MOV, R2, R4) shouldBe 0x14
        compile(MOV, R2, R5) shouldBe 0x15
        compile(MOV, R2, R6) shouldBe 0x16
        compile(MOV, R2, R7) shouldBe 0x17

        compile(MOV, R3, R0) shouldBe 0x18
        compile(MOV, R3, R1) shouldBe 0x19
        compile(MOV, R3, R2) shouldBe 0x1A
        compile(MOV, R3, R4) shouldBe 0x1C
        compile(MOV, R3, R5) shouldBe 0x1D
        compile(MOV, R3, R6) shouldBe 0x1E
        compile(MOV, R3, R7) shouldBe 0x1F

        compile(MOV, R4, R0) shouldBe 0x20
        compile(MOV, R4, R1) shouldBe 0x21
        compile(MOV, R4, R2) shouldBe 0x22
        compile(MOV, R4, R3) shouldBe 0x23
        compile(MOV, R4, R5) shouldBe 0x25
        compile(MOV, R4, R6) shouldBe 0x26
        compile(MOV, R4, R7) shouldBe 0x27

        compile(MOV, R5, R0) shouldBe 0x28
        compile(MOV, R5, R1) shouldBe 0x29
        compile(MOV, R5, R2) shouldBe 0x2A
        compile(MOV, R5, R3) shouldBe 0x2B
        compile(MOV, R5, R4) shouldBe 0x2C
        compile(MOV, R5, R6) shouldBe 0x2E
        compile(MOV, R5, R7) shouldBe 0x2F

        compile(MOV, R6, R0) shouldBe 0x30
        compile(MOV, R6, R1) shouldBe 0x31
        compile(MOV, R6, R2) shouldBe 0x32
        compile(MOV, R6, R3) shouldBe 0x33
        compile(MOV, R6, R4) shouldBe 0x34
        compile(MOV, R6, R5) shouldBe 0x35
        compile(MOV, R6, R7) shouldBe 0x37

        compile(MOV, R7, R0) shouldBe 0x38
        compile(MOV, R7, R1) shouldBe 0x39
        compile(MOV, R7, R2) shouldBe 0x3A
        compile(MOV, R7, R3) shouldBe 0x3B
        compile(MOV, R7, R4) shouldBe 0x3C
        compile(MOV, R7, R5) shouldBe 0x3D
        compile(MOV, R7, R6) shouldBe 0x3E
    }
//
//    @Test fun testMovI() {
//    }
//
    @Test fun `alu rd, r0`() {
        compile(ADD, R0, R0) shouldBe 0x40
        compile(ADD, R1, R0) shouldBe 0x41
        compile(ADD, R2, R0) shouldBe 0x42
        compile(ADD, R3, R0) shouldBe 0x43

        compile(ADC, R0, R0) shouldBe 0x44
        compile(ADC, R1, R0) shouldBe 0x45
        compile(ADC, R2, R0) shouldBe 0x46
        compile(ADC, R3, R0) shouldBe 0x47

        compile(SUB, R1, R0) shouldBe 0x49
        compile(SUB, R2, R0) shouldBe 0x4A
        compile(SUB, R3, R0) shouldBe 0x4B

        compile(SBC, R0, R0) shouldBe 0x4C
        compile(SBC, R1, R0) shouldBe 0x4D
        compile(SBC, R2, R0) shouldBe 0x4E
        compile(SBC, R3, R0) shouldBe 0x4F

        compile(AND, R0, R0) shouldBe 0x50
        compile(AND, R1, R0) shouldBe 0x51
        compile(AND, R2, R0) shouldBe 0x52
        compile(AND, R3, R0) shouldBe 0x53

        compile(OR, R1, R0) shouldBe 0x55
        compile(OR, R2, R0) shouldBe 0x56
        compile(OR, R3, R0) shouldBe 0x57

        compile(XOR, R0, R0) shouldBe 0x58
        compile(XOR, R1, R0) shouldBe 0x59
        compile(XOR, R2, R0) shouldBe 0x5A
        compile(XOR, R3, R0) shouldBe 0x5B

        compile(CMP, R1, R0) shouldBe 0x5D
        compile(CMP, R2, R0) shouldBe 0x5E
        compile(CMP, R3, R0) shouldBe 0x5F
    }

    @Test fun `alu immediate`() {
        compile(SUBI, R0, 0x05.toArg()) shouldBe 0x4805
        compile(ORI,  R0, 0x0F.toArg()) shouldBe 0x540F
        compile(CMPI, R0, 0x10.toArg()) shouldBe 0x5C10
        compile(ADDI, R0, 0x01.toArg()) shouldBe 0xC101
        compile(XORI, R0, 0xFF.toArg()) shouldBe 0xC4FF
        compile(SBCI, R0, 0x02.toArg()) shouldBe 0xC702
        compile(ANDI, R0, 0x01.toArg()) shouldBe 0xD101
    }


    @Test fun `inc dec rd`() {
        compile(INC, R0) shouldBe 0x60
        compile(INC, R1) shouldBe 0x61
        compile(INC, R2) shouldBe 0x62
        compile(INC, R3) shouldBe 0x63
        compile(INC, R4) shouldBe 0x64
        compile(INC, R5) shouldBe 0x65
        compile(INC, R6) shouldBe 0x66
        compile(INC, R7) shouldBe 0x67

        compile(INC, RX) shouldBe 0xE8
        compile(INC, RY) shouldBe 0xEA
        compile(INC, RZ) shouldBe 0xEC

        compile(DEC, R0) shouldBe 0x68
        compile(DEC, R1) shouldBe 0x69
        compile(DEC, R2) shouldBe 0x6A
        compile(DEC, R3) shouldBe 0x6B
        compile(DEC, R4) shouldBe 0x6C
        compile(DEC, R5) shouldBe 0x6D
        compile(DEC, R6) shouldBe 0x6E
        compile(DEC, R7) shouldBe 0x6F

        compile(DEC, RX) shouldBe 0xE9
        compile(DEC, RY) shouldBe 0xEB
        compile(DEC, RZ) shouldBe 0xED
    }

    @Test fun `push pop rd`() {
        compile(PUSH, R0) shouldBe 0x70
        compile(PUSH, R1) shouldBe 0x71
        compile(PUSH, R2) shouldBe 0x72
        compile(PUSH, R3) shouldBe 0x73
        compile(PUSH, R4) shouldBe 0x74
        compile(PUSH, R5) shouldBe 0x75
        compile(PUSH, R6) shouldBe 0x76
        compile(PUSH, R7) shouldBe 0x77

        compile(POP, R0) shouldBe 0x78
        compile(POP, R1) shouldBe 0x79
        compile(POP, R2) shouldBe 0x7A
        compile(POP, R3) shouldBe 0x7B
        compile(POP, R4) shouldBe 0x7C
        compile(POP, R5) shouldBe 0x7D
        compile(POP, R6) shouldBe 0x7E
        compile(POP, R7) shouldBe 0x7F
    }

    @Test fun `load store not rd`() {
        compile(LOAD, R0) shouldBe 0x98
        compile(LOAD, R1) shouldBe 0x99
        compile(LOAD, R2) shouldBe 0x9A
        compile(LOAD, R3) shouldBe 0x9B
        compile(LOAD, R4) shouldBe 0x9C
        compile(LOAD, R5) shouldBe 0x9D
        compile(LOAD, R6) shouldBe 0x9E
        compile(LOAD, R7) shouldBe 0x9F

        compile(STORE, R0) shouldBe 0xA0
        compile(STORE, R1) shouldBe 0xA1
        compile(STORE, R2) shouldBe 0xA2
        compile(STORE, R3) shouldBe 0xA3
        compile(STORE, R4) shouldBe 0xA4
        compile(STORE, R5) shouldBe 0xA5
        compile(STORE, R6) shouldBe 0xA6
        compile(STORE, R7) shouldBe 0xA7

        compile(NOT, R0) shouldBe 0xA8
        compile(NOT, R1) shouldBe 0xA9
        compile(NOT, R2) shouldBe 0xAA
        compile(NOT, R3) shouldBe 0xAB
        compile(NOT, R4) shouldBe 0xAC
        compile(NOT, R5) shouldBe 0xAD
        compile(NOT, R6) shouldBe 0xAE
        compile(NOT, R7) shouldBe 0xAF
    }

    @Test fun `loadi storei rd, v8`() {
        compile(LOADI, R0, 0x00.toArg()) shouldBe 0x8800
        compile(LOADI, R0, 0x10.toArg()) shouldBe 0x8810
        compile(LOADI, R0, 0xFF.toArg()) shouldBe 0x88FF
        compile(LOADI, R1, 0x10.toArg()) shouldBe 0x8910
        compile(LOADI, R2, 0x10.toArg()) shouldBe 0x8A10
        compile(LOADI, R3, 0x10.toArg()) shouldBe 0x8B10
        compile(LOADI, R4, 0x10.toArg()) shouldBe 0x8C10
        compile(LOADI, R5, 0x10.toArg()) shouldBe 0x8D10
        compile(LOADI, R6, 0x10.toArg()) shouldBe 0x8E10
        compile(LOADI, R7, 0x20.toArg()) shouldBe 0x8F20
        compile(LOADI, R7, 0xFF.toArg()) shouldBe 0x8FFF

        // STOREI — 10 010 RRR + v8 (R0..R7, v8 = 0x00..0xFF)
        compile(STOREI, R0, 0x00.toArg()) shouldBe 0x9000
        compile(STOREI, R0, 0x10.toArg()) shouldBe 0x9010
        compile(STOREI, R0, 0xFF.toArg()) shouldBe 0x90FF
        compile(STOREI, R1, 0x10.toArg()) shouldBe 0x9110
        compile(STOREI, R2, 0x10.toArg()) shouldBe 0x9210
        compile(STOREI, R3, 0x10.toArg()) shouldBe 0x9310
        compile(STOREI, R4, 0x10.toArg()) shouldBe 0x9410
        compile(STOREI, R5, 0x10.toArg()) shouldBe 0x9510
        compile(STOREI, R6, 0x10.toArg()) shouldBe 0x9610
        compile(STOREI, R7, 0x20.toArg()) shouldBe 0x9720
        compile(STOREI, R7, 0xFF.toArg()) shouldBe 0x97FF

        compile(MOVI, R0, 0x00.toArg()) shouldBe 0x8000
        compile(MOVI, R0, 0x12.toArg()) shouldBe 0x8012
        compile(MOVI, R0, 0xFF.toArg()) shouldBe 0x80FF
        compile(MOVI, R1, 0x12.toArg()) shouldBe 0x8112
        compile(MOVI, R2, 0x12.toArg()) shouldBe 0x8212
        compile(MOVI, R3, 0x12.toArg()) shouldBe 0x8312
        compile(MOVI, R4, 0x12.toArg()) shouldBe 0x8412
        compile(MOVI, R5, 0x12.toArg()) shouldBe 0x8512
        compile(MOVI, R6, 0x12.toArg()) shouldBe 0x8612
        compile(MOVI, R7, 0x34.toArg()) shouldBe 0x8734
        compile(MOVI, R7, 0xFF.toArg()) shouldBe 0x87FF
    }

    @Test fun shifts() {
        // ROL R0 — alias ADC R0, R0 (0x44)
        compile(ROL, R0) shouldBe 0x44

        // ROR R0 — 11 000 010 (0xC2)
        compile(ROR, R0) shouldBe 0xC2

        // LSR R0 — 11 000 101 (0xC5)
        compile(LSR, R0) shouldBe 0xC5

        // LSL R0 — alias ADD R0, R0 (0x40)
        compile(LSL, R0) shouldBe 0x40
    }

    @Test fun `jumps addr16`() {
        compile(JMP, 0x0000.toArg()) shouldBe 0xCA0000
        compile(JMP, 0x1234.toArg()) shouldBe 0xCA1234
        compile(JMP, 0xFFFF.toArg()) shouldBe 0xCAFFFF

        compile(JNZ, 0x0000.toArg()) shouldBe 0xD80000
        compile(JNZ, 0x1234.toArg()) shouldBe 0xD81234
        compile(JNZ, 0xFFFF.toArg()) shouldBe 0xD8FFFF

        compile(JZ, 0x0000.toArg()) shouldBe 0xD90000
        compile(JZ, 0x1234.toArg()) shouldBe 0xD91234
        compile(JZ, 0xFFFF.toArg()) shouldBe 0xD9FFFF

        compile(JNC, 0x0000.toArg()) shouldBe 0xDA0000
        compile(JNC, 0x1234.toArg()) shouldBe 0xDA1234
        compile(JNC, 0xFFFF.toArg()) shouldBe 0xDAFFFF

        compile(JC, 0x0000.toArg()) shouldBe 0xDB0000
        compile(JC, 0x1234.toArg()) shouldBe 0xDB1234
        compile(JC, 0xFFFF.toArg()) shouldBe 0xDBFFFF

        compile(JVS, 0x0000.toArg()) shouldBe 0xDC0000
        compile(JVS, 0x1234.toArg()) shouldBe 0xDC1234
        compile(JVS, 0xFFFF.toArg()) shouldBe 0xDCFFFF

        compile(JVC, 0x0000.toArg()) shouldBe 0xDD0000
        compile(JVC, 0x1234.toArg()) shouldBe 0xDD1234
        compile(JVC, 0xFFFF.toArg()) shouldBe 0xDDFFFF

        compile(JP, 0x0000.toArg()) shouldBe 0xDE0000
        compile(JP, 0x1234.toArg()) shouldBe 0xDE1234
        compile(JP, 0xFFFF.toArg()) shouldBe 0xDEFFFF

        compile(JM, 0x0000.toArg()) shouldBe 0xDF0000
        compile(JM, 0x1234.toArg()) shouldBe 0xDF1234
        compile(JM, 0xFFFF.toArg()) shouldBe 0xDFFFFF
    }

    @Test fun `calls addr16`() {
        compile(CALL, 0x0000.toArg()) shouldBe 0xCB0000
        compile(CALL, 0x5678.toArg()) shouldBe 0xCB5678
        compile(CALL, 0xFFFF.toArg()) shouldBe 0xCBFFFF

        compile(CALLNZ, 0x0000.toArg()) shouldBe 0xE00000
        compile(CALLNZ, 0x1234.toArg()) shouldBe 0xE01234
        compile(CALLNZ, 0xFFFF.toArg()) shouldBe 0xE0FFFF

        compile(CALLZ, 0x0000.toArg()) shouldBe 0xE10000
        compile(CALLZ, 0x1234.toArg()) shouldBe 0xE11234
        compile(CALLZ, 0xFFFF.toArg()) shouldBe 0xE1FFFF

        compile(CALLNC, 0x0000.toArg()) shouldBe 0xE20000
        compile(CALLNC, 0x1234.toArg()) shouldBe 0xE21234
        compile(CALLNC, 0xFFFF.toArg()) shouldBe 0xE2FFFF

        compile(CALLC, 0x0000.toArg()) shouldBe 0xE30000
        compile(CALLC, 0x1234.toArg()) shouldBe 0xE31234
        compile(CALLC, 0xFFFF.toArg()) shouldBe 0xE3FFFF

        compile(CALLVS, 0x0000.toArg()) shouldBe 0xE40000
        compile(CALLVS, 0x1234.toArg()) shouldBe 0xE41234
        compile(CALLVS, 0xFFFF.toArg()) shouldBe 0xE4FFFF

        compile(CALLVC, 0x0000.toArg()) shouldBe 0xE50000
        compile(CALLVC, 0x1234.toArg()) shouldBe 0xE51234
        compile(CALLVC, 0xFFFF.toArg()) shouldBe 0xE5FFFF

        compile(CALLP, 0x0000.toArg()) shouldBe 0xE60000
        compile(CALLP, 0x1234.toArg()) shouldBe 0xE61234
        compile(CALLP, 0xFFFF.toArg()) shouldBe 0xE6FFFF

        compile(CALLM, 0x0000.toArg()) shouldBe 0xE70000
        compile(CALLM, 0x1234.toArg()) shouldBe 0xE71234
        compile(CALLM, 0xFFFF.toArg()) shouldBe 0xE7FFFF
    }

    @Test fun `rjmpf rjmpb n8`() {
        compile(RJMPF, 0x00.toArg()) shouldBe 0xCC00
        compile(RJMPF, 0x01.toArg()) shouldBe 0xCC01
        compile(RJMPF, 0x7F.toArg()) shouldBe 0xCC7F
        compile(RJMPF, 0x80.toArg()) shouldBe 0xCC80
        compile(RJMPF, 0xFF.toArg()) shouldBe 0xCCFF

        compile(RJMPB, 0x00.toArg()) shouldBe 0xCD00
        compile(RJMPB, 0x01.toArg()) shouldBe 0xCD01
        compile(RJMPB, 0x7F.toArg()) shouldBe 0xCD7F
        compile(RJMPB, 0x80.toArg()) shouldBe 0xCD80
        compile(RJMPB, 0xFF.toArg()) shouldBe 0xCDFF
    }

    @Test fun `jpc jps p8, addr16`() {
        compile(JPC, 1.toArg(), 0x0000.toArg()).toLong() and 0xffffffff shouldBe 0xCE010000
        compile(JPC, 1.toArg(), 0x1234.toArg()).toLong() and 0xffffffff shouldBe 0xCE011234
        compile(JPC, 40.toArg(), 0xFFFF.toArg()).toLong() and 0xffffffff shouldBe 0xCE28FFFF

        compile(JPS, 1.toArg(), 0x0000.toArg()).toLong() and 0xffffffff shouldBe 0xCF010000
        compile(JPS, 1.toArg(), 0x1234.toArg()).toLong() and 0xffffffff shouldBe 0xCF011234
        compile(JPS, 40.toArg(), 0xFFFF.toArg()).toLong() and 0xffffffff shouldBe 0xCF28FFFF
    }

    @Test fun syscall() {
        compile(SYSCALL, 0x00.toArg()) shouldBe 0xD000
        compile(SYSCALL, 0x01.toArg()) shouldBe 0xD001
        compile(SYSCALL, 0x0D.toArg()) shouldBe 0xD00D
        compile(SYSCALL, 0x0E.toArg()) shouldBe 0xD00E
        compile(SYSCALL, 0xFF.toArg()) shouldBe 0xD0FF
    }

    @Test fun `ports io`() {
        compile(IN, R0, 0x00.toArg()) shouldBe 0xB000
        compile(IN, R7, 0x12.toArg()) shouldBe 0xB712
        compile(IN, R3, 0xff.toArg()) shouldBe 0xB3ff

        compile(OUT, 0x00.toArg(), R0) shouldBe 0xB800
        compile(OUT, 0x12.toArg(), R7) shouldBe 0xBF12
        compile(OUT, 0xFF.toArg(), R3) shouldBe 0xBBFF

        compile(OUTI, 0x12.toArg(), 0x34.toArg()) shouldBe 0xC01234
    }

    private fun compile(cmd: ViperCmd, vararg args: AsmArg): Int {
        val sz = cmd.size(*args)
        val out = BinaryOutput(Endian.BIG).apply {
            gotoAddress(0)
            reserve(sz)
            allocatePages()
            gotoAddress(0)
        }
        cmd.compile(out, *args)
        val page = out.pages[0]

        return when (sz) {
            4 -> (page[0] shl 24) + (page[1] shl 16) + (page[2] shl 8) + page[3]
            3 -> (page[0] shl 16) + (page[1] shl 8) + page[2]
            2 -> (page[0] shl 8) + page[1]
            1 -> page[0]
            else -> throw IllegalArgumentException("Wrong instruction size $sz")
        }
    }

    private fun Int.toArg(): ArgNumber = ArgNumber(this)
}
