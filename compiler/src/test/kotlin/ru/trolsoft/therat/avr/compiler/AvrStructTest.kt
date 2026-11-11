package ru.trolsoft.therat.avr.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.compiler.CompilerException

class AvrStructTest {
    @Test fun `simple struct address`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
            
            var ms: my_struct_t
            
            proc main() {
                Z = ms
            }
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "ldi r31, 0"
        r[1] shouldBe "ldi r30, 96"
    }

    @Test fun `simple struct sizeof`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
            
            var ms: my_struct_t
            
            proc main() {
                r24 = sizeof(my_struct_t)
                r25 = sizeof(ms)
            }
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "ldi r24, 4"
        r[1] shouldBe "ldi r25, 4"
    }

    @Test fun `GCC simple struct address`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
            
            extern var ms: my_struct_t
            
            proc main() {
                Z = ms
            }
        """.trimIndent(), true)
        r shouldHaveSize 6
        r.last(2) shouldBe "main:"
        r.last(1) shouldBe "ldi R31, hi8(ms)"
        r.last(0) shouldBe "ldi R30, lo8(ms)"
    }

    @Test fun `simple struct array address`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
            
            var ms: my_struct_t[5]
            
            proc main() {
                Z = ms
            }
        """.trimIndent())
        r shouldHaveSize 2
        r[0] shouldBe "ldi r31, 0"
        r[1] shouldBe "ldi r30, 96"
    }

    @Test fun `GCC simple struct array address`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
            
            extern var ms: my_struct_t[5]
            
            proc main() {
                Z = ms
            }
        """.trimIndent(), true)
        r shouldHaveSize 6
        r.last(2) shouldBe "main:"
        r.last(1) shouldBe "ldi R31, hi8(ms)"
        r.last(0) shouldBe "ldi R30, lo8(ms)"
    }

    @Test fun `simple struct`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
            
            var ms: my_struct_t
            
            proc main() {
                r24 = ms->b1
                ms->b2 = r24
                r21.r22 = ms->w
            }
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldBe "lds r24, 96"
        r[1] shouldBe "sts 97, r24"
        r[2] shouldBe "lds r21, 99"
        r[3] shouldBe "lds r22, 98"
    }

    @Test fun `GCC simple struct`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
            
            extern var ms: my_struct_t
            
            proc main() {
                r24 = ms->b1
                ms->b2 = r24
                r21.r22 = ms->w
            }
        """.trimIndent(), true)
        r shouldHaveSize 8

        r.last(3) shouldBe "lds R24, ms"
        r.last(2) shouldBe "sts ms+1, R24"
        r.last(1) shouldBe "lds R21, ms+2+1"
        r.last(0) shouldBe "lds R22, ms+2"
    }

    @Test fun `simple struct with wrong field name`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                
                struct {
                    byte b1
                    byte b2
                    word w
                } my_struct_t
                
                var ms: my_struct_t
                
                proc main() {
                    r24 = ms->bx1
                }
                """)
        }.message shouldStartWith "my_struct_t has no member named 'bx1'"
    }

    @Test fun `GCC simple struct with wrong field name`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                
                struct {
                    byte b1
                    byte b2
                    word w
                } my_struct_t
                
                extern var ms: my_struct_t
                
                proc main() {
                    r24 = ms->bx1
                }
                """, true)
        }.message shouldStartWith "my_struct_t has no member named 'bx1'"
    }

    @Test fun `simple struct with pointer`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b
                byte *pb
                word w
            } my_struct_t
            
            var ms: my_struct_t
            
            proc main() {
                Z = ms->pb
            }
        """.trimIndent())
        r shouldHaveSize 2

        r[0] shouldBe "ldi r31, 0"
        r[1] shouldBe "ldi r30, 97"
    }

    @Test fun `GCC simple struct with pointer`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b
                byte *pb
                word w
            } my_struct_t
            
            extern var ms: my_struct_t
            
            proc main() {
                Z = ms->pb
            }
        """.trimIndent(), true)
        r shouldHaveSize 6
        r.last(2) shouldBe "main:"
        r.last(1) shouldBe "ldi R31, hi8(ms+1)"
        r.last(0) shouldBe "ldi R30, lo8(ms+1)"
    }

    @Test fun `simple struct with array`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1[5]
                byte b2[10]
                word w[3]
            } my_struct_t
            
            var ms: my_struct_t
            
            proc main() {
                r24 = ms->b1[0]
                ms->b2[1] = r24
                ms->b2[5] = r25
                r21.r22 = ms->w[0]
                ms->w[1] = r21.r22
                
                Z = ms->w
            }
        """.trimIndent())
        r shouldHaveSize 9
        r[0] shouldBe "lds r24, 96"
        r[1] shouldBe "sts 102, r24"
        r[2] shouldBe "sts 106, r25"
        r[3] shouldBe "lds r21, 112"
        r[4] shouldBe "lds r22, 111"
        r[5] shouldBe "sts 114, r21"
        r[6] shouldBe "sts 113, r22"
        r[7] shouldBe "ldi r31, 0"
        r[8] shouldBe "ldi r30, 111"
    }

    @Test fun `GCC simple struct with array`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1[5]
                byte b2[10]
                word w[3]
            } my_struct_t
            
            extern var ms: my_struct_t
            
            proc main() {
                r24 = ms->b1[0]
                ms->b2[1] = r24
                ms->b2[5] = r25
                r21.r22 = ms->w[0]
                ms->w[1] = r21.r22
                
                Z = ms->w
            }
        """.trimIndent(), true)
        r shouldHaveSize 13
        r.last(8) shouldBe "lds R24, ms"
        r.last(7) shouldBe "sts ms+6, R24"
        r.last(6) shouldBe "sts ms+10, R25"
        r.last(5) shouldBe "lds R21, ms+15+1"
        r.last(4) shouldBe "lds R22, ms+15"
        r.last(3) shouldBe "sts ms+17+1, R21"
        r.last(2) shouldBe "sts ms+17, R22"
        r.last(1) shouldBe "ldi R31, hi8(ms+15)"
        r.last(0) shouldBe "ldi R30, lo8(ms+15)"
    }

    @Test fun `simple struct with array with wrong field name`() {
        shouldThrow<CompilerException> {
            compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1[5]
                byte b2[10]
                word w[3]
            } my_struct_t
            
            var ms: my_struct_t
            
            proc main() {
                r24 = ms->bx1[0]
            }
                """)
        }.message shouldStartWith "my_struct_t has no member named 'bx1'"
    }

    @Test fun `GCC simple struct with array with wrong field name`() {
        shouldThrow<CompilerException> {
            compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1[5]
                byte b2[10]
                word w[3]
            } my_struct_t
            
            extern var ms: my_struct_t
            
            proc main() {
                r24 = ms->bx1[0]
            }
                """, true)
        }.message shouldStartWith "my_struct_t has no member named 'bx1'"
    }

    @Test fun `array of simple struct`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
            
            var ms: my_struct_t[5]
            
            proc main() {
                r24 = ms[1]->b1
                ms[0]->b2 = r24
                r21.r22 = ms[2]->w
            }
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldBe "lds r24, ${96+4}"
        r[1] shouldBe "sts ${96+1}, r24"
        r[2] shouldBe "lds r21, ${96+8+2+1}"
        r[3] shouldBe "lds r22, ${96+8+2}"
    }

    @Test fun `GCC array of simple struct`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
            
            extern var ms: my_struct_t[5]
            
            proc main() {
                r24 = ms[1]->b1
                ms[0]->b2 = r24
                r21.r22 = ms[2]->w
            }
        """.trimIndent(), true)
        r shouldHaveSize 8
        r.last(3) shouldBe "lds R24, ms+4"
        r.last(2) shouldBe "sts ms+1, R24"
        r.last(1) shouldBe "lds R21, ms+8+2+1"
        r.last(0) shouldBe "lds R22, ms+8+2"
    }

    @Test fun `array of simple struct with wrong name`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                
                struct {
                    byte b1
                    byte b2
                    word w
                } my_struct_t
                
                var ms: my_struct_t[5]
                
                proc main() {
                    r24 = ms[1]->bx1
                }                
                """)
        }.message shouldStartWith "my_struct_t has no member named 'bx1'"
    }

    @Test fun `GCC array of simple struct with wrong name`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                
                struct {
                    byte b1
                    byte b2
                    word w
                } my_struct_t
                
                extern var ms: my_struct_t[5]
                
                proc main() {
                    r24 = ms[1]->bx1
                }                
                """, true)
        }.message shouldStartWith "my_struct_t has no member named 'bx1'"
    }

    @Test fun `nested structures`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b
                byte *pb
                word w
            } my_struct1_t
            
            struct {
                byte x
                my_struct1_t s1
            } my_struct2_t
            
            var ms2: my_struct2_t
            
            proc main() {
                ms2->s1->w = r21.r20
                r21 = ms2->s1->b
            }
        """.trimIndent())
        r shouldHaveSize 3
        r[0] shouldBe "sts ${96+1+3+1}, r21"
        r[1] shouldBe "sts ${96+1+3}, r20"
        r[2] shouldBe "lds r21, ${96+1}"
    }

    @Test fun `GCC nested structures`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b
                byte *pb
                word w
            } my_struct1_t
            
            struct {
                byte x
                my_struct1_t s1
            } my_struct2_t
            
            extern var ms2: my_struct2_t
            
            proc main() {
                ms2->s1->w = r21.r20
                r21 = ms2->s1->b
            }
        """.trimIndent(), true)
        r shouldHaveSize 7
        r.last(2) shouldBe "sts ms2+1+3+1, R21"
        r.last(1) shouldBe "sts ms2+1+3, R20"
        r.last(0) shouldBe "lds R21, ms2+1"
    }

    @Test fun `nested structures with wrong name`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                
                struct {
                    byte b
                    byte *pb
                    word w
                } my_struct1_t
                
                struct {
                    byte x
                    my_struct1_t s1
                } my_struct2_t
                
                var ms2: my_struct2_t
                
                proc main() {
                    ms2->s1->wx = r21.r20
                }                
                """)
        }.message shouldStartWith "my_struct1_t has no member named 'wx'"
    }

    @Test fun `GCC nested structures with wrong name`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                
                struct {
                    byte b
                    byte *pb
                    word w
                } my_struct1_t
                
                struct {
                    byte x
                    my_struct1_t s1
                } my_struct2_t
                
                extern var ms2: my_struct2_t
                
                proc main() {
                    ms2->s1->wx = r21.r20
                }                
                """, true)
        }.message shouldStartWith "my_struct1_t has no member named 'wx'"
    }

    @Test fun `more nested structures`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte start[10]
                byte b
                byte middle[10]
                word w
            } my_struct1_t
            
            struct {
                byte x
                my_struct1_t s1
            } my_struct2_t
            
            struct {
                dword x
                my_struct2_t s2
            } my_struct3_t
            
            var ms3: my_struct3_t
            
            proc main() {
                ms3->s2->s1->w = r21.r20
                r21 = ms3->s2->s1->b
            }
        """.trimIndent())
        r shouldHaveSize 3
        r[0] shouldBe "sts ${96+4+1+10+1+10+1}, r21"
        r[1] shouldBe "sts ${96+4+1+10+1+10}, r20"
        r[2] shouldBe "lds r21, ${96+4+1+10}"
    }

    @Test fun `GCC more nested structures`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte start[10]
                byte b
                byte middle[10]
                word w
            } my_struct1_t
            
            struct {
                byte x
                my_struct1_t s1
            } my_struct2_t
            
            struct {
                dword x
                my_struct2_t s2
            } my_struct3_t
            
            extern var ms3: my_struct3_t
            
            proc main() {
                ms3->s2->s1->w = r21.r20
                r21 = ms3->s2->s1->b
            }
        """.trimIndent(), true)
        r shouldHaveSize 7

        r.last(2) shouldBe "sts ms3+4+1+21+1, R21"
        r.last(1) shouldBe "sts ms3+4+1+21, R20"
        r.last(0) shouldBe "lds R21, ms3+4+1+10"
    }

    @Test fun `more nested structures with wrong name`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                
                struct {
                    byte start[10]
                    byte b
                    byte middle[10]
                    word w
                } my_struct1_t
                
                struct {
                    byte x
                    my_struct1_t s1
                } my_struct2_t
                
                struct {
                    dword x
                    my_struct2_t s2
                } my_struct3_t
                
                var ms3: my_struct3_t
                
                proc main() {
                    ms3->s2->s1->wx = r21.r20
                }                
                """)
        }.message shouldStartWith "my_struct1_t has no member named 'wx'"
    }

    @Test fun `GCC more nested structures with wrong name`() {
        shouldThrow<CompilerException> {
            compileCode("""
                #define CPU "atmega8"
                
                struct {
                    byte start[10]
                    byte b
                    byte middle[10]
                    word w
                } my_struct1_t
                
                struct {
                    byte x
                    my_struct1_t s1
                } my_struct2_t
                
                struct {
                    dword x
                    my_struct2_t s2
                } my_struct3_t
                
                extern var ms3: my_struct3_t
                
                proc main() {
                    ms3->s2->s1->wx = r21.r20
                }                
                """, true)
        }.message shouldStartWith "my_struct1_t has no member named 'wx'"
    }

    @Test fun `array in nested structures`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b[5]
                word w[5]
            } my_struct1_t
            
            struct {
                word x
                my_struct1_t s1
            } my_struct2_t
            
            var ms2: my_struct2_t
            
            proc main() {
                ms2->s1->w[2] = r21.r20
                r21 = ms2->s1->b[3]
            }
        """.trimIndent())
        r shouldHaveSize 3
        r[0] shouldBe "sts ${96+2+5+4+1}, r21"
        r[1] shouldBe "sts ${96+2+5+4}, r20"
        r[2] shouldBe "lds r21, ${96+2+3}"
    }

    @Test fun `GCC array in nested structures`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b[5]
                word w[5]
            } my_struct1_t
            
            struct {
                word x
                my_struct1_t s1
            } my_struct2_t
            
            extern var ms2: my_struct2_t
            
            proc main() {
                ms2->s1->w[2] = r21.r20
                r21 = ms2->s1->b[3]
            }
        """.trimIndent(), true)
        r shouldHaveSize 7
        r.last(2) shouldBe "sts ms2+2+9+1, R21"
        r.last(1) shouldBe "sts ms2+2+9, R20"
        r.last(0) shouldBe "lds R21, ms2+2+3"
    }

    @Test fun `nested structures as array`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b[5]
                word w[5]
            } my_struct1_t
            
            struct {
                word x
                my_struct1_t s1[5]
            } my_struct2_t
            
            var ms2: my_struct2_t
            
            proc main() {
                ms2->s1[1]->w[2] = r21.r20
                r21 = ms2->s1[2]->b[3]
            }
        """.trimIndent())
        r shouldHaveSize 3
        r[0] shouldBe "sts ${96+2+15+5+2*2+1}, r21"
        r[1] shouldBe "sts ${96+2+15+5+2*2}, r20"
        r[2] shouldBe "lds r21, ${96+2+15*2+3}"
    }

    @Test fun `GCC nested structures as array`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte b[5]
                word w[5]
            } my_struct1_t
            
            struct {
                word x
                my_struct1_t s1[5]
            } my_struct2_t
            
            extern var ms2: my_struct2_t
            
            proc main() {
                ms2->s1[1]->w[2] = r21.r20
                r21 = ms2->s1[2]->b[3]
            }
        """.trimIndent(), true)
        r shouldHaveSize 7
        r.last(2) shouldBe "sts ms2+17+9+1, R21"
        r.last(1) shouldBe "sts ms2+17+9, R20"
        r.last(0) shouldBe "lds R21, ms2+32+3"
    }

    @Test fun `simple struct array in prg space`() {
        val r = compileHex("""
            #define CPU "atmega8"
            
            struct {
                byte id
                word some_proc
                word data[5]
            } my_struct_t
            
            proc main() {
                nop
                nop
            }
             
            proc p1() {
                r24 = 1
            }
            
            proc p2() {
                r25 = 2
            }
            
            my_struct_t[] {
                {id = 10, some_proc = p1, data = [1, 2, 3, 4, 5000]},
                { ; comment
                   id = 20, some_proc = p2, 
                   data = [6, 7, 8, 9, 7000]
                },
                /* 
                   multiline comment
                */
                {id = 30, some_proc = main, data = [0xf0, 0xf1, 0xf2, 0xf3, 0xf4]}
            }
        """.trimIndent())
        r.trim() shouldBe
                """
                    :10000000 0000 0000 81E0 92E0 0A 0400 0100 0200 03 09
                    :10001000 00 0400 8813 14 0600 0600 0700 0800 0900 09
                    :10002000 581B 1E 0000 F000 F100 F200 F300 F400 00 85
                    :00000001FF
                """.replace(" ", "").trimIndent()
    }

    @Test fun `simple struct array in prg space GCC`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                byte id
                word some_proc
                word data[3]
            } my_struct_t
            
            proc main() {
                nop
                nop
            }
             
            proc p1() {
                r24 = 1
            }
            
            proc p2() {
                r25 = 2
            }
            
            extern proc p3()
            
            my_struct_t[] {
                {id = 10, some_proc = p1, data = [1, 2, 3]},
                {id = 20, some_proc = p2/2, data = [6, 7, 8]},
                {id = 30, some_proc = main, data = [0xf0, 0xf1, 0xf2]},
                {id = 40, some_proc = p3/2, data = [9, 10, 11]}
            }
        """.trimIndent(), true)

        var b = 12
        r[b++] shouldBe ".byte 10"
        r[b++] shouldBe ".word p1"
        r[b++] shouldBe ".word 1"
        r[b++] shouldBe ".word 2"
        r[b++] shouldBe ".word 3"

        r[b++] shouldBe ".byte 20"
        r[b++] shouldBe ".word pm(p2)"
        r[b++] shouldBe ".word 6"
        r[b++] shouldBe ".word 7"
        r[b++] shouldBe ".word 8"

        r[b++] shouldBe ".byte 30"
        r[b++] shouldBe ".word main"
        r[b++] shouldBe ".word 240"
        r[b++] shouldBe ".word 241"
        r[b++] shouldBe ".word 242"

        r[b++] shouldBe ".byte 40"
        r[b++] shouldBe ".word pm(p3)"
        r[b++] shouldBe ".word 9"
        r[b++] shouldBe ".word 10"
        r[b] shouldBe ".word 11"
    }

    @Test fun `struct array error, item not defined`() {
        shouldThrow<CompilerException> {
            compileHex(
                """
                #define CPU "atmega8"
                
                struct {
                    byte a
                    word b
                    word data[5]
                } my_struct_t
                
                my_struct_t[] {
                    { a = 10 },
                }
            """.trimIndent()
            )
        }.message.shouldBe("Undefined value for field 'b'")
    }

    @Test fun `struct array error, extra item`() {
        shouldThrow<CompilerException> {
            compileHex(
                """
                #define CPU "atmega8"
                
                struct {
                    byte a
                    word b
                } my_struct_t
                
                my_struct_t[] {
                    { a = 10, b = 11, c = 0 },
                }
            """.trimIndent()
            )
        }.message.shouldBe("Unknown field 'c' for structure my_struct_t")
    }

    @Test fun `struct array error, wrong array size`() {
        shouldThrow<CompilerException> {
            compileHex(
                """
                #define CPU "atmega8"
                
                struct {
                    byte a
                    word data[5]
                } my_struct_t
                
                my_struct_t[] {
                    { a = 10, data = [1, 2] },
                }
            """.trimIndent()
            )
        }.message.shouldBe("Wrong array size. Expected 5 but found 2 items")
    }

    @Test fun `GCC read and write dword field with the same name`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                uint32_t addr
            } my_struct_t
                        
            extern var ms: my_struct_t
            
            proc main() {
                use r21.r20.r19.r18 as addr
                addr = ms->addr
                addr++
                ms->addr = addr 
            }
        """.trimIndent(), true)

        r shouldHaveSize 16
        r.last(11) shouldBe "lds R18, ms+3"
        r.last(10) shouldBe "lds R19, ms+2"
        r.last(9) shouldBe "lds R20, ms+1"
        r.last(8) shouldBe "lds R21, ms"

        r.last(7) shouldBe "subi R18, 255"
        r.last(6) shouldBe "sbci R19, 255"
        r.last(5) shouldBe "sbci R20, 255"
        r.last(4) shouldBe "sbci R21, 255"

        r.last(3) shouldBe "sts ms+3, R18"
        r.last(2) shouldBe "sts ms+2, R19"
        r.last(1) shouldBe "sts ms+1, R20"
        r.last(0) shouldBe "sts ms, R21"
    }

    @Test fun `add struct address to Z`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                uint32_t addr
            } my_struct_t
                        
            var ms: my_struct_t
            
            proc main() {
                Z += ms
            }
        """.trimIndent())

        r shouldHaveSize 2
        r.last(1) shouldBe "subi r30, 160"
        r.last(0) shouldBe "sbci r31, 255"
    }

    @Test fun `add struct address to Z GCC`() {
        val r = compileCode("""
            #define CPU "atmega8"
            
            struct {
                uint32_t addr
            } my_struct_t
                        
            extern var ms: my_struct_t
            
            proc main() {
                Z += ms
            }
        """.trimIndent(), true)

        r shouldHaveSize 6
        r.last(1) shouldBe "subi R30, lo8(-(ms))"
        r.last(0) shouldBe "sbci R31, hi8(-(ms))"
    }

    @Test fun `array of struct fields write`() {
        val r = compileCode("""
            #define CPU "atmega8"

            struct {
	            dword sum
	            byte count
	            word min
	            word max
            } adc_value_t

            extern var a1: adc_value_t[2]

            proc main() {
                  a1[1]->sum = r1.r1.r1.r1
            }
        """.trimIndent(), true)

        r shouldHaveSize 8
        r.last(3) shouldBe "sts a1+9+3, R1"
        r.last(2) shouldBe "sts a1+9+2, R1"
        r.last(1) shouldBe "sts a1+9+1, R1"
        r.last(0) shouldBe "sts a1+9, R1"
    }

    private fun List<String>.last(n: Int) = this[this.size-1-n]
}