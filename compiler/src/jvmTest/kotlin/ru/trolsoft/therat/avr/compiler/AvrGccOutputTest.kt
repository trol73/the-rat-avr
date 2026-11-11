package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException

class AvrGccOutputTest {

    @Test fun `movw instruction`() {
        val r = compileM8("""
            movw X, Y
        """.trimIndent())
        r shouldHaveSize 1
        r.last() shouldBe "movw R26, R28"
    }

    @Test fun `adiw instruction`() {
        val r = compileM8("""
            adiw Z, 10
        """.trimIndent())
        r shouldHaveSize 1
        r.last() shouldBe "adiw R30, 10"
    }

    @Test fun `sbiw instruction`() {
        val r = compileM8("""
            sbiw Z, 10
        """.trimIndent())
        r shouldHaveSize 1
        r.last() shouldBe "sbiw R30, 10"
    }

    @Test fun `movw operation`() {
        val r = compileM8("""
            X = Y
        """.trimIndent())
        r shouldHaveSize 1
        r.last() shouldBe "movw R26, R28"
    }

    @Test fun `write external byte var`() {
        val r = compileM8("""
            b = r10
        """.trimIndent(), "extern var b: byte")
        r shouldHaveSize 2
        r[0] shouldBe ".extern b"
        r[1] shouldBe "sts b, R10"
    }

    @Test fun `write external word var`() {
        val r = compileM8("""
            w = r10.r11
        """.trimIndent(), "extern var w: word")
        r shouldHaveSize 3
        r[0] shouldBe ".extern w"
        r[1] shouldBe "sts w+1, R10"
        r[2] shouldBe "sts w, R11"
    }

    @Test fun `read external byte var`() {
        val r = compileM8("""
            r10 = b
        """.trimIndent(), "extern var b: byte")
        r shouldHaveSize 2
        r[0] shouldBe ".extern b"
        r[1] shouldBe "lds R10, b"
    }

    @Test fun `read external word var`() {
        val r = compileM8("""
            r10.r11 = w
        """.trimIndent(), "extern var w: word")
        r shouldHaveSize 3
        r[0] shouldBe ".extern w"
        r[1] shouldBe "lds R10, w+1"
        r[2] shouldBe "lds R11, w"
    }

