package ru.trolsoft.therat.avr.compiler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.trolsoft.compiler.compiler.CompilerException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvrGccOutputTest {

/*
    @Test fun testOutInstruction() {
        val r = compileM8("""
            out PORTD, r1
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("out _SFR_IO_ADDR(18), R1", r.last())
    }

    @Test fun testInInstruction() {
        val r = compileM8("""
            in r1, PIND
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("in R1, _SFR_IO_ADDR(16)", r.last())
    }

    @Test fun testSbiInstruction() {
        val r = compileM8("""
            sbi PORTD, 3
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("sbi _SFR_IO_ADDR(18), 3", r.last())
    }

    @Test fun testCbiInstruction() {
        val r = compileM8("""
            cbi PORTD, 3
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("cbi _SFR_IO_ADDR(18), 3", r.last())
    }

    @Test fun testSbicInstruction() {
        val r = compileM8("""
            sbic EECR, EEWE
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("sbic _SFR_IO_ADDR(28), 1", r.last())
    }

    @Test fun testSbisInstruction() {
        val r = compileM8("""
            sbis EECR, EEWE
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("sbis _SFR_IO_ADDR(28), 1", r.last())
    }
*/
    @Test fun testMovwInstruction() {
        val r = compileM8("""
            movw X, Y
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("movw R26, R28", r.last())
    }

    @Test fun testAdiwInstruction() {
        val r = compileM8("""
            adiw Z, 10
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("adiw R30, 10", r.last())
    }

    @Test fun testSbiwInstruction() {
        val r = compileM8("""
            sbiw Z, 10
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("sbiw R30, 10", r.last())
    }

    @Test fun testMovwOperation() {
        val r = compileM8("""
            X = Y
        """.trimIndent())
        assertEquals(1, r.size)
        assertEquals("movw R26, R28", r.last())
    }

    @Test fun testWriteExternalByteVar() {
        val r = compileM8("""
            b = r10
        """.trimIndent(), "extern var b: byte")
        assertEquals(2, r.size)
        assertEquals(".extern b", r[0])
        assertEquals("sts b, R10", r.last())
    }

    @Test fun testWriteExternalWordVar() {
        val r = compileM8("""
            w = r10.r11
        """.trimIndent(), "extern var w: word")

        assertEquals(3, r.size)
        assertEquals(".extern w", r[0])
        assertEquals("sts w+1, R10", r[1])
        assertEquals("sts w, R11", r[2])
    }

    @Test fun testReadExternalByteVar() {
        val r = compileM8("""
            r10 = b
        """.trimIndent(), "extern var b: byte")
        assertEquals(2, r.size)
        assertEquals(".extern b", r[0])
        assertEquals("lds R10, b", r.last())
    }

    @Test fun testReadExternalWordVar() {
        val r = compileM8("""
            r10.r11 = w
        """.trimIndent(), "extern var w: word")

        assertEquals(3, r.size)
        assertEquals(".extern w", r[0])
        assertEquals("lds R10, w+1", r[1])
        assertEquals("lds R11, w", r[2])
    }

    @Test fun testLocalLabel() {
        val r = compileM8("""
            rjmp lbl
            lbl:
        """.trimIndent())

        assertEquals(2, r.size)
        assertEquals("rjmp _main_lbl", r[0])
        assertEquals("_main_lbl:", r[1])
    }

    @Test fun testIfBit() {
        val r = compileM8("""
            if (!r24[7]) {
                nop
                sei
            }
        """.trimIndent())

        assertEquals(5, r.size)
        assertEquals("sbrc R24, 7", r[0])
        assertEquals("rjmp _main_if_end_4", r[1])
        assertEquals("nop", r[2])
        assertEquals("sei", r[3])
        assertEquals("_main_if_end_4:", r[4])
    }

//    @Test fun testExternalProcAccess() {
//        val r = compileM8("""
//            Z = myExtProc
//        """.trimIndent(), "extern proc myExtProc()")
//
//    }

    @Test fun testAssignPairToProcPtr() {
        val res = compileM8("""
            Z = main
        """.trimIndent())
        assertEquals(2, res.size)
        assertEquals("ldi R31, pm_hi8(main)", res[0])
        assertEquals("ldi R30, pm_lo8(main)", res[1])
    }

    @Test fun testExternalArrayWriteFirst() {
        val res = compileM8("""
            buf[0] = r24
        """.trimIndent(), "extern var buf: ptr")
        assertEquals(2, res.size)
        assertEquals(".extern buf", res[0])
        assertEquals("sts buf, R24", res[1])
    }

    @Test fun testExternalArrayWrite() {
        val res = compileM8("""
            buf[10] = r24
        """.trimIndent(), "extern var buf: ptr")
        assertEquals(2, res.size)
        assertEquals(".extern buf", res[0])
        assertEquals("sts buf+10, R24", res[1])
    }

    @Test fun testAssignExternalBufPlusWord() {
        val res = compileM8("""
            Z = buf + 0x100
        """.trimIndent(), "extern var buf: ptr")
        assertEquals(3, res.size)
        assertEquals(".extern buf", res[0])
        assertEquals("ldi R31, hi8(buf+256)", res[1])
        assertEquals("ldi R30, lo8(buf+256)", res[2])
    }

    @Test fun testTwoProc() {
        val res = compile("""
            #define CPU "mega8"
            
            proc readByte() {
            	r24 = SPDR
	            ret
            }

            proc readBlock(buf: r25.r24) {
	            Y = buf
	            loop (r25 = 0xff) {
		            rcall readByte()
                    mem[Y++] = r24
            	}
                ret
        }
        """.trimIndent())
        assertTrue(res.contains(".global readByte"))
        assertTrue(res.contains(".global readBlock"))
        assertTrue(res.contains("readBlock:"))
        assertTrue(res.contains("readByte:"))
        assertTrue(res.contains("rcall readByte"))

//        assertEquals(3, res.size)
//        assertEquals(".extern buf", res[0])
//        assertEquals("ldi R31, hi8(buf+256)", res[1])
//        assertEquals("ldi R30, lo8(buf+256)", res[2])
    }

    @Test
    fun testLoopArgRegPair2() {
        val r = compile("""
            #define CPU "mega8"
            proc myProc(size: r25.r24, buf: r23.r22) {
                loop(size) {
                    nop
                }
            }
        """.trimIndent())
        assertEquals(8, r.size)
        assertEquals("#include <avr/io.h>", r[0])
        assertEquals(".global myProc", r[1])
        assertEquals("myProc:", r[2])
        assertEquals("_myProc_loop_3:", r[3])
        assertEquals("nop", r[4])
        assertEquals("sbiw R24, 1", r[5])
        assertEquals("brne _myProc_loop_3", r[6])
        assertEquals("_myProc_loopEnd_3:", r[7])
    }

    @Test
    fun testAddExternPtrToZ() {
        val r = compile("""
            #define CPU "mega8"
            extern var some_array : ptr
            proc myProc() {
                Z += some_array
            }
        """.trimIndent())
        assertEquals("#include <avr/io.h>", r[0])
        assertEquals(".extern some_array", r[1])
        assertEquals(".global myProc", r[2])
        assertEquals("myProc:", r[3])
        assertEquals("subi R30, lo8(-(some_array))", r[4])
        assertEquals("sbci R31, hi8(-(some_array))", r[5])
    }

    @Test
    fun testAddExternPtrToZAssign() {
        val r = compile("""
            #define CPU "mega8"
            extern var some_array : ptr
            proc myProc() {
                Z = Z + some_array
            }
        """.trimIndent())
        assertEquals("#include <avr/io.h>", r[0])
        assertEquals(".extern some_array", r[1])
        assertEquals(".global myProc", r[2])
        assertEquals("myProc:", r[3])
        assertEquals("subi R30, lo8(-(some_array))", r[4])
        assertEquals("sbci R31, hi8(-(some_array))", r[5])
    }

    @Test
    fun testUnknownIdentifierError() {
        val exception = assertThrows<CompilerException> {
            val r = compile("""
                #define CPU "mega8"
                proc myProc() {
                    ZH = invalid_identifier
                }
            """.trimIndent())
        }
        assertEquals("Unknown identifier: invalid_identifier", exception.message)
    }

    /*
proc readBlock(size: r25.r24, buf: r23.r22) {
;	r26 = 0xff
;	Z = buf

	loop (size) {
;		SPDR = r26
;wait:
;		if (!SPSR->SPIF) goto wait
;		SPSR->SPIF = 0
;		mem[Z++] = r24 = SPDR
	}
;	ret
}
     */
    //extern var sdcard_buf_1, sdcard_buf_2: ptr
    //Z = sdcard_buf_1 + 0x100

//    @Test fun testAssignPairToNextProcPtr() {
//        val res = compile("""
//            #define CPU "mega8"
//
//            proc firstProc() {
//                Z = nextProc
//            }
//            proc nextProc() {
//
//            }
//        """.trimIndent())
//        assertEquals(2, res.size)
//        assertEquals("ldi R31, pm_hi8(main)", res[0])
//        assertEquals("ldi R30, pm_lo8(main)", res[1])
//    }

/*
    @Test fun testZxFio() {
        val res = compile("""
            #define CPU "atmega8"
extern proc readNext()
extern proc FioNextByte()
extern proc FioPutByte(b: r24)

extern var fio_buf_part_pos: byte
extern var fio_get_next_byte_handler: word
extern var fio_put_next_byte_handler: word
extern var sdcard_buf_1, sdcard_buf_2: ptr
extern var fio_readyFlags: byte
extern var fio_error_cnt: word
extern var fio_error_mask: byte

proc FioReadWord() {
	call		readNext

	call		FioNextByte
	push		r24
	call		FioNextByte
	r25 = r24
	pop		r24
	ret							; r25:r24
}


proc FioReadDword() {
	call		readNext

	call		FioNextByte
	push		r24
	call		FioNextByte
	push		r24
	call		FioNextByte
	push		r24
	call		FioNextByte
	r25 = r24
	pop		r24 r23 r22
	ret							; r25:r24:r23:r22
}


proc FioPutWord(w: r25.r24) {
	push		r25
	call		FioPutByte
	pop		r24
	jmp		FioPutByte
}

proc FioPutDword(dw: r25.r24.r23.r22) {
	push		r25 r24
	call		FioPutByte(r22)
	call		FioPutByte(r23)
	pop		r24
	call		FioPutByte
	pop		r24
	jmp		FioPutByte
}


proc fioNextFromBuf1Start() {
	Z = sdcard_buf_1							; ldi !!!!
	r25 = fio_buf_part_pos
	Z += r1.r25

	r24 = mem[Z]
	fio_buf_part_pos = r25++

	if (!SREG->Z) ret
	Z = fioNextFromBuf1End					; ldi !!!
	fio_get_next_byte_handler = Z
	ret
}

proc fioNextFromBuf2Start() {
	Z = sdcard_buf_2
	r25 = fio_buf_part_pos
	Z += r1.r25

	r24 = mem[Z]

	fio_buf_part_pos = r25++
	if (!SREG->Z) ret

	Z = fioNextFromBuf2End
	fio_get_next_byte_handler = Z
	ret
}

proc fioNextFromBuf1End() {
	Z = sdcard_buf_1 + 0x100
	r25 = fio_buf_part_pos
	Z += r1.r25

	r24 = mem[Z]
	fio_buf_part_pos = r25++
	if (!SREG->Z) ret

   fio_readyFlags = r25 = fio_readyFlags & 0xFE

	fio_get_next_byte_handler = Z = fioNextFromBuf2Wait
	ret
}


proc fioNextFromBuf2End() {
	Z = sdcard_buf_2 + 0x100
	r25 = fio_buf_part_pos
	Z += r1.r25

	r24 = mem[Z]
	fio_buf_part_pos = r25++
	if (!SREG->Z) ret

   fio_readyFlags = r25 = fio_readyFlags & 0xFD
	fio_get_next_byte_handler = Z = fioNextFromBuf1Wait
	ret
}

proc fioNextFromBuf1Wait() {
	r24 = fio_readyFlags
	if (!r24[0]) rjmp	error

	fio_get_next_byte_handler = Z = fioNextFromBuf1Start

	r24 = sdcard_buf_1[0]
	fio_buf_part_pos = r25 = 1
	ret
error:
    fio_error_mask = r24 = fio_error_mask | 2
    fio_error_cnt = r25.r24 = fio_error_cnt+1

    r24 = 0x80					; при ошибке возвращается 0x80
    ret
}

proc fioNextFromBuf2Wait() {
	r24 = fio_readyFlags
	if (!r24[1]) rjmp	error

	fio_get_next_byte_handler = Z = fioNextFromBuf2Start

	r24 = sdcard_buf_2[0]
	fio_buf_part_pos = r25 = 1
	ret

error:

   fio_error_mask = r24 = fio_error_mask | 2
   fio_error_cnt = r25.r24 = fio_error_cnt + 1
   r24 = 0x80					; при ошибке возвращается 0x80
   ret
}

proc fioNextToBuf1Start() {
	Z = sdcard_buf_1
	r25 = fio_buf_part_pos
	Z += r1.r25

	mem[Z] = r24
	fio_buf_part_pos = r25++

	if (!SREG->Z) ret
	fio_put_next_byte_handler = Z = fioNextToBuf1End
	ret
}

proc fioNextToBuf2Start() {
	Z = sdcard_buf_2
	r25 = fio_buf_part_pos
	Z += r1.r25
	mem[Z] = r24
	fio_buf_part_pos = r25++

	if (!SREG->Z) ret
	fio_put_next_byte_handler = Z = fioNextToBuf2End
	ret
}

proc fioNextToBuf1End() {
	Z = sdcard_buf_1 + 0x100
	r25 = fio_buf_part_pos
	Z += r1.r25

	mem[Z] = r24
	fio_buf_part_pos = r25++

	if (!SREG->Z) ret
	fio_put_next_byte_handler = Z = fioNextToBuf2Wait
	ret
}

proc fioNextToBuf2End() {
	Z = sdcard_buf_2 + 0x100
	r25 = fio_buf_part_pos
	Z += r1.r25

	mem[Z] = r24
	fio_buf_part_pos = r25++

	if (!SREG->Z) ret
   fio_readyFlags = r25 = fio_readyFlags | 2

	fio_put_next_byte_handler = Z = fioNextToBuf1Wait
	ret
}

proc fioNextToBuf1Wait() {
	r25 = fio_readyFlags
	if (!r25[0]) rjmp	ok
	fio_error_mask = r24 = fio_error_mask | 2
	fio_error_cnt = r25.r24 = fio_error_cnt+1
	ret
ok:
	fio_put_next_byte_handler = Z = fioNextToBuf1Start

	sdcard_buf_1[0] = r24
	fio_buf_part_pos = r25 = 1
	ret
}

proc fioNextToBuf2Wait() {
	r25 = fio_readyFlags
	if (!r25[1]) rjmp	ok

	fio_error_mask = r24 = fio_error_mask | 4
	fio_error_cnt = r25.r24 = fio_error_cnt+1
	ret
ok:
	fio_put_next_byte_handler = Z = fioNextToBuf2Start
	sdcard_buf_2[0] = r24
	fio_buf_part_pos = r25 = 1
	ret
}


        """.trimIndent())
    }
*/

    private fun compile(code: String): List<String> = compileCode(code, true)

    private fun compileM8(expr: String, initCode: String = ""): List<String> {
        val code = """
            #define CPU "mega8"
            $initCode
            proc main() {
               $expr
            }
        """.trimIndent()

        val res= compile(code).
                filter { it.isNotEmpty() && it != ".global main" && it != "main:" }.toMutableList()
        assertEquals("#include <avr/io.h>", res[0])
        res.removeAt(0)
        return res
    }
}