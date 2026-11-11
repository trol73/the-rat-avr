package ru.trolsoft.therat.i8085.compiler

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test

class TetrisTest {
//    @Test fun test() {
//        val code = """
//            extern proc biosReadKeyboard() absolute 0xF803
//            extern proc biosPrintChar(chr: C) absolute 0xF809
//            extern proc biosPrintMessage(msg: HL) absolute 0xF818
//            extern proc biosCheckKeyboard() absolute 0xF81B	; Ввод кода нажатой клавиши (A=0FFH - не нажата, A=0FEH - РУС/ЛАТ, ИНАЧЕ - код клавиши)
//
//            proc init() {
//                SP = 0x75FF
//                DE = 0x03F6
//                call    prc0350
//                mem[0x132E] = A = 0
//                call	prc56F(chr: 0x0A, len: 0x10)
//                mem[0x1305] = HL = 0x0100
//                HL = 0x1330
//                JMP	prc79D
//            }
//
//            proc prc100() {
//                HL = 0x7824
//                mem[0x1307] = HL
//                mem[0x1309] = HL
//                XCHG
//                mem[0x1319] = A = 0x14
//                mem[0x1311] = A = 0
//                mem[0x1312] = A
//                CALL	prc21F
//                CALL	prc24A
//                mem[0x132F] = A
//                CALL	prc21F
//                CALL	0x025E
//                A = mem[0x132F]
//                CPI	0x2E
//                JNZ	0x032E
//                HL = 0x7ACE
//                BC = 0x0802
//lbl_1:
//                M = 0x20
//                HL++
//                B--
//                JNZ	lbl_1
//                B = 0x08
//                HL = 0x7B1C
//                C--
//                JNZ	lbl_1
//                A = mem[0x1317]
//                ORA	A
//                JNZ	0x0153
//                DE = 0x7AD0
//                CALL	0x0228
//                CALL	0x025E
//                mem[0x130D] = A = mem[0x130F]
//                mem[0x130E] = A = mem[0x1310]
//                mem[0x130C] = A = 0
//lbl_2:
//                HL = 0x130D
//                A++
//                CMP	M
//                CZ	0x017B
//                HL = 0x130E
//                CMP	M
//                CZ	0x02BF
//                C = 0xFF
//lbl_3:
//                C--
//                JNZ	lbl_3
//                JMP	lbl_2
//            }
//
//            proc prc17B() {
//                PUSH	PSW
//                M++
//                mem[0x130B] = A = 0xFF
//                CALL    biosCheckKeyboard	; Ввод кода нажатой клавиши (A=0FFH - не нажата, A=0FEH - РУС/ЛАТ, ИНАЧЕ - код клавиши)
//                CPI	0xFF
//                JZ	lbl_2BD
//                B = A
//                HL = 0x130D
//                A = mem[0x130F]
//                A += M
//                M = A
//                A = B
//                CPI	0x08
//                JZ	lbl_1e1
//                CPI	0x18
//                JZ	lbl_1EC
//                CPI	0x1A
//                JZ	lbl_1f7
//                CPI	0x19
//                JZ	lbl_207
//                HL = mem[0x1307]
//                XCHG
//                CALL	prc21F
//                CALL	0x027F
//            lbl_01B2:
//                CALL	prc21F
//                HL = 0x004E
//                HL += DE
//                XCHG
//                A = mem[0x1319]
//                A--
//                mem[0x1319] = A
//                CALL	prc24A
//                CPI	0x2E
//                JZ	lbl_01B2
//                CALL	prc21F
//                HL = 0xFFB2 + DE
//                mem[0x1307] = HL
//                XCHG
//                A = mem[0x1319]
//                A++
//                mem[0x1319] = A
//                CALL	0x025E
//                JMP	lbl_2BD
//        lbl_1e1:
//                HL = mem[0x1307]
//                HL--
//                HL--
//                mem[0x1309] = HL
//                JMP	0x028C
//        lbl_1ec:
//                HL = mem[0x1307]
//                HL++
//                HL++
//                mem[0x1309] = HL
//                JMP	0x028C
//         lbl_1f7:
//                A = mem[0x1311] + 1
//                CPI	0x04
//                JM	0x0201
//                mem[0x1312] = A = 0
//                JMP	0x028C
//         lbl_207:
//                CALL	biosPrintChar(0x07)	; Вывод символа C на экран
//                mem[0x1317] = A = mem[0x1317] - 4
//                JZ	lbl_2BD
//                mem[0x1317] = A = 0x04
//                JMP	lbl_2BD
//            }
//
//            proc prc21F() {
//                mem[0x1313] = A = mem[0x1311]
//                JMP	lbl_234
//                A = mem[0x1300]
//                JMP	lbl_237
//                A = mem[0x1312]
//                mem[0x1313] = A
//            lbl_234:
//                A = mem[0x1301]
//            lbl_237:
//                RRC
//                RRC
//                RRC
//                A &= 0xE0
//                C = A
//                A = mem[0x1313]
//                RLC
//                RLC
//                RLC
//                A &= 0x1F
//                A += C
//                C = A
//                B = 0
//                RET
//            }
//
//            proc prc24A() {
//                A = mem[BC]
//                L = A
//                C++
//                A = mem[BC]
//                H = A
//                C++
//                HL += DE
//                A = mem[HL]
//                CPI	0x2E
//                RNZ
//                A = C
//                A &= 7
//                JNZ	prc24a
//                A = 0x2E
//                RET
//            }
//
//
//            proc prc0350() {
//                C = 0x1F
//                saveregs (DE) {
//                   call biosPrintChar()
//                }
//                HL = 0x7997
//                B = 0x07
//                mem[0x1318] = A = mem[DE++]
// loop:
//                saveregs (BC) {
//                   B = 0x0808
//        ll_2:
//                   LDAX	DE
//                   DE++
//                   saveregs (DE) {
//                        E = A
//                        D = A = mem[0x1318]
//                        A = E
//                ll_1:
//                        RAL
//                        JNC	ll_3
//                        M = D
//                ll_3:
//                        HL++
//                        C--
//                        JNZ	ll_1
//                        C = 0x08
//                }
//                B--
//                JNZ	ll_2
//              }
//              B--
//              RZ
//              C = 0x0E
//     lbl:
//              HL++
//              C--
//              JNZ	lbl
//              JMP	loop
//            }
//
//            proc prc038c() {
//               call    biosPrintChar(0x1F)
//               D = 0x15
//               saveregs (DE) {
//                  CALL	0x0577
//                  BC = 0x0B18
//                  call	prc56F(chr: 0x18, len: 8)
//               }
//               D--
//               JZ	0x03AD
//               saveregs (DE) {
//                  call    biosPrintMessage(0x045D)
//               }
//               JMP	0x0393
//               call	biosPrintMessage(0x0474)
//               call	biosPrintChar(0x0C)
//               ret
//            }
//
//            proc prc03B9() {
//               HL = 0x7885
//               DE = 0x048B
//               CALL	prc03ED
//               CALL	prc03ED(0x791E)
//               CALL	prc03ED(0x796C)
//               CALL	prc03ED(0x79BA)
//               CALL	prc03ED(0x7A08)
//               CALL	prc03ED(0x7BDC)
//               CALL	prc03ED(0x7C78)
//               CALL	prc03ED(0x7D14)
//               RET
//            }
//
//            proc prc03ED(arg: HL) {
//                A = mem[DE++]
//                ORA	A
//                RZ
//                mem[HL++] = A
//                JMP	prc03ED
//            }
//
//
//            proc prc535() {
//               saveregs (BC) {
//                  A = mem[0x1314]++
//                  mem[0x1314] = A
//                  mem[0x132C] = HL = mem[0x132A]
//                  B--
//l_2:
//                  C = 0x14
//                  HL = mem[0x132C]
//                  XCHG
//                  HL = 0xFFB2 + DE
//                  mem[0x132C] = HL
//l_3:
//                  A = M
//                  STAX	DE
//                  DE++
//                  HL++
//                  C--
//                  JNZ	l_3
//                  B--
//                  JNZ	l_2
//                  HL = mem[0x132C]
//                  DE = 0x2E20
//                  C = 0x0A
//l_1:
//                  M = D
//                  HL++
//                  M = E
//                  HL++
//                  C--
//                  JNZ	l_1
//                }
//                RET
//            }
//
//
//            proc prc56F(chr: C, len: B) {
//                call    biosPrintChar(chr)
//                B--
//                JNZ     prc56F
//                ret
//            }
//
//            proc prc581() {
//                LDA	0x1314
//                A++
//                B = A
//                A = mem[0x1338]++
//                C = A
//                A = 0
//l_1:
//                A += 10
//                C--
//                JNZ	l_1
//                CMP	B
//                RP
//                A = mem[0x1338]
//                CPI	0x09
//                RZ
//                A++
//                STA	0x1338
//                JMP	biosPrintChar(0x07)	; Вывод символа C на экран
//            }
//
//            proc prc5A3() {
//                A = mem[0x1319]
//                HL = 0x1338
//                A += M
//                A += M
//                A += M
//                HL = 0x1317
//                A += mem[HL]
//                E = A
//                D = 0
//                mem[0x1315] = HL = mem[0x1315] + DE
//                RET
//           }
//
//           proc prc5BB() {
//                mem[0x1327] = HL
//                XCHG
//                mem[0x1326] = A = 0
//                mem[0x1329] = A = H
//           lbl_3:
//                E = 0
//                B = 0x10
//                ORA	A
//          lbl_0:
//                A = L
//                RAL
//                L = A
//                A = H
//                RAL
//                H = A
//                A = E
//                RAL
//                E = A
//                A -= 0x0A
//                CMC
//                JNC	lbl_1
//                E = A
//                B--
//lbl_1:
//                JNZ	lbl_0
//                RAL
//                A &= 1
//                HL += HL
//                A |= L
//                L = A
//                A = E
//                A += 0x30
//                CALL	0x05FE
//                A = H
//                A |= L
//                JNZ	lbl_3
//                A = mem[0x1329]
//                ORA	A
//                JP	end
//                A = 0x2D
//                CALL	0x05FE
//end:
//                RET
//           }
//
//           proc prc5FE() {
//                saveregs (HL) {
//                   C = A
//                   HL = mem[0x1327]
//                   D = H
//                   E = L
//                   mem[0x1327] = ++HL
//                   A = mem[0x1326]
//                   ORA	A
//                   JZ	lbl_1
//                   B = A
//lbl_0:
//                   XCHG
//                   A = M
//                   XCHG
//                   M = A
//                   HL--
//                   DE--
//                   B--
//                   JNZ	lbl_0
//lbl_1:
//                   M = A = C
//                   mem[0x1326] = A = mem[0x1326]++
//                   HL--
//                   M = A
//                }
//                RET
//           }
//
//           proc prc628() {
//                B = M
//                HL++
//             lbl_1:
//                mem[DE] = A = M
//                HL++
//                DE++
//                B--
//                JNZ	lbl_1
//                RET
//           }
//
//           proc prc633() {
//                HL = 0x1330
//                B = 0x08
//lbl:
//@lbl_0638:
//                saveregs (HL) {
//@lbl_0639:
//                   CALL	biosReadKeyboard	; Ввод символа с клавиатуры -> A
//                   C = A
//                   CPI	0x08
//                   JZ	prc657()
//                   CPI	0x0D
//                   JZ	0x0666
//                   CPI	0x20
//                   JM	0x0639
//                   CALL	biosPrintChar	; Вывод символа C на экран
//                }
//                M = C
//                HL++
//                B--
//                JNZ	lbl
//                RET
//           }
//
//           proc prc657() {
//                A = B
//                CPI	0x08
//                JP	lbl_0639
//                CALL	biosPrintChar	; Вывод символа C на экран
//                POP	HL
//                HL--
//                B++
//                JMP	lbl_0638
//                POP	HL
//lbl_1:
//                mem[HL++] = 0x20
//                B--
//                JNZ	lbl_1
//                RET
//           }
//
//           proc prc66F() {
//                HL = 0x1000
//                A = mem[0x132E]++
//                E = A
//                D = 1
//           lbl_0:
//                A = D
//                CPI	0x0A
//                JP	lbl_1
//                C = 0x20
//                A += 0x30
//                B = A
//           lbl_3:
//                CALL	0x078A
//                C = B
//                CALL	0x078A
//                C = 0x2E
//                CALL	0x078A
//                CALL	prc788  ; printSpaceChar
//                B = 0x08
//                CALL	prc77E
//                CALL	prc792
//                C = A = mem[HL++] + 0x30
//                CALL	0x078A
//                CALL	prc792
//                B = 0x06
//                HL++
//                CALL	prc77E
//                CALL	prc792
//                BC = 0x0308
//                CALL	0x0795
//                A = mem[0x1322]
//                CMP	D
//                JNZ	lbl_2
//                C = 0x2A
//                CALL	0x078A
//                CALL	0x078A
//           lbl_2:
//                D++
//                A = D
//                CMP	E
//                RP
//                saveregs (HL, DE) {
//                   CALL	0x0577
//                }
//                JMP	lbl_0
//           lbl_1:
//                C = 0x31
//                A += 0x26
//                B = A
//                JMP	lbl_3
//
//                A = mem[0x132E]
//                ORA	A
//                JNZ	lbl_5
//                A++
//                mem[0x132E] = A
//                DE = 0x1000
//                HL = 0x1330
//                JMP	prc773
//
//           lbl_5:
//                B = A
//                B++
//                C = 0
//           lbl_6F2:
//                DE = 0x1339
//                HL = 0x1000
//                A = C
//                RLC
//                RLC
//                RLC
//                RLC
//                A += 9
//                L = A
//                A = mem[DE]
//                CMP	M
//                JZ	0x072E
//                JM	lbl_73c
//            @lbl_708:
//                A = L & 0xF0
//                L =  A
//                HL = mem[0x1320]
//                A = C
//                A++
//                mem[0x1322] = A
//                CALL	0x0760
//                HL = mem[0x1320]
//                XCHG
//            @lbl_71B:
//                HL = 0x1330
//                B = 0x10
//                CALL	0x0775
//                A = mem[0x132E]
//                CPI	0x0F
//                RZ
//                mem[0x132E] = ++A
//                RET
//           }
//
//           proc prc72E() {
//                A = M
//           lbl_72f:
//                HL++
//                DE++
//                mem[0x1323] = A
//                mem[DE] = A
//                CMP	M
//                JZ	lbl_756
//                JP	lbl_708
//           lbl_73c:
//                C++
//                A = B
//                CMP	C
//                JNZ	lbl_6F2
//                C--
//                A = mem[0x132E]
//                CPI	0x0F
//                JNZ	lbl_708
//                DE = 0x10E0
//                mem[0x1322] = A = 0x0F
//                JMP	lbl_71B
//           @lbl_756:
//                A = mem[0x1323]
//                A--
//                JNZ	lbl_72f
//                JMP	lbl_73c
//                HL = 0x10EF
//                DE = 0x10FF
//           lbl_766:
//                mem[DE] = A = M
//                HL--
//                DE--
//                A = mem[0x1320]
//                A--
//                CMP	L
//                JNZ	lbl_766
//                RET
//           }
//
//           proc prc773() {
//                B = 0x10
//           loop:
//                mem[DE] = A = mem[HL]
//                HL++
//                DE++
//                B--
//                JNZ	loop
//                RET
//           }
//
//           proc prc77E() {
//                C = mem[HL++]
//                CALL	0x078A
//                B--
//                JNZ	prc77E
//                RET
//           }
//
//           proc prc788() {      ; printSpaceChar
//                C = 0x20
//                saveregs (HL, DE) {
//                        CALL	biosPrintChar	; Вывод символа C на экран
//                }
//                RET
//           }
//
//           proc prc792() {
//                BC = 0x0520
//                saveregs (HL, DE) {
//                    CALL	0x056F
//                }
//                RET
//           }
//
//            proc prc79D() {
//                BC = 0x1020
//            next:
//                M = C
//                HL++
//                B--
//                JNZ	next
//                call    biosPrintMessage(0x042F)
//                CALL	0x0633
//                call	biosPrintMessage(0x0443)
//            readKey:
//                CALL	biosReadKeyboard
//                C = A
//                A -= 0x30
//                JM      readKey
//                CPI	0x0A
//                JP	readKey
//                mem[0x1338] = A
//                call    prc038c
//                HL = 0
//                SHLD	0x1315
//                mem[0x1314] = A = 0
//                mem[0x1317] = A = 4
//                CALL	prc03B9
//                CALL	0x0334
//                mem[0x1300] = A
//                A = mem[0x1338]
//                HL = 0x7C88
//                A += 0x30
//                M = A
//                HL = 0x131A
//                E = A = mem[0x1314]
//                D = 0
//                CALL	0x05BB
//                HL = 0x131A
//                DE = 0x7D24
//                CALL	0x0628
//                DE = 0x1339
//                HL = mem[0x1315]
//                XCHG
//                CALL	0x05BB
//                HL = 0x1339
//                DE = 0x7BEC
//                CALL	0x0628
//                mem[0x1301] = A = mem[0x1300]
//                CALL	0x0334
//                mem[0x1300] = A
//                A = 0x5A
//                HL = 0x1338
//                SUB	M
//                SUB	M
//                SUB	M
//                SUB	M
//                SUB	M
//                SUB	M
//                mem[0x1310] = A
//                A = 0x50
//                SUB	M
//                SUB	M
//                mem[0x130F] = A
//                JMP	0x0100
//            }
//
//        """.trimIndent()
//        val r = compileCode(code)
//        var c = 0
//        r[c++] shouldBe "init:"
//        r[c++] shouldBe "lxi SP, 0x75FF"
//        r[c++] shouldBe "lxi DE, 0x3F6"
//        r[c++] shouldBe "call prc0350"
//        r[c++] shouldBe "xra A"
//        r[c++] shouldBe "sta 0x132E"
//        r[c++] shouldBe "lxi BC, 0x100A"
//        r[c++] shouldBe "call prc56F"
//        r[c++] shouldBe "lxi HL, 0x100"
//        r[c++] shouldBe "shld 0x1305"
//        r[c++] shouldBe "lxi HL, 0x1330"
//        r[c++] shouldBe "jmp prc79D"
//        for (cmd in r) println(cmd)
//    }


