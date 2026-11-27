package ru.trolsoft.therat.viper.compiler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import ru.trolsoft.therat.avr.compiler.andPrint
import kotlin.test.Test

class ExpressionCompilerTest {
    @Test fun `reg = reg`() {
        val r = compileProc("R0 = R5")
        r shouldHaveSize 1
        r.first() shouldBe "mov R0, R5"
    }

    @Test fun `reg = val`() {
        val r = compileProc("R1 = 10")
        r shouldHaveSize 1
        r.first() shouldBe "movi R1, 10"
    }

    @Test fun `reg = 0`() {
        val r = compileProc("R1 = 0")
        r shouldHaveSize 1
        r.first() shouldBe "movi R1, 0"
    }

    @Test fun `pair = val`() {
        val r = compileProc("RX = 0x1234")
        r shouldHaveSize 2
        r.first() shouldBe "movi R3, 0x12"
        r.last() shouldBe "movi R2, 0x34"
    }

    @Test fun `RX = 0x1200`() {
        val r = compileProc("RX = 0x1200")
        r shouldHaveSize 2
        r.first() shouldBe "movi R3, 0x12"
        r.last() shouldBe "movi R2, 0"
    }

    @Test fun `RX = 0`() {
        val r = compileProc("RX = 0")
        r shouldHaveSize 2
        r.first() shouldBe "movi R3, 0"
        r.last() shouldBe "movi R2, 0"
    }

    @Test fun `RX = 0 (hex)`() {
        compileHexProc("RX = 0") shouldBe "83008200"
    }


    @Test fun `call proc with single pair arg`() {
        val r = compileCode("""
            proc main() {
                call subproc(0x12)
            }

            proc subproc(v: R5) {
                mem[0x10] = R5
            }
        """.trimIndent())
        r shouldHaveSize 4
        r[0] shouldBe "movi R5, 0x12"
        r[1] shouldBe "call subproc"
        r[2] shouldBe "subproc:"
        r[3] shouldBe "storei R5, 0x10"
    }

    @Test fun `saveregs (R3)`() {
        val r = compileProc("""
            saveregs(R3) {
                call    0x1234
            }
        """.trimIndent()
        )
        r shouldHaveSize 3
        r[0] shouldBe "push R3"
        r[1] shouldBe "call 0x1234"
        r[2] shouldBe "pop R3"
    }

    @Test fun `saveregs (R0, RX)`() {
        val r = compileProc("""
            saveregs(R0, RX) {
                call    0x1234
            }
        """.trimIndent()
        )
        r shouldHaveSize 7
        r[0] shouldBe "push R0"
        r[1] shouldBe "push R3"
        r[2] shouldBe "push R2"
        r[3] shouldBe "call 0x1234"
        r[4] shouldBe "pop R2"
        r[5] shouldBe "pop R3"
        r[6] shouldBe "pop R0"
    }

    @Test fun `simple function with argument`() {
        val r = compileCode("""
            proc myProc(arg: R0) {
                R7 = arg
            }
        """.trimIndent())
        r shouldHaveSize 2
        r[1] shouldBe "mov R7, R0"
    }

    @Test fun `syscall inline()`() {
        val r = compileCode("""
            inline proc uartRead() -> (b: r0) {
                syscall 0x00
            }
            
            proc main() {
                uartRead()
            }
        """.trimIndent())
        r shouldHaveSize 1
        r[0] shouldBe "syscall 0"
    }