    @Test fun `local label`() {
        val r = compileM8("""
            rjmp lbl
            lbl:
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "rjmp _main_lbl"
        r[1] shouldBe "_main_lbl:"
    }

    @Test fun `if (!bit)`() {
        val r = compileM8("""
            if (!r24[7]) {
                nop
                sei
            }
        """.trimIndent())
        r shouldHaveSize 5
        r[0] shouldBe "sbrc R24, 7"
        r[1] shouldBe "rjmp _main_if_end_4"
        r[2] shouldBe "nop"
        r[3] shouldBe "sei"
        r[4] shouldBe "_main_if_end_4:"
    }

//    @Test fun testExternalProcAccess() {
//        val r = compileM8("""
//            Z = myExtProc
//        """.trimIndent(), "extern proc myExtProc()")
//
//    }

    @Test fun `assign pair to proc ptr`() {
        val r = compileM8("""
            Z = main
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "ldi R31, hi8(main)"
        r[1] shouldBe "ldi R30, lo8(main)"
    }

    @Test fun `write first byte to external array`() {
        val r = compileM8("""
            buf[0] = r24
        """.trimIndent(), "extern var buf: ptr")
        r shouldHaveSize 2
        r[0] shouldBe ".extern buf"
        r[1] shouldBe "sts buf, R24"
    }

    @Test fun `write to external array`() {
        val r = compileM8("""
            buf[10] = r24
        """.trimIndent(), "extern var buf: ptr")
        r shouldHaveSize 2
        r[0] shouldBe ".extern buf"
        r[1] shouldBe "sts buf+10, R24"
    }

    @Test fun `assign external buf plus word`() {
        val r = compileM8("""
            Z = buf + 0x100
        """.trimIndent(), "extern var buf: ptr")
        r shouldHaveSize 3
        r[0] shouldBe ".extern buf"
        r[1] shouldBe "ldi R31, hi8(buf+256)"
        r[2] shouldBe "ldi R30, lo8(buf+256)"
    }

    @Test fun `add external ptr to Z`() {
        val r = compileM8("""
            Z += buf
        """.trimIndent(), "extern var buf: ptr")
        r shouldHaveSize 3

        r[0] shouldBe ".extern buf"
        r[1] shouldBe "subi R30, lo8(-(buf))"
        r[2] shouldBe "sbci R31, hi8(-(buf))"
    }

    @Test fun `two proc`() {
        val r = compile("""
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
        r.shouldContainInOrder(
            ".global readByte",
            ".global readBlock",
            "readByte:",
            "readBlock:",
            "rcall readByte"
        )
    }

    @Test fun `loop arg reg pair 2`() {
        val r = compile("""
            #define CPU "mega8"
            proc myProc(size: r25.r24, buf: r23.r22) {
                loop(size) {
                    nop
                }
            }
        """.trimIndent())
        r shouldHaveSize 8
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".global myProc"
        r[2] shouldBe "myProc:"
        r[3] shouldBe "_myProc_loop_3:"
        r[4] shouldBe "nop"
        r[5] shouldBe "sbiw R24, 1"
        r[6] shouldBe "brne _myProc_loop_3"
        r[7] shouldBe "_myProc_loopEnd_3:"
    }

    @Test fun `assign with add extern ptr to Z`() {
        val r = compile("""
            #define CPU "mega8"
            extern var some_array : ptr
            proc myProc() {
                Z += some_array
            }
        """.trimIndent())
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern some_array"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "subi R30, lo8(-(some_array))"
        r[5] shouldBe "sbci R31, hi8(-(some_array))"
    }

    @Test fun `add extern ptr to Z`() {
        val r = compile("""
            #define CPU "mega8"
            extern var some_array : ptr
            proc myProc() {
                Z = Z + some_array
            }
        """.trimIndent())
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern some_array"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "subi R30, lo8(-(some_array))"
        r[5] shouldBe "sbci R31, hi8(-(some_array))"
    }

    @Test fun `unknown identifier error`() {
        shouldThrow<CompilerException> {
            compile("""
                #define CPU "mega8"
                proc myProc() {
                    ZH = invalid_identifier
                }
            """)
        }.message shouldBe "Unknown identifier: invalid_identifier"
    }

    @Test fun `STS var with offset`() {
        val r = compile("""
            #define CPU "mega8"
            extern var some_word : word 
            proc myProc() {
                sts some_word, r24
                sts some_word+1, r25
            }
        """.trimIndent())
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern some_word"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "sts some_word, R24"
        r[5] shouldBe "sts some_word+1, R25"
    }

    @Test fun `STS var with negative offset`() {
        val r = compile("""
            #define CPU "mega8"
            extern var some_word : word 
            proc myProc() {
                sts some_word, r24
                sts some_word-1, r28
            }
        """.trimIndent())
        r shouldHaveSize 6
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern some_word"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "sts some_word, R24"
        r[5] shouldBe "sts some_word-1, R28"
    }

    @Test fun `LDS var with offset`() {
        val r = compile("""
            #define CPU "mega8"
            extern var some_word : word 
            proc myProc() {
                lds r24, some_word
                lds r28, some_word+1
            }
        """.trimIndent())
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern some_word"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "lds R24, some_word"
        r[5] shouldBe "lds R28, some_word+1"
    }

    @Test fun `LDS var with negative offset`() {
        val r = compile("""
            #define CPU "mega8"
            extern var some_word : word 
            proc myProc() {
                lds r24, some_word
                lds r28, some_word-1
            }
        """.trimIndent())
        r shouldHaveSize 6
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern some_word"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "lds R24, some_word"
        r[5] shouldBe "lds R28, some_word-1"
    }

    @Test fun `loop with register pair`() {
        val r = compile("""
            #define CPU "atmega8"
            
            proc delay_ms_long() {
                loop (r25.r24) {
                    nop
                }
                ret
            }
        """.trimIndent())
        r shouldHaveSize 9
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".global delay_ms_long"
        r[2] shouldBe "delay_ms_long:"
        r[3] shouldBe "_delay_ms_long_loop_4:"
        r[4] shouldBe "nop"
        r[5] shouldBe "sbiw R24, 1"
        r[6] shouldBe "brne _delay_ms_long_loop_4"
        r[7] shouldBe "_delay_ms_long_loopEnd_4:"
        r[8] shouldBe "ret"
    }

    @Test fun `assign local label address`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc myProc() {
                Z = loc_lbl
            loc_lbl:
                ret
            }
        """.trimIndent())
        r.last(1) shouldEndWith ":"
        val labelName = r[r.size-2].removeSuffix(":")
        r.last(3) shouldBe "ldi R31, hi8($labelName)"
        r.last(2) shouldBe "ldi R30, lo8($labelName)"
    }

    @Test fun `data bytes in procedure`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc myProc() {
                ret
            loc_data:
                byte[] {
                    123, 45
                }
            }
        """.trimIndent())
        r.last(1) shouldBe  ".byte 123"
        r.last() shouldBe ".byte 45"
    }

    @Test fun `address local table words`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc myProc() {
                Z = my_table 
            }
            
            my_table:
                byte[] {
                    123, 45
                }

        """.trimIndent())
        r.last(4) shouldBe "ldi R31, hi8(my_table)"
        r.last(3) shouldBe "ldi R30, lo8(my_table)"
    }

    @Test fun `address local table bytes`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc myProc() {
                Z = my_table/2
            my_table:
                byte[] {
                    123, 45
                }                 
            }
        """.trimIndent())
        r.last(4) shouldBe "ldi R31, pm_hi8(_myProc_my_table)"
        r.last(3) shouldBe "ldi R30, pm_lo8(_myProc_my_table)"
        r.last(1) shouldBe ".byte 123"
        r.last(0) shouldBe ".byte 45"
    }

    @Test fun `table with proc addresses in bytes`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc myProc1() {
                r20 = 1
                ret
            }
            
            proc myProc2() {
                r21 = 2
                ret
            }
            
            proc myProc3() {
                r22 = 1
                ret
            }
            
            proc myProc() {
                Z = my_table/2
            my_table:
                word[] {
                    myProc1, myProc2, myProc3  
                }                 
            }
        """.trimIndent())
        r.last(2) shouldBe ".word myProc1"
        r.last(1) shouldBe ".word myProc2"
        r.last(0) shouldBe ".word myProc3"
    }

    @Test fun `table with proc addresses in words`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc myProc1() {
                r20 = 1
                ret
            }
            
            proc myProc2() {
                r21 = 2
                ret
            }
            
            proc myProc3() {
                r22 = 1
                ret
            }
            
            proc myProc() {
                Z = my_table/2
            my_table:
                word[] {
                    myProc1/2, myProc2, myProc3/2  
                }                 
            }
        """.trimIndent())
        r.last(2) shouldBe ".word pm(myProc1)"
        r.last(1) shouldBe ".word myProc2"
        r.last(0) shouldBe ".word pm(myProc3)"
    }

    @Test fun `assign low registers to address cause error`() {
        shouldThrow<CompilerException> {
            compile(
                """
                #define CPU "mega8"
                
                proc myProc() {
                    r1.r0 = myProc/2
                }
            """.trimIndent()
            )
        }.message shouldBe "r16..r31 expected but r1 found. Perhaps you can do this using a temporary register."
    }

    @Test fun `table with undefined proc in bytes error`() {
        shouldThrow<CompilerException> {
            compile(
                """
                #define CPU "mega8"
                
                proc myProc1() {
                    r20 = 1
                    ret
                }
               
                proc myProc() {
                    Z = my_table/2
                my_table:
                    word[] {
                        myProc1, myProc2, myProc3  
                    }                 
                }
            """.trimIndent()
            )
        }.message shouldBe "Unexpected argument: myProc2"
    }

    @Test fun `table with undefined proc in word error`() {
        shouldThrow<CompilerException> {
            compile(
                """
                #define CPU "mega8"
                
                proc myProc1() {
                    r20 = 1
                    ret
                }
               
                proc myProc() {
                    Z = my_table/2
                my_table:
                    word[] {
                        myProc1/2, myProc2/2, myProc3/2  
                    }                 
                }
            """.trimIndent()
            )
        }.message shouldBe "Unexpected argument: myProc2"
    }

    @Test fun `global label`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc proc3() {
                r21 = 1
                rcall lbl
            }
            
            proc proc1() {
                r20 = 0
            @lbl:
                r21 = 0
                ret
            }
            
            proc proc2() {
                r20 = 1
                rjmp lbl
            }
        """.trimIndent())

        r.last(10) shouldBe "proc3:"
        r.last(8) shouldBe "rcall lbl"
        r.last(7) shouldBe "proc1:"
        r.last(5) shouldBe "lbl:"
        r.last(3) shouldBe "ret"
        r.last(2) shouldBe "proc2:"
        r.last(0) shouldBe "rjmp lbl"
    }

    @Test fun `private function should not exported`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc proc1() {
                ret
            }
            
            proc _proc2() {
            }
            
            proc proc3() {
                ret
            }
        """.trimIndent())
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".global proc1"
        r[2] shouldBe ".global proc3"
        r[3] shouldBe "proc1:"
    }

    @Test fun `LDS structure byte`() {
        val r = compile("""
            #define CPU "mega8"
            struct {
                uint8_t data[5]
                uint8_t type
                uint32_t flags
            } some_struct_t
            extern var ss : some_struct_t 
            proc myProc() {
                lds r24, ss->type
                r22 = ss->type
            }
        """.trimIndent())
        r shouldHaveSize 6
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern ss"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "lds R24, ss+5"
        r[5] shouldBe "lds R22, ss+5"
    }

    @Test fun `extern byte array`() {
        val r = compile("""
            #define CPU "mega8"
            
            extern var buf : byte[10]
            
            proc myProc() {
                r24 = buf[1]
                buf[5] = r24
            }
        """.trimIndent())
        r shouldHaveSize 6
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern buf"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "lds R24, buf+1"
        r[5] shouldBe "sts buf+5, R24"
    }

    @Test fun `extern word array`() {
        val r = compile("""
            #define CPU "mega8"
            
            extern var buf : word[10]
            
            proc myProc() {
                X = buf[1]
                buf[5] = X
            }
        """.trimIndent())
        r shouldHaveSize 8
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern buf"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "lds R27, buf+2+1"
        r[5] shouldBe "lds R26, buf+2"
        r[6] shouldBe "sts buf+10+1, R27"
        r[7] shouldBe "sts buf+10, R26"
    }

    @Test fun `extern dword array`() {
        val r = compile("""
            #define CPU "mega8"
            
            extern var buf : dword[10]
            
            proc myProc() {
                r21.r22.r23.r24 = buf[1]
                buf[5] = r21.r22.r23.r24
            }
        """.trimIndent())
        r shouldHaveSize 12
        r[0] shouldBe "#include <avr/io.h>"
        r[1] shouldBe ".extern buf"
        r[2] shouldBe ".global myProc"
        r[3] shouldBe "myProc:"
        r[4] shouldBe "lds R24, buf+4+3"
        r[5] shouldBe "lds R23, buf+4+2"
        r[6] shouldBe "lds R22, buf+4+1"
        r[7] shouldBe "lds R21, buf+4"
        r[8] shouldBe "sts buf+20+3, R24"
        r[9] shouldBe "sts buf+20+2, R23"
        r[10] shouldBe "sts buf+20+1, R22"
        r[11] shouldBe "sts buf+20, R21"
    }

    @Test fun `byte var`() {
        val r = compile("""
            #define CPU "mega8"
            
            var b1: byte
            
            proc main() {
                nop
            }
        """.trimIndent())
        r shouldContain ".global b1"
        r shouldContain ".bss"
        r shouldContain "b1: .space 1"
        r shouldContain ".text"
    }

    @Test fun `word var`() {
        val r = compile("""
            #define CPU "mega8"
            
            var w1: word
            var _w2: word
            
            proc main() {
                _w2 = r25.r24 = w1
            }
        """.trimIndent())
        r[1] shouldBe ".global w1"
        r[2] shouldBe ".global main"
        r[3] shouldBe ".bss"
        r[4] shouldBe "w1: .space 2"
        r[5] shouldBe "_w2: .space 2"
        r[6] shouldBe ".text"
        r[7] shouldBe "main:"
        r[8] shouldBe "lds R25, w1+1"
        r[9] shouldBe "lds R24, w1"
        r[10] shouldBe "sts _w2+1, R25"
        r[11] shouldBe "sts _w2, R24"
    }

    @Test fun `word array var`() {
        val r = compile("""
            #define CPU "mega8"
            
            var wa: word[10]
            
            proc main() {
                nop
            }
        """.trimIndent())
        r shouldContain ".global wa"
        r shouldContain ".bss"
        r shouldContain "wa: .space 20"
        r shouldContain ".text"
    }

    @Test fun `add reg to byte var`() {
        val r = compile("""
            #define CPU "mega8"
            
            var b: byte
            
            proc main() {
                r24 = b + r23
            }
        """.trimIndent())
        r shouldHaveSize 9
        r[6] shouldBe "main:"
        r[7] shouldBe "lds R24, b"
        r[8] shouldBe "add R24, R23"
    }

    @Test fun `Z += prgmem array address`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc main() {
                Z += data
            }
            data: byte[] { 1, 2 }
        """.trimIndent())
        r shouldHaveSize 8
        r[3] shouldBe "subi R30, lo8(-(data-0x10000))"
        r[4] shouldBe "sbci R31, hi8(-(data-0x10000))"
    }

    @Test fun `Z += prgmem array address div 2`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc main() {
                Z += data/2
            }
            data: byte[] { 1, 2 }
        """.trimIndent())
        r shouldHaveSize 8
        r[3] shouldBe "subi R30, pm_lo8(-(data-0x20000))"
        r[4] shouldBe "sbci R31, pm_hi8(-(data-0x20000))"
    }

    @Test fun `double assign pair address div 2`() {
        val r = compile("""
            #define CPU "mega8"
            
            proc main() {
                r1.r0 = Z = main/2
            }
        """.trimIndent())
        r[3] shouldBe "ldi R31, pm_hi8(main)"
        r[4] shouldBe "ldi R30, pm_lo8(main)"
        r[5] shouldBe "movw R0, R30"
    }

    @Test fun `call wrong label`() {
        shouldThrow<CompilerException> {
            compileM8("rcall wrong_name")
        }.message shouldBe "Unknown label: wrong_name"
    }


    @Test fun `extern proc load address to pair`() {
        val r = compile("""
            #define CPU "mega8"
            
            extern proc ext_p()
            
            proc main() {
                Z = ext_p
            }
        """.trimIndent())

        r shouldContain ".extern ext_p"
        r shouldContain "ldi R31, hi8(ext_p)"
        r shouldContain "ldi R30, lo8(ext_p)"
    }

    @Test fun `extern proc add address to pair`() {
        val r = compile("""
            #define CPU "mega8"
            
            extern proc ext_p()
            
            proc main() {
                Z += ext_p
            }
        """.trimIndent())
        r[1] shouldBe ".extern ext_p"
        r[4] shouldBe "subi R30, lo8(-(ext_p))"
        r[5] shouldBe "sbci R31, hi8(-(ext_p))"
    }


    @Test fun `assign pair to next proc ptr`() {
        val res = compile("""
            #define CPU "mega8"

            proc firstProc() {
                Z = nextProc
            }
            proc nextProc() {

            }
        """.trimIndent())
        res shouldHaveSize 7
        res.last(2) shouldBe "ldi R31, hi8(nextProc)"
        res.last(1) shouldBe "ldi R30, lo8(nextProc)"
    }

    @Test fun `multiple inlines call with probes`() {
        val res = compile("""
            #define CPU "mega8"

            proc delay() {
                nop
                ret
            }

            inline proc test_delay_if_need() {
            	if (SPCR->SPR1) {
            		rcall	delay
            	}
            }

            proc main() {
                test_delay_if_need()
                test_delay_if_need()
            }
        """.trimIndent())

        res[3] shouldBe "delay:"
        res[4] shouldBe "nop"
        res[5] shouldBe "ret"
        res[6] shouldBe "main:"
        res[7] shouldBe "sbic 0xD, 1"
        res[8] shouldBe "rcall delay"
        res[9] shouldBe "sbic 0xD, 1"
        res[10] shouldBe "rcall delay"
    }

    private fun compile(code: String): List<String> = compileCode(code, true)

    private fun compileM8(expr: String, initCode: String = ""): List<String> {
        val code = """
            #define CPU "mega8"
            $initCode
            proc main() {
               $expr
            }
        """.trimIndent()

        val res = compile(code).
                filter { it.isNotEmpty() && it != ".global main" && it != "main:" }.toMutableList()
        res[0] shouldBe "#include <avr/io.h>"
        res.removeAt(0)
        return res
    }

    private fun List<String>.last(n: Int) = this[this.size-1-n]
}