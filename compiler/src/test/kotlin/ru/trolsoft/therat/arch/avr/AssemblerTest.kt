package ru.trolsoft.therat.arch.avr

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.therat.arch.ArgNumber
import ru.trolsoft.therat.arch.AsmArg
import ru.trolsoft.therat.arch.avr.ArgReg.*
import ru.trolsoft.therat.arch.avr.ArgRegPair.*
import ru.trolsoft.therat.arch.avr.AvrCmd.*
import ru.trolsoft.compiler.generator.BinaryOutput
import ru.trolsoft.compiler.generator.Endian


private fun compile(cmd: AvrCmd, vararg args: AsmArg): Int {
    val out = BinaryOutput(Endian.LITTLE)
    out.gotoAddress(0)
    val sz = cmd.size(*args)
    out.reserve(sz)
    out.allocatePages()
    out.gotoAddress(0)
    cmd.compile(out, *args)
    val page = out.pages[0]
    if (sz == 4) {
//        val stream = ByteArrayOutputStream()
//        out.saveToIntelHex(stream, 16)
        return (page[0] shl 24) + (page[1] shl 16) + (page[2] shl 8) + page[3]
    }
    sz shouldBe 2
    return (page[0] shl 8) + page[1]
}


class AvrAssemblerTest {
    @Test fun test() {
        compile(NOP) shouldBe 0x0000
        compile(SEC) shouldBe 0x0894
        compile(CLC) shouldBe 0x8894
        compile(SEN) shouldBe 0x2894
        compile(CLN) shouldBe 0xA894
        compile(SEZ) shouldBe 0x1894
        compile(CLZ) shouldBe 0x9894
        compile(SEI) shouldBe 0x7894
        compile(CLI) shouldBe 0xF894
        compile(SES) shouldBe 0x4894
        compile(CLS) shouldBe 0xC894
        compile(SEV) shouldBe 0x3894
        compile(CLV) shouldBe 0xB894
        compile(SET) shouldBe 0x6894
        compile(CLT) shouldBe 0xE894
        compile(SEH) shouldBe 0x5894
        compile(CLH) shouldBe 0xD894
        compile(SLEEP) shouldBe 0x8895
        compile(WDR) shouldBe 0xA895
        compile(IJMP) shouldBe 0x0994
        compile(EIJMP) shouldBe 0x1994
        compile(ICALL) shouldBe 0x0995
        compile(EICALL) shouldBe 0x1995
        compile(RET) shouldBe 0x0895
        compile(RETI) shouldBe 0x1895
        compile(SPM) shouldBe 0xE895
        compile(ESPM) shouldBe 0xF895
        compile(BREAK) shouldBe 0x9895

        compile(LPM) shouldBe 0xC895
        compile(LPM, R10, Z_PLUS) shouldBe 0xA590
        compile(LPM, R31, Z) shouldBe 0xF491

        compile(ELPM) shouldBe 0xD895
        compile(ELPM, R20, Z_PLUS) shouldBe 0x4791
        compile(ELPM, R5, Z) shouldBe 0x5690

        compile(BSET, 3.toArg()) shouldBe 0x3894
        compile(BCLR, 5.toArg()) shouldBe 0xD894
        compile(SER, R16) shouldBe 0x0FEF
        compile(COM, R17) shouldBe 0x1095
        compile(NEG, R1) shouldBe 0x1194
        compile(INC, R2) shouldBe 0x2394
        compile(DEC, R3) shouldBe 0x3A94
        compile(LSR, R4) shouldBe 0x4694
        compile(ROR, R5) shouldBe 0x5794
        compile(ASR, R6) shouldBe 0x6594
        compile(SWAP, R7) shouldBe 0x7294
        compile(PUSH, R8) shouldBe 0x8F92
        compile(POP, R9) shouldBe 0x9F90
        compile(TST, R10) shouldBe 0xAA20
        compile(TST, R27) shouldBe 0xbb23
        compile(CLR, R11) shouldBe 0xBB24
        compile(LSL, R12) shouldBe 0xCC0C
        compile(ROL, R13) shouldBe 0xDD1C
        compile(BREQ, 1.toArg()) shouldBe 0x09F0
        compile(BREQ, 1.toArg()) shouldBe 0x09F0
        compile(BRNE, 1.toArg()) shouldBe 0x09F4
        compile(BRCS, 1.toArg()) shouldBe 0x08F0
        compile(BRCC, 1.toArg()) shouldBe 0x08F4
        compile(BRSH, 1.toArg()) shouldBe 0x08F4
        compile(BRLO, 1.toArg()) shouldBe 0x08F0
        compile(BRMI, 1.toArg()) shouldBe 0x0AF0
        compile(BRPL, 1.toArg()) shouldBe 0x0AF4
        compile(BRGE, 1.toArg()) shouldBe 0x0CF4
        compile(BRLT, 1.toArg()) shouldBe 0x0CF0
        compile(BRHS, 1.toArg()) shouldBe 0x0DF0
        compile(BRHC, 1.toArg()) shouldBe 0x0DF4
        compile(BRTS, 1.toArg()) shouldBe 0x0EF0
        compile(BRTC, 1.toArg()) shouldBe 0x0EF4
        compile(BRVS, 1.toArg()) shouldBe 0x0BF0
        compile(BRVC, 1.toArg()) shouldBe 0x0BF4
        compile(BRIE, 1.toArg()) shouldBe 0x0FF0
        compile(BRID, 1.toArg()) shouldBe 0x0FF4
        compile(RJMP, 1.toArg()) shouldBe 0x01C0
        compile(RJMP, (-2).toArg()) shouldBe 0xFECF
        compile(RCALL, 1.toArg()) shouldBe 0x01D0
        compile(RCALL, (-2).toArg()) shouldBe 0xFEDF
        compile(JMP, 3.toArg()) shouldBe 0x0C940300
        compile(CALL, 3.toArg()) shouldBe 0x0E940300

        compile(BRBS, ArgNumber(3), 1.toArg()) shouldBe 0x0BF0
        compile(BRBS, ArgNumber(3), (-2).toArg()) shouldBe 0xF3F3
        compile(BRBC, ArgNumber(3), 1.toArg()) shouldBe 0x0BF4
        compile(BRBC, ArgNumber(3), (-2).toArg()) shouldBe 0xF3F7

        compile(ADD, R1, R20) shouldBe 0x140E
        compile(ADC, R31, R0) shouldBe 0xF01D
        compile(SUB, R1, R10) shouldBe 0x1A18
        compile(SBC, R13, R25) shouldBe 0xD90A
        compile(AND, R3, R5) shouldBe 0x3520
        compile(AND, R27, R27) shouldBe 0xbb23
        compile(OR, R23, R29) shouldBe 0x7D2B
        compile(EOR, R23, R0) shouldBe 0x7025
        compile(CP, R7, R11) shouldBe 0x7B14
        compile(CPC, R17, R8) shouldBe 0x1805
        compile(CPSE, R9, R18) shouldBe 0x9212
        compile(MOV, R23, R3) shouldBe 0x732D
        compile(MUL, R4, R30) shouldBe 0x4E9E
        compile(MOVW, R11_R10, R31_R30) shouldBe 0x5F01
        compile(MULS, R20, R30) shouldBe 0x4E02
        compile(MULSU, R18, R23) shouldBe 0x2703
        compile(FMUL, R20, R21) shouldBe 0x4D03
        compile(FMULS, R21, R19) shouldBe 0xD303
        compile(FMULSU, R23, R16) shouldBe 0xF803
        compile(ADIW, R25_R24, 1.toArg()) shouldBe 0x0196
        compile(SBIW, R29_R28, 50.toArg()) shouldBe 0xE297
        compile(SUBI, R16, 100.toArg()) shouldBe 0x0456
        compile(SUBI, R29, (-99).toArg()) shouldBe 0xDD59
        compile(SBCI, R17, 10.toArg()) shouldBe 0x1A40
        compile(ANDI, R18, 30.toArg()) shouldBe 0x2E71
        compile(ORI, R19, 100.toArg()) shouldBe 0x3466
        compile(SBR, R20, 5.toArg()) shouldBe 0x4560
        compile(CPI, R30, 155.toArg()) shouldBe 0xEB39
        compile(LDI, R31, 203.toArg()) shouldBe 0xFBEC
        compile(CBR, R29, 123.toArg()) shouldBe 0xD478
        compile(SBRC, R3, 1.toArg()) shouldBe 0x31FC
        compile(SBRS, R11, 5.toArg()) shouldBe 0xB5FE
        compile(BST, R21, 6.toArg()) shouldBe 0x56FB
        compile(BLD, R31, 7.toArg()) shouldBe 0xF7F9
        compile(IN, R11, 10.toArg()) shouldBe 0xBAB0
        compile(OUT, 5.toArg(), R29) shouldBe 0xD5B9
        compile(SBIC, 10.toArg(), 1.toArg()) shouldBe 0x5199
        compile(SBIS, 20.toArg(), 2.toArg()) shouldBe 0xA29B
        compile(SBI, 30.toArg(), 3.toArg()) shouldBe 0xF39A
        compile(CBI, 5.toArg(), 4.toArg()) shouldBe 0x2C98
        compile(LDS, R20, 100.toArg()) shouldBe 0x40916400
        compile(STS, 0x1234.toArg(), R11).toLong() and 0xffffffff shouldBe 0xB0923412

        compile(LD, R11, X) shouldBe 0xBC90
        compile(LD, R12, X_PLUS) shouldBe 0xCD90
        compile(LD, R13, MINUS_X) shouldBe 0xDE90
        compile(LD, R21, Y) shouldBe 0x5881
        compile(LD, R22, Y_PLUS) shouldBe 0x6991
        compile(LD, R23, MINUS_Y) shouldBe 0x7A91
        compile(LD, R31, Z) shouldBe 0xF081
        compile(LD, R30, Z_PLUS) shouldBe 0xE191
        compile(LD, R29, MINUS_Z) shouldBe 0xD291

        compile(ST, X, R1) shouldBe 0x1C92
        compile(ST, X_PLUS, R3) shouldBe 0x3D92
        compile(ST, MINUS_X, R5) shouldBe 0x5E92
        compile(ST, Y, R10) shouldBe 0xA882
        compile(ST, Y_PLUS, R15) shouldBe 0xF992
        compile(ST, MINUS_Y, R19) shouldBe 0x3A93
        compile(ST, Z, R25) shouldBe 0x9083
        compile(ST, Z_PLUS, R27) shouldBe 0xB193
        compile(ST, MINUS_Z, R28) shouldBe 0xC293

        compile(LDD, R13, ArgOffsetPair(Y, 37)) shouldBe 0xDDA0
        compile(LDD, R23, ArgOffsetPair(Z, 11)) shouldBe 0x7385

        compile(STD, ArgOffsetPair(Y, 17), R11) shouldBe 0xB98A
        compile(STD, ArgOffsetPair(Z, 21), R22) shouldBe 0x658B
    }
}

private fun Int.toArg(): ArgNumber = ArgNumber(this)