    @Test fun `syscall inline with assign retval()`() {
        val r = compileCode("""
            inline proc uartRead() -> (b: r0) {
                syscall 0x00
            }
            
            proc main() {
                uartRead() -> R7
            }
        """.trimIndent()).andPrint()
        r shouldHaveSize 2
        r[0] shouldBe "syscall 0"
        r[1] shouldBe "mov R7, R0"
    }



//    @Test fun `RX += RY`() {
//        val r = compileProc("RX += RY")
//        r shouldHaveSize 1
//        r.first() shouldBe "dad BC"
//    }
//
//    @Test
//    fun `HL += DE`() {
//        val r = compileProc("HL += DE")
//        r shouldHaveSize 1
//        r.first() shouldBe "dad DE"
//    }
//
//    @Test
//    fun `HL += HL`() {
//        val r = compileProc("HL += HL")
//        r shouldHaveSize 1
//        r.first() shouldBe "dad HL"
//    }
//
//    @Test
//    fun `HL += SP`() {
//        val r = compileProc("HL += SP")
//        r shouldHaveSize 1
//        r.first() shouldBe "dad SP"
//    }
//
//    @Test
//    fun `A += M`() {
//        val r = compileProc("A += M")
//        r shouldHaveSize 1
//        r.first() shouldBe "add M"
//    }
//
//    @Test
//    fun `A -= C`() {
//        val r = compileProc("A -= C")
//        r shouldHaveSize 1
//        r.first() shouldBe "sub C"
//    }
//
//    @Test
//    fun `A = mem addr`() {
//        val r = compileProc("A = mem[0x1234]")
//        r shouldHaveSize 1
//        r.first() shouldBe "lda 0x1234"
//    }
//
//    @Test
//    fun `A = mem with expr in addr`() {
//        val r = compileProc("A = mem[0x1000 + 0x34]")
//        r shouldHaveSize 1
//        r.first() shouldBe "lda 0x1034"
//    }
//
//    @Test
//    fun `mem addr = A`() {
//        val r = compileProc("mem[0x1234] = A")
//        r shouldHaveSize 1
//        r.first() shouldBe "sta 0x1234"
//    }
//
//    @Test
//    fun `mem addr = A = 0`() {
//        val r = compileProc("mem[0x1234] = A = 0")
//        r shouldHaveSize 2
//        r[0] shouldBe "xra A"
//        r[1] shouldBe "sta 0x1234"
//    }
//
//    @Test
//    fun `mem addr = A = 10`() {
//        val r = compileProc("mem[0x1234] = A = 10")
//        r shouldHaveSize 2
//        r[0] shouldBe "mvi A, 10"
//        r[1] shouldBe "sta 0x1234"
//    }
//
//    @Test
//    fun `mem1 = A = mem2`() {
//        val r = compileProc("mem[0x1234] = A = mem[0x5678]")
//        r shouldHaveSize 2
//        r[0] shouldBe "lda 0x5678"
//        r[1] shouldBe "sta 0x1234"
//    }
//
//    @Test fun `A = mem ++`() {
//        val r = compileProc("A = mem[0x1234]++")
//        r shouldHaveSize 2
//        r[0] shouldBe "lda 0x1234"
//        r[1] shouldBe "inr A"
//    }
//
//    @Test fun `D = A = mem`() {
//        val r = compileProc("D = A = mem[0x1234]")
//        r shouldHaveSize 2
//        r[0] shouldBe "lda 0x1234"
//        r[1] shouldBe "mov D, A"
//    }
//
//    @Test
//    fun `HL = mem addr`() {
//        val r = compileProc("HL = mem[0x1234]")
//        r shouldHaveSize 1
//        r.first() shouldBe "lhld 0x1234"
//    }
//
//    @Test
//    fun `mem addr = HL`() {
//        val r = compileProc("mem[0x1234] = HL")
//        r shouldHaveSize 1
//        r.first() shouldBe "shld 0x1234"
//    }
//
//    @Test
//    fun `HL = absolute variable`() {
//        val code = """
//            var myVar: word absolute 0x7766
//            proc main() {
//                HL = myVar
//            }
//        """.trimIndent()
//        val r = compileCode(code)
//        r shouldHaveSize 1
//        r.first() shouldBe "lhld 0x7766"
//    }
//
//    @Test
//    fun `absolute variable = HL`() {
//        val code = """
//            var myVar: word absolute 0x7766
//            proc main() {
//                myVar = HL
//            }
//        """.trimIndent()
//        val r = compileCode(code)
//        r shouldHaveSize 1
//        r.first() shouldBe "shld 0x7766"
//    }
//
//    @Test
//    fun `mem1 = HL = mem2`() {
//        val r = compileProc("mem[0x1234] = HL = mem[0x5678]")
//        r shouldHaveSize 2
//        r[0] shouldBe "lhld 0x5678"
//        r[1] shouldBe "shld 0x1234"
//    }
//
//    @Test fun `HL = const + DE`() {
//        val r = compileProc("HL = 0xFFB2 + DE")
//        r shouldHaveSize 2
//        r[0] shouldBe "lxi HL, 0xFFB2"
//        r[1] shouldBe "dad DE"
//    }
//
//    @Test fun `A = mem BC`() {
//        val r = compileProc("A = mem[BC]")
//        r shouldHaveSize 1
//        r.first() shouldBe "ldax BC"
//    }
//
//    @Test fun `A = mem DE`() {
//        val r = compileProc("A = mem[DE]")
//        r shouldHaveSize 1
//        r.first() shouldBe "ldax DE"
//    }
//
//    @Test fun `mem BC = A`() {
//        val r = compileProc("mem[BC] = A")
//        r shouldHaveSize 1
//        r.first() shouldBe "stax BC"
//    }
//
//    @Test fun `mem DE = A`() {
//        val r = compileProc("mem[DE] = A")
//        r shouldHaveSize 1
//        r.first() shouldBe "stax DE"
//    }
//
//    @Test fun `A = mem DE++`() {
//        val r = compileProc("A = mem[DE++]")
//        r shouldHaveSize 2
//        r[0] shouldBe "ldax DE"
//        r[1] shouldBe "inx DE"
//    }
//
//    @Test fun `A = mem ++DE`() {
//        val r = compileProc("A = mem[++DE]")
//        r shouldHaveSize 2
//        r[0] shouldBe "inx DE"
//        r[1] shouldBe "ldax DE"
//    }
//
//    @Test fun `mem DE++ = A`() {
//        val r = compileProc("mem[DE++] = A")
//        r shouldHaveSize 2
//        r[0] shouldBe "stax DE"
//        r[1] shouldBe "inx DE"
//    }
//
//    @Test fun `mem ++DE = A`() {
//        val r = compileProc("mem[++DE] = A")
//        r shouldHaveSize 2
//        r[0] shouldBe "inx DE"
//        r[1] shouldBe "stax DE"
//    }
//
//    @Test fun `mem HL = A`() {
//        val r = compileProc("mem[HL] = A")
//        r shouldHaveSize 1
//        r.first() shouldBe "mov M, A"
//    }
//
//    @Test fun `A = mem HL`() {
//        val r = compileProc("A = mem[HL]")
//        r shouldHaveSize 1
//        r.first() shouldBe "mov A, M"
//    }
//
//    @Test fun `mem HL++ = A`() {
//        val r = compileProc("mem[HL++] = A")
//        r shouldHaveSize 2
//        r[0] shouldBe "mov M, A"
//        r[1] shouldBe "inx HL"
//    }
//
//    @Test fun `mem ++HL = A`() {
//        val r = compileProc("mem[++HL] = A")
//        r shouldHaveSize 2
//        r[0] shouldBe "inx HL"
//        r[1] shouldBe "mov M, A"
//    }
//
//    @Test fun `A = mem HL++`() {
//        val r = compileProc("A = mem[HL++]")
//        r shouldHaveSize 2
//        r[0] shouldBe "mov A, M"
//        r[1] shouldBe "inx HL"
//    }
//
//    @Test fun `A = mem ++HL`() {
//        val r = compileProc("A = mem[++HL]")
//        r shouldHaveSize 2
//        r[0] shouldBe "inx HL"
//        r[1] shouldBe "mov A, M"
//    }
//
//    @Test fun `A += mem HL`() {
//        val r = compileProc("A += mem[HL]")
//        r shouldHaveSize 1
//        r.first() shouldBe "add M"
//    }
//
//    @Test fun `A -= mem HL++`() {
//        val r = compileProc("A -= mem[HL++]")
//        r shouldHaveSize 2
//        r[0] shouldBe "sub M"
//        r[1] shouldBe "inx HL"
//    }
//
//    @Test fun `A -= mem --HL`() {
//        val r = compileProc("A -= mem[--HL]")
//        r shouldHaveSize 2
//        r[0] shouldBe "dcx HL"
//        r[1] shouldBe "sub M"
//    }
//
//    @Test fun `A = mem + 1`() {
//        val r = compileProc("A = mem[0x1234] + 1")
//        r shouldHaveSize 2
//        r[0] shouldBe "lda 0x1234"
//        r[1] shouldBe "inr A"
//    }
//
//    @Test fun `A = mem - 4`() {
//        val r = compileProc("A = mem[0x1234] - 4")
//        r shouldHaveSize 2
//        r[0] shouldBe "lda 0x1234"
//        r[1] shouldBe "sui 4"
//    }
//
//    @Test fun `mem = ++HL`() {
//        val r = compileProc("mem[0x1234] = ++HL")
//        r shouldHaveSize 2
//        r[0] shouldBe "inx HL"
//        r[1] shouldBe "shld 0x1234"
//    }
//
//    @Test fun `mem = HL++`() {
//        val r = compileProc("mem[0x1234] = HL++")
//        r shouldHaveSize 2
//        r[0] shouldBe "shld 0x1234"
//        r[1] shouldBe "inx HL"
//    }
//
//    @Test fun `mem HL++ = const`() {
//        val r = compileProc("mem[HL++] = 0x20")
//        r shouldHaveSize 2
//        r[0] shouldBe "mvi M, 0x20"
//        r[1] shouldBe "inx HL"
//    }
//
//    @Test fun `mem ++HL = const`() {
//        val r = compileProc("mem[++HL] = 0x20")
//        r shouldHaveSize 2
//        r[0] shouldBe "inx HL"
//        r[1] shouldBe "mvi M, 0x20"
//    }
//
//    @Test fun `mem = ++A`() {
//        val r = compileProc("mem[0x1234] = ++A")
//        r shouldHaveSize 2
//        r[0] shouldBe "inr A"
//        r[1] shouldBe "sta 0x1234"
//    }
//
//    @Test fun `mem = A++`() {
//        val r = compileProc("mem[0x1234] = A++")
//        r shouldHaveSize 2
//        r[0] shouldBe "sta 0x1234"
//        r[1] shouldBe "inr A"
//    }
//
//    @Test fun `mem = --A`() {
//        val r = compileProc("mem[0x1234] = --A")
//        r shouldHaveSize 2
//        r[0] shouldBe "dcr A"
//        r[1] shouldBe "sta 0x1234"
//    }
//
//    @Test fun `mem = A--`() {
//        val r = compileProc("mem[0x1234] = A--")
//        r shouldHaveSize 2
//        r[0] shouldBe "sta 0x1234"
//        r[1] shouldBe "dcr A"
//    }
//
//    @Test fun `mem HL = C--`() {
//        val r = compileProc("mem[HL] = C--")
//        r shouldHaveSize 2
//        r[0] shouldBe "mov M, C"
//        r[1] shouldBe "dcr C"
//    }
//
//    @Test fun `mem ++HL = C--`() {
//        val r = compileProc("mem[++HL] = C--")
//        r shouldHaveSize 3
//        r[0] shouldBe "inx HL"
//        r[1] shouldBe "mov M, C"
//        r[2] shouldBe "dcr C"
//    }
//
//    @Test fun `C = mem HL++`() {
//        val r = compileProc("C = mem[HL++]")
//        r shouldHaveSize 2
//        r[0] shouldBe "mov C, M"
//        r[1] shouldBe "inx HL"
//    }
//
//    @Test fun `C = mem --HL`() {
//        val r = compileProc("C = mem[--HL]")
//        r shouldHaveSize 2
//        r[0] shouldBe "dcx HL"
//        r[1] shouldBe "mov C, M"
//    }
//
//    @Test fun `mem HL = C`() {
//        val r = compileProc("mem[HL] = C")
//        r shouldHaveSize 1
//        r[0] shouldBe "mov M, C"
//    }
//
//    @Test fun `mem --HL = E`() {
//        val r = compileProc("mem[--HL] = E")
//        r shouldHaveSize 2
//        r[0] shouldBe "dcx HL"
//        r[1] shouldBe "mov M, E"
//    }
//
//    @Test fun `A = bitmask`() {
//        val r = compileProc("A = bitmask(1, 2)")
//        r shouldHaveSize 1
//        r[0] shouldBe "mvi A, 0b110"
//    }
//
//    @Test fun `A = L ^ 0x20`() {
//        val r = compileProc("A = L ^ 0x20")
//        r shouldHaveSize 2
//        r[0] shouldBe "mov A, L"
//        r[1] shouldBe "xri 0x20"
//    }
//
//    @Test fun `A shl= 1`() {
//        val r = compileProc("A <<= 1")
//        r shouldHaveSize 1
//        r[0] shouldBe "rlc"
//    }
//
//    @Test fun `A shr= 1`() {
//        val r = compileProc("A >>= 1")
//        r shouldHaveSize 1
//        r[0] shouldBe "rrc"
//    }
//
//    @Test fun `A shl= 3`() {
//        val r = compileProc("A <<= 3")
//        r shouldHaveSize 3
//        r[0] shouldBe "rlc"
//        r[1] shouldBe "rlc"
//        r[2] shouldBe "rlc"
//    }
//
//    @Test fun `A = A shr 3`() {
//        val r = compileProc("A = A >> 3")
//        r shouldHaveSize 3
//        r[0] shouldBe "rrc"
//        r[1] shouldBe "rrc"
//        r[2] shouldBe "rrc"
//    }
//
//    @Test fun `HL shl= 3`() {
//        val r = compileProc("HL <<= 3")
//        r shouldHaveSize 3
//        r[0] shouldBe "dad HL"
//        r[1] shouldBe "dad HL"
//        r[2] shouldBe "dad HL"
//    }
//
//    @Test fun `A = L & 0xF`() {
//        val r = compileProc("A = L & 0xF")
//        r shouldHaveSize 2
//        r[0] shouldBe "mov A, L"
//        r[1] shouldBe "ani 0xF"
//    }
//
//    @Test fun `directive org`() {
//        val code = """
//            ${'$'}org(0x8000)
//            proc main() {
//                jmp main
//            }
//        """.trimIndent()
//        val r = compileHex(code)
//        r shouldBe ":03800000C300803A"
//    }
//
//    @Test fun `HL = deferred data block label`() {
//        val code = """
//            proc main() {
//                HL = data_block
//            }
//            data_block:
//            byte[] {
//                0x12, 0x34, 0x56
//            }
//        """.trimIndent()
//        val r = compileHex(code)
//        r shouldBe ":060000002103001234563A"
//    }
//
//    @Test fun `call proc with ignoread arguments`() {
//        val code = """
//            proc main() {
//                call	memcopy(src: HL, dest: DE, size: B)
//            }
//
//            proc memcopy(src: HL, dest: DE, size: B) {
//            }
//        """.trimIndent()
//        val v = compileCode(code)
//        v shouldHaveSize 2
//        v[0] shouldBe "call memcopy"
//        v[1] shouldBe "memcopy:"
//    }
//
//    @Test fun `A = not bitmask`() {
//        val r = compileProc("A = ~bitmask(0)")
//        r shouldHaveSize 1
//        r.first() shouldBe "mvi A, 0b11111110"
//    }
//
//    @Test fun `read port`() {
//        val r = compileProc("A = port[0x12]")
//        r shouldHaveSize 1
//        r.first() shouldBe "in 0x12"
//    }
//
//    @Test fun `write port`() {
//        val r = compileProc("port[0x34] = A")
//        r shouldHaveSize 1
//        r.first() shouldBe "out 0x34"
//    }
//
//    @Test fun `byte array with negative`() {
//        val code = """
//            byte[] {
//                -1, -2, -3
//            }
//        """.trimIndent()
//        val r = compileHex(code)
//        r shouldBe ":03000000FFFEFD03"
//    }
//
//    @Test fun `A = ~A`() {
//        val r = compileProc("A = ~A")
//        r shouldHaveSize 1
//        r.first() shouldBe "cma"
//    }
//
//    @Test fun `A = ~B`() {
//        val r = compileProc("A = ~B")
//        r shouldHaveSize 2
//        r[0] shouldBe "mov A, B"
//        r[1] shouldBe "cma"
//    }
//
//    @Test fun `A = ~byte_var`() {
//        val code = """
//            var myVar: byte absolute 0x7766
//            proc main() {
//                A = ~myVar
//            }
//        """.trimIndent()
//        val r = compileCode(code)
//        r shouldHaveSize 2
//        r[0] shouldBe "lda 0x7766"
//        r[1] shouldBe "cma"
//    }
//
//    @Test fun `A = ~var + 1 + H`() {
//        val code = """
//            var myVar: byte absolute 0x7766
//            proc main() {
//                A = ~myVar + 1 + H
//            }
//        """.trimIndent()
//        val r = compileCode(code)
//        r shouldHaveSize 4
//        r[0] shouldBe "lda 0x7766"
//        r[1] shouldBe "cma"
//        r[2] shouldBe "inr A"
//        r[3] shouldBe "add H"
//    }
//
//    @Test fun `A = -1`() {
//        val r = compileHexProc("A = -1")
//        r shouldBe ":020000003EFFC1"
//    }
//
//    @Test fun `A = ++L`() {
//        val r = compileProc("A = ++L")
//        r shouldHaveSize 2
//        r[0] shouldBe "inr L"
//        r[1] shouldBe "mov A, L"
//    }
//
//    @Test fun `A = L mul 2`() {
//        val r = compileProc("A = L*2")
//        r shouldHaveSize 2
//        r[0] shouldBe "mov A, L"
//        r[1] shouldBe "add A"
//    }
//
//    @Test fun `declare variable array`() {
//        val r = compileCode("""
//            var buf: byte[10] absolute 0x3000
//            proc main() {
//                HL = buf
//                A = sizeof(buf)
//            }
//        """.trimIndent())
//        r shouldHaveSize 2
//        r[0] shouldBe "lxi HL, 0x3000"
//        r[1] shouldBe "mvi A, 10"
//    }
//
//    @Test fun `DE = ptr`() {
//        val r = compileCode("""
//            var buf: byte[10] absolute 0x3000
//            proc main() {
//                DE = buf
//            }
//        """.trimIndent())
//        r shouldHaveSize 1
//        r[0] shouldBe "lxi DE, 0x3000"
//    }
//
//    @Test fun `D = H + 1`() {
//        val r = compileProc("""
//            D = H + 1
//        """.trimIndent())
//        r shouldHaveSize 2
//        r[0] shouldBe "mov D, H"
//        r[1] shouldBe "inr D"
//    }
//
//    @Test fun `variables`() {
//        val r = compileCode("""
//            var b1: byte absolute 0x3000
//            var b2: byte
//            var ba: byte[7]
//            var w: word
//
//            proc main() {
//                A = b1
//                A = b2
//                HL = ba
//                HL = w
//            }
//        """.trimIndent())
//        r shouldHaveSize 4
//        r[0] shouldBe "lda 0x3000"
//        r[1] shouldBe "lda 0x3001"
//        r[2] shouldBe "lxi HL, 0x3002"
//        r[3] shouldBe "lhld 0x3009"
//    }
//
//    @Test fun `A += D + 5`() {
//        val r = compileProc("A += D + 5")
//        r shouldHaveSize 2
//        r[0] shouldBe "add D"
//        r[1] shouldBe "adi 5"
//    }
//
//    @Test fun `A += D + 5 + 10`() {
//        val r = compileProc("A += D + 5 + 10")
//        r shouldHaveSize 2
//        r[0] shouldBe "add D"
//        r[1] shouldBe "adi 15"
//    }
}