    @Test fun testDecomiled() {
        val code = """
            extern proc biosReadKeyboard() absolute 0xF803
            extern proc biosPrintChar(chr: C) absolute 0xF809
            extern proc biosPrintMessage(msg: HL) absolute 0xF818
            extern proc biosCheckKeyboard() absolute 0xF81B	; Ввод кода нажатой клавиши (A=0FFH - не нажата, A=0FEH - РУС/ЛАТ, ИНАЧЕ - код клавиши)
            extern proc biosWarmBoot() absolute 0xF86C            
            
proc start() {
	SP = 0x75FF
	DE = 0x03F6
	CALL	proc_0350
	A = 0
	mem[0x132E] = A
	BC = 0x100A
	CALL	proc_056F
	HL = 0x0100
	mem[0x1305] = HL
@proc_0019:
	HL = 0x1330
	JMP	proc_079D
}

byte[] {
	0x00, 0x4E, 0x00, 0x50, 0x00, 0x02, 0x00, 0x00, 0x00, 0x4E, 0x00, 0x50, 0x00, 0x02, 0x00, 0x00, 0x00, 0x4E, 0x00, 0x50, 0x00, 0x02, 0x00, 0x00, 0x00, 0x4E, 0x00, 0x50, 0x00, 0x02, 0x00, 0x00, 
	0x00, 0x04, 0x00, 0xFE, 0xFF, 0x02, 0x00, 0x00, 0x00, 0x9C, 0x00, 0xB2, 0xFF, 0x4E, 0x00, 0x00, 0x00, 0x04, 0x00, 0xFE, 0xFF, 0x02, 0x00, 0x00, 0x00, 0x9C, 0x00, 0xB2, 0xFF, 0x4E, 0x00, 0x00, 
	0x00, 0x4C, 0x00, 0x4E, 0x00, 0x02, 0x00, 0x00, 0x00, 0x50, 0x00, 0x02, 0x00, 0xB2, 0xFF, 0x00, 0x00, 0x4C, 0x00, 0x4E, 0x00, 0x02, 0x00, 0x00, 0x00, 0x50, 0x00, 0x02, 0x00, 0xB2, 0xFF, 0x00, 
	0x00, 0x50, 0x00, 0x4E, 0x00, 0xFE, 0xFF, 0x00, 0x00, 0x4C, 0x00, 0xFE, 0xFF, 0xB2, 0xFF, 0x00, 0x00, 0x50, 0x00, 0x4E, 0x00, 0xFE, 0xFF, 0x00, 0x00, 0x4C, 0x00, 0xFE, 0xFF, 0xB2, 0xFF, 0x00, 
	0x00, 0x4C, 0x00, 0x02, 0x00, 0xFE, 0xFF, 0x00, 0x00, 0x50, 0x00, 0x4E, 0x00, 0xB2, 0xFF, 0x00, 0x00, 0xFE, 0xFF, 0x02, 0x00, 0xB4, 0xFF, 0x00, 0x00, 0x4E, 0x00, 0xB0, 0xFF, 0xB2, 0xFF, 0x00, 
	0x00, 0x50, 0x00, 0xFE, 0xFF, 0x02, 0x00, 0x00, 0x00, 0x4E, 0x00, 0xB4, 0xFF, 0xB2, 0xFF, 0x00, 0x00, 0x02, 0x00, 0xFE, 0xFF, 0xB0, 0xFF, 0x00, 0x00, 0x4C, 0x00, 0x4E, 0x00, 0xB2, 0xFF, 0x00, 
	0x00, 0x4E, 0x00, 0xFE, 0xFF, 0x02, 0x00, 0x00, 0x00, 0x4E, 0x00, 0x02, 0x00, 0xB2, 0xFF, 0x00, 0x00, 0xFE, 0xFF, 0x02, 0x00, 0xB2, 0xFF, 0x00, 0x00, 0x4E, 0x00, 0xFE, 0xFF, 0xB2, 0xFF, 0x00, 
	0x00 
}
proc proc_0100() {
	HL = 0x7824
	mem[0x1307] = HL
	mem[0x1309] = HL
	XCHG
	A = 0x14
	mem[0x1319] = A
	A = 0
	mem[0x1311] = A
	mem[0x1312] = A
	CALL	proc_021F
	CALL	proc_024A
	mem[0x132F] = A
	CALL	proc_021F
	CALL	proc_025E
	A = mem[0x132F]
	CPI	0x2E
	JNZ	proc_032E
	HL = 0x7ACE
	BC = 0x0802
lbl_0133:
	M = 0x20
	HL++
	B--
	JNZ	lbl_0133
	B = 0x08
	HL = 0x7B1C
	C--
	JNZ	lbl_0133
	A = mem[0x1317]
	A |= A
	JNZ	lbl_0153
	DE = 0x7AD0
	CALL	proc_0228
	CALL	proc_025E
lbl_0153:
	A = mem[0x130F]
	mem[0x130D] = A
	A = mem[0x1310]
	mem[0x130E] = A
	A = 0
	mem[0x130C] = A
lbl_0163:
	HL = 0x130D
	A++
	CMP	M
	CZ	proc_017B
	HL = 0x130E
	CMP	M
	CZ	proc_02BF
	C = 0xFF
lbl_0174:
	C--
	JNZ	lbl_0174
	JMP	lbl_0163
}

proc proc_017B() {
	PUSH	PSW
	M++
	A = 0xFF
	mem[0x130B] = A
	CALL	biosCheckKeyboard
	CPI	0xFF
	JZ	proc_02BD
	B = A
	HL = 0x130D
	A = mem[0x130F]
	A += M
	M = A
	A = B
	CPI	0x08
	JZ	proc_01E1
	CPI	0x18
	JZ	proc_01EC
	CPI	0x1A
	JZ	proc_01F7
	CPI	0x19
	JZ	proc_0207
	HL = mem[0x1307]
	XCHG
	CALL	proc_021F
	CALL	proc_027F
lbl_01B2:
	CALL	proc_021F
	HL = 0x004E
	HL += DE
	XCHG
	A = mem[0x1319]
	A--
	mem[0x1319] = A
	CALL	proc_024A
	CPI	0x2E
	JZ	lbl_01B2
	CALL	proc_021F
	HL = 0xFFB2
	HL += DE
	mem[0x1307] = HL
	XCHG
	A = mem[0x1319]
	A++
	mem[0x1319] = A
	CALL	proc_025E
	JMP	proc_02BD
}

proc proc_01E1() {
	HL = mem[0x1307]
	HL--
	HL--
	mem[0x1309] = HL
	JMP	proc_028C
}

proc proc_01EC() {
	HL = mem[0x1307]
	HL++
	HL++
	mem[0x1309] = HL
	JMP	proc_028C
}

proc proc_01F7() {
	A = mem[0x1311]
	A++
	CPI	0x04
	JM	lbl_0201
	A = 0
lbl_0201:
	mem[0x1312] = A
	JMP	proc_028C
}

proc proc_0207() {
	C = 0x07
	CALL	biosPrintChar
	A = mem[0x1317]
	A -= 0x04
	mem[0x1317] = A
	JZ	proc_02BD
	A = 0x04
	mem[0x1317] = A
	JMP	proc_02BD
}

proc proc_021F() {
	A = mem[0x1311]
	mem[0x1313] = A
	JMP	proc_0234
}

proc proc_0228() {
	A = mem[0x1300]
	JMP	proc_0237
}

proc proc_022E() {
	A = mem[0x1312]
	mem[0x1313] = A
@proc_0234:
	A = mem[0x1301]
@proc_0237:
	RRC
	RRC
	RRC
	A &= 0xE0
	C = A
	A = mem[0x1313]
	RLC
	RLC
	RLC
	A &= 0x1F
	A += C
	C = A
	B = 0
	RET
}

proc proc_024A() {
	A = mem[BC]
	L = A
	C++
	A = mem[BC]
	H = A
	C++
	HL += DE
	A = M
	CPI	0x2E
	RNZ
	A = C
	A &= 0x07
	JNZ	proc_024A
	A = 0x2E
	RET
}

proc proc_025E() {
	A = 0x17
	mem[0x1302] = A
	A = 0x11
	mem[0x1303] = A
@proc_0268:
	A = mem[BC]
	L = A
	C++
	A = mem[BC]
	H = A
	C++
	HL += DE
	A = mem[0x1302]
	M = A
	HL++
	A = mem[0x1303]
	M = A
	A = C
	A &= 0x07
	JNZ	proc_0268
	RET
}

proc proc_027F() {
	A = 0x2E
	mem[0x1302] = A
	A = 0x20
	mem[0x1303] = A
	JMP	proc_0268
}

proc proc_028C() {
	HL = mem[0x1307]
	XCHG
	CALL	proc_021F
	CALL	proc_027F
	HL = mem[0x1309]
	XCHG
	CALL	proc_022E
	CALL	proc_024A
	CPI	0x2E
	JNZ	lbl_02B3
	XCHG
	mem[0x1307] = HL
	A = 0
	mem[0x130B] = A
	A = mem[0x1312]
	mem[0x1311] = A
lbl_02B3:
	CALL	proc_021F
	HL = mem[0x1307]
	XCHG
	CALL	proc_025E
@proc_02BD:
	POP	PSW
	RET
}

proc proc_02BF() {
	PUSH	PSW
	A = mem[0x1310]
	A += M
	mem[0x130E] = A
	HL = mem[0x1307]
	XCHG
	HL = 0x004E
	HL += DE
	mem[0x1309] = HL
	CALL	proc_021F
	CALL	proc_027F
	HL = mem[0x1309]
	XCHG
	A = C
	A -= 0x08
	C = A
	CALL	proc_024A
	CPI	0x2E
	JNZ	proc_02FB
	XCHG
	mem[0x1307] = HL
	A = mem[0x1319]
	A--
	mem[0x1319] = A
@proc_02F3:
	CALL	proc_021F
	CALL	proc_025E
	POP	PSW
	RET
}

proc proc_02FB() {
	A = mem[0x130B]
	A |= A
	JNZ	proc_0316
@proc_0302:
	HL = mem[0x1307]
	XCHG
	JMP	proc_02F3
}

byte[] {
	0x2A, 0x07, 0x13, 0xEB, 0xCD, 0x1F, 0x02, 0xCD, 0x7F, 0x02, 0xC3, 0xB3, 0x02 
}
proc proc_0316() {
	A = mem[0x130C]
	A |= A
	JNZ	proc_0324
	A++
	mem[0x130C] = A
	JMP	proc_0302
}

proc proc_0324() {
	HL = mem[0x1307]
	XCHG
	CALL	proc_021F
	CALL	proc_025E
@proc_032E:
	SP = 0x75FF
	JMP	proc_0836
}

proc proc_0334() {
	HL = mem[0x1305]
	C = 0x10
lbl_0339:
	A = H
	HL += HL
	A &= 0x60
	JPE	lbl_0341
	HL++
lbl_0341:
	C--
	JNZ	lbl_0339
	mem[0x1305] = HL
	A = H
	A += L
	A &= 0x07
	JZ	proc_0334
	RET
}

proc proc_0350() {
	C = 0x1F
	PUSH	DE
	CALL	biosPrintChar
	POP	DE
	HL = 0x7997
	B = 0x07
	A = mem[DE]
	DE++
	mem[0x1318] = A
lbl_0361:
	PUSH	BC
	BC = 0x0808
lbl_0365:
	A = mem[DE]
	DE++
	PUSH	DE
	E = A
	A = mem[0x1318]
	D = A
	A = E
lbl_036E:
	RAL
	JNC	lbl_0373
	M = D
lbl_0373:
	HL++
	C--
	JNZ	lbl_036E
	C = 0x08
	POP	DE
	B--
	JNZ	lbl_0365
	POP	BC
	B--
	RZ
	C = 0x0E
lbl_0384:
	HL++
	C--
	JNZ	lbl_0384
	JMP	lbl_0361
}

proc proc_038C() {
	C = 0x1F
	CALL	biosPrintChar
	D = 0x15
lbl_0393:
	PUSH	DE
	CALL	proc_0577
	BC = 0x0B18
	CALL	proc_056F
	POP	DE
	D--
	JZ	proc_03AD
	PUSH	DE
	HL = 0x045D
	CALL	biosPrintMessage
	POP	DE
	JMP	lbl_0393
}

proc proc_03AD() {
	HL = 0x0474
	CALL	biosPrintMessage
	C = 0x0C
	CALL	biosPrintChar
	RET
}

proc proc_03B9() {
	HL = 0x7885
	DE = 0x048B
	CALL	proc_03ED
	HL = 0x791E
	CALL	proc_03ED
	HL = 0x796C
	CALL	proc_03ED
	HL = 0x79BA
	CALL	proc_03ED
	HL = 0x7A08
	CALL	proc_03ED
	HL = 0x7BDC
	CALL	proc_03ED
	HL = 0x7C78
	CALL	proc_03ED
	HL = 0x7D14
	CALL	proc_03ED
	RET
}

proc proc_03ED() {
	A = mem[DE]
	DE++
	A |= A
	RZ
	M = A
	HL++
	JMP	proc_03ED
}

byte[] {
	0x40, 0x00, 0x78, 0xFE, 0x18, 0x42, 0x18, 0x42, 0x00, 0x00, 0x84, 0x10, 0x24, 0x44, 0x24, 0x42, 0x00, 0x00, 0x80, 0x10, 0x42, 0x48, 0x42, 0x42, 0x00, 0x00, 0x80, 0x10, 0x42, 0x70, 0x42, 0x7E, 
	0x00, 0x00, 0x80, 0x10, 0x7E, 0x48, 0x7E, 0x42, 0x00, 0x00, 0x84, 0x10, 0x42, 0x44, 0x42, 0x42, 0x00, 0x00, 0x78, 0x10, 0x42, 0x42, 0x42, 0x42, 0x00, 0x0D, 0x0A, 0x0A, 0x20, 0x20, 0x20, 0x20, 
	0x20, 0x77, 0x61, 0x7B, 0x65, 0x20, 0x69, 0x6D, 0x71, 0x20, 0x3F, 0x20, 0x00, 0x0D, 0x0A, 0x0A, 0x20, 0x20, 0x20, 0x20, 0x20, 0x77, 0x61, 0x7B, 0x20, 0x72, 0x61, 0x6E, 0x67, 0x20, 0x28, 0x30, 
	0x2D, 0x39, 0x29, 0x20, 0x3F, 0x20, 0x00, 0x11, 0x2E, 0x20, 0x2E, 0x20, 0x2E, 0x20, 0x2E, 0x20, 0x2E, 0x20, 0x2E, 0x20, 0x2E, 0x20, 0x2E, 0x20, 0x2E, 0x20, 0x2E, 0x20, 0x11, 0x00, 0x15, 0x14, 
	0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x11, 0x00, 0x75, 0x70, 0x72, 0x61, 0x77, 0x6C, 0x65, 0x6E, 0x69, 0x65, 0x00, 
	0x1D, 0x20, 0x2D, 0x20, 0x73, 0x64, 0x77, 0x69, 0x67, 0x20, 0x77, 0x6C, 0x65, 0x77, 0x6F, 0x00, 0x0E, 0x20, 0x2D, 0x20, 0x73, 0x64, 0x77, 0x69, 0x67, 0x20, 0x77, 0x70, 0x72, 0x61, 0x77, 0x6F, 
	0x00, 0x0F, 0x20, 0x2D, 0x20, 0x70, 0x6F, 0x77, 0x6F, 0x72, 0x6F, 0x74, 0x00, 0x0B, 0x20, 0x2D, 0x20, 0x70, 0x6F, 0x6B, 0x61, 0x7A, 0x61, 0x74, 0x78, 0x20, 0x73, 0x6C, 0x65, 0x64, 0x75, 0x60, 
	0x7D, 0x60, 0x60, 0x00, 0x77, 0x73, 0x65, 0x67, 0x6F, 0x20, 0x6F, 0x7E, 0x6B, 0x6F, 0x77, 0x20, 0x20, 0x2D, 0x00, 0x72, 0x61, 0x6E, 0x67, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 
	0x2D, 0x00, 0x70, 0x6F, 0x6C, 0x6E, 0x79, 0x68, 0x20, 0x73, 0x74, 0x72, 0x6F, 0x6B, 0x20, 0x2D, 0x00 
}
proc proc_0507() {
	HL = 0x7DE6
	mem[0x132A] = HL
	B = 0x14
@proc_050F:
	C = 0x0A
lbl_0511:
	A = M
	CPI	0x2E
	JZ	proc_0526
	HL++
	HL++
	C--
	JNZ	lbl_0511
	CALL	proc_0535
	HL = mem[0x132A]
	JMP	proc_050F
}

proc proc_0526() {
	DE = 0xFFB2
	HL = mem[0x132A]
	HL += DE
	mem[0x132A] = HL
	B--
	JNZ	proc_050F
	RET
}

proc proc_0535() {
	PUSH	BC
	A = mem[0x1314]
	A++
	mem[0x1314] = A
	HL = mem[0x132A]
	mem[0x132C] = HL
	B--
lbl_0544:
	C = 0x14
	HL = mem[0x132C]
	XCHG
	HL = 0xFFB2
	HL += DE
	mem[0x132C] = HL
lbl_0551:
	A = M
	mem[DE] = A
	DE++
	HL++
	C--
	JNZ	lbl_0551
	B--
	JNZ	lbl_0544
	HL = mem[0x132C]
	DE = 0x2E20
	C = 0x0A
lbl_0565:
	M = D
	HL++
	M = E
	HL++
	C--
	JNZ	lbl_0565
	POP	BC
	RET
}

proc proc_056F() {
	CALL	biosPrintChar
	B--
	JNZ	proc_056F
	RET
}

proc proc_0577() {
	C = 0x0A
	CALL	biosPrintChar
	C = 0x0D
	JMP	biosPrintChar
}

proc proc_0581() {
	A = mem[0x1314]
	A++
	B = A
	A = mem[0x1338]
	A++
	C = A
	A = 0
lbl_058C:
	A += 0x0A
	C--
	JNZ	lbl_058C
	CMP	B
	RP
	A = mem[0x1338]
	CPI	0x09
	RZ
	A++
	mem[0x1338] = A
	C = 0x07
	JMP	biosPrintChar
}

proc proc_05A3() {
	A = mem[0x1319]
	HL = 0x1338
	A += M
	A += M
	A += M
	HL = 0x1317
	A += M
	E = A
	D = 0
	HL = mem[0x1315]
	HL += DE
	mem[0x1315] = HL
	RET
}

proc proc_05BB() {
	mem[0x1327] = HL
	XCHG
	A = 0
	mem[0x1326] = A
	A = H
	mem[0x1329] = A
lbl_05C7:
	E = 0
	B = 0x10
	A |= A
lbl_05CC:
	A = L
	RAL
	L = A
	A = H
	RAL
	H = A
	A = E
	RAL
	E = A
	A -= 0x0A
	CMC
	JNC	lbl_05DC
	E = A
lbl_05DC:
	B--
	JNZ	lbl_05CC
	RAL
	A &= 0x01
	HL += HL
	A |= L
	L = A
	A = E
	A += 0x30
	CALL	proc_05FE
	A = H
	A |= L
	JNZ	lbl_05C7
	A = mem[0x1329]
	A |= A
	JP	lbl_05FD
	A = 0x2D
	CALL	proc_05FE
lbl_05FD:
	RET
}

proc proc_05FE() {
	PUSH	HL
	C = A
	HL = mem[0x1327]
	D = H
	E = L
	HL++
	mem[0x1327] = HL
	A = mem[0x1326]
	A |= A
	JZ	lbl_061B
	B = A
lbl_0611:
	XCHG
	A = M
	XCHG
	M = A
	HL--
	DE--
	B--
	JNZ	lbl_0611
lbl_061B:
	A = C
	M = A
	A = mem[0x1326]
	A++
	mem[0x1326] = A
	HL--
	M = A
	POP	HL
	RET
}

proc proc_0628() {
	B = M
	HL++
lbl_062A:
	A = M
	mem[DE] = A
	HL++
	DE++
	B--
	JNZ	lbl_062A
	RET
}

proc proc_0633() {
	HL = 0x1330
	B = 0x08
@proc_0638:
	PUSH	HL
@proc_0639:
	CALL	biosReadKeyboard
	C = A
	CPI	0x08
	JZ	proc_0657
	CPI	0x0D
	JZ	proc_0666
	CPI	0x20
	JM	proc_0639
	CALL	biosPrintChar
	POP	HL
	M = C
	HL++
	B--
	JNZ	proc_0638
	RET
}

proc proc_0657() {
	A = B
	CPI	0x08
	JP	proc_0639
	CALL	biosPrintChar
	POP	HL
	HL--
	B++
	JMP	proc_0638
}

proc proc_0666() {
	POP	HL
lbl_0667:
	M = 0x20
	HL++
	B--
	JNZ	lbl_0667
	RET
}

proc proc_066F() {
	HL = 0x1000
	A = mem[0x132E]
	A++
	E = A
	D = 0x01
@proc_0679:
	A = D
	CPI	0x0A
	JP	proc_06D2
	C = 0x20
	A += 0x30
	B = A
@proc_0684:
	CALL	proc_078A
	C = B
	CALL	proc_078A
	C = 0x2E
	CALL	proc_078A
	CALL	proc_0788
	B = 0x08
	CALL	proc_077E
	CALL	proc_0792
	A = M
	HL++
	A += 0x30
	C = A
	CALL	proc_078A
	CALL	proc_0792
	B = 0x06
	HL++
	CALL	proc_077E
	CALL	proc_0792
	BC = 0x0308
	CALL	proc_0795
	A = mem[0x1322]
	CMP	D
	JNZ	lbl_06C4
	C = 0x2A
	CALL	proc_078A
	CALL	proc_078A
lbl_06C4:
	D++
	A = D
	CMP	E
	RP
	PUSH	HL
	PUSH	DE
	CALL	proc_0577
	POP	DE
	POP	HL
	JMP	proc_0679
}

proc proc_06D2() {
	C = 0x31
	A += 0x26
	B = A
	JMP	proc_0684
}

proc proc_06DA() {
	A = mem[0x132E]
	A |= A
	JNZ	proc_06EE
	A++
	mem[0x132E] = A
	DE = 0x1000
	HL = 0x1330
	JMP	proc_0773
}

proc proc_06EE() {
	B = A
	B++
	C = 0
@proc_06F2:
	DE = 0x1339
	HL = 0x1000
	A = C
	RLC
	RLC
	RLC
	RLC
	A += 0x09
	L = A
	A = mem[DE]
	CMP	M
	JZ	proc_072E
	JM	proc_073C
@proc_0708:
	A = L
	A &= 0xF0
	L = A
	mem[0x1320] = HL
	A = C
	A++
	mem[0x1322] = A
	CALL	proc_0760
	HL = mem[0x1320]
	XCHG
@proc_071B:
	HL = 0x1330
	B = 0x10
	CALL	proc_0775
	A = mem[0x132E]
	CPI	0x0F
	RZ
	A++
	mem[0x132E] = A
	RET
}

proc proc_072E() {
	A = M
@proc_072F:
	HL++
	DE++
	mem[0x1323] = A
	A = mem[DE]
	CMP	M
	JZ	proc_0756
	JP	proc_0708
@proc_073C:
	C++
	A = B
	CMP	C
	JNZ	proc_06F2
	C--
	A = mem[0x132E]
	CPI	0x0F
	JNZ	proc_0708
	DE = 0x10E0
	A = 0x0F
	mem[0x1322] = A
	JMP	proc_071B
}

proc proc_0756() {
	A = mem[0x1323]
	A--
	JNZ	proc_072F
	JMP	proc_073C
}

proc proc_0760() {
	HL = 0x10EF
	DE = 0x10FF
lbl_0766:
	A = M
	mem[DE] = A = mem[HL]
	HL--
	DE--
	A = mem[0x1320]
	A--
	CMP	L
	JNZ	lbl_0766
	RET
}

proc proc_0773() {
	B = 0x10
@proc_0775:
	A = M
	mem[DE] = A
	HL++
	DE++
	B--
	JNZ	proc_0775
	RET
}

proc proc_077E() {
	C = M
	HL++
	CALL	proc_078A
	B--
	JNZ	proc_077E
	RET
}

proc proc_0788() {
	C = 0x20
@proc_078A:
	PUSH	HL
	PUSH	DE
	CALL	biosPrintChar
	POP	DE
	POP	HL
	RET
}

proc proc_0792() {
	BC = 0x0520
@proc_0795:
	PUSH	HL
	PUSH	DE
	CALL	proc_056F
	POP	DE
	POP	HL
	RET
}

proc proc_079D() {
	BC = 0x1020
lbl_07A0:
	M = C
	HL++
	B--
	JNZ	lbl_07A0
	HL = 0x042F
	CALL	biosPrintMessage
	CALL	proc_0633
	HL = 0x0443
	CALL	biosPrintMessage
lbl_07B5:
	CALL	biosReadKeyboard
	C = A
	A -= 0x30
	JM	lbl_07B5
	CPI	0x0A
	JP	lbl_07B5
	mem[0x1338] = A
	CALL	proc_038C
	HL = 0
	mem[0x1315] = HL
	A = 0
	mem[0x1314] = A
	A = 0x04
	mem[0x1317] = A
	CALL	proc_03B9
	CALL	proc_0334
	mem[0x1300] = A
@proc_07E1:
	A = mem[0x1338]
	HL = 0x7C88
	A += 0x30
	M = A
	HL = 0x131A
	A = mem[0x1314]
	E = A
	D = 0
	CALL	proc_05BB
	HL = 0x131A
	DE = 0x7D24
	CALL	proc_0628
	DE = 0x1339
	HL = mem[0x1315]
	XCHG
	CALL	proc_05BB
	HL = 0x1339
	DE = 0x7BEC
	CALL	proc_0628
	A = mem[0x1300]
	mem[0x1301] = A
	CALL	proc_0334
	mem[0x1300] = A
	A = 0x5A
	HL = 0x1338
	A -= M
	A -= M
	A -= M
	A -= M
	A -= M
	A -= M
	mem[0x1310] = A
	A = 0x50
	A -= M
	A -= M
	mem[0x130F] = A
	JMP	proc_0100
}

proc proc_0836() {
	CALL	proc_0507
	CALL	proc_0581
	CALL	proc_05A3
	A = mem[0x132F]
	CPI	0x2E
	JZ	proc_07E1
	CALL	proc_06DA
	HL = 0x0882
	CALL	biosPrintMessage
	CALL	proc_0577
	CALL	proc_066F
	HL = 0x086E
	CALL	biosPrintMessage
lbl_085C:
	CALL	biosReadKeyboard
	A &= 0x5F
	CPI	0x44
	JZ	proc_0019
	CPI	0x4E
	JZ	biosWarmBoot()
	JMP	lbl_085C
}

byte[] {
	0x1B, 0x59, 0x34, 0x25, 0x65, 0x7D, 0x65, 0x20, 0x72, 0x61, 0x7A, 0x20, 0x28, 0x44, 0x2F, 0x4E, 0x29, 0x20, 0x3F, 0x00, 0x1F, 0x0A, 0x0A, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x69, 0x6D, 0x71, 
	0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x72, 0x61, 0x6E, 0x67, 0x20, 0x20, 0x20, 0x20, 0x6F, 0x7E, 0x6B, 0x69, 0x0D, 0x0A, 0x00, 0xCA, 0x62, 0x03, 0x79, 0xBA, 0xCA, 0x0B, 0x03, 0x78, 0xA3, 0xBA, 
	0xC2, 0x36, 0x03, 0x3E, 0x23, 0x32, 0x5A, 0x03, 0xC3, 0x55, 0x03, 0xBB, 0xC2, 0x42, 0x03, 0x3E, 0x2B, 0x32, 0x5A, 0x03, 0xC3, 0x55, 0x03, 0xFE, 0x40, 0xC2, 0x4D, 0x03, 0x11, 0xB2, 0xFF, 0xC3, 
	0x50, 0x03, 0x11, 0x4E, 0x00, 0x3E, 0x19, 0x32, 0x5A, 0x03, 0x78, 0xE6, 0x3F, 0x47, 0x71, 0x23, 0x05, 0xC2, 0xE0, 0x01, 0xC3, 0x0B, 0x03, 0xC9, 0x00, 0x00, 0x00, 0xC9, 0x00, 0x00, 0x00, 0x00, 
	0x00, 0x00, 0x00, 0x02, 0x03, 0x03, 0x2E, 0x14, 0x02, 0x20, 0x81, 0x03, 0x01, 0x05, 0x01, 0x10, 0x81, 0x02
}
           
        """.trimIndent()
        compileRkr(code, 0x0000, SystemFileSystem.sink(Path("/Users/trol/tetris6-decompiled.rkr")).buffered())
    }

}