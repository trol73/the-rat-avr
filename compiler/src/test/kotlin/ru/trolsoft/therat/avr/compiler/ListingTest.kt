package ru.trolsoft.therat.avr.compiler

import io.kotest.matchers.shouldBe
import ru.trolsoft.compiler.generator.ListingRec
import kotlin.test.Test

class ListingTest {
    @Test
    fun `simple listing`() {
        val list = compile(
            $$"""#define CPU "atmega8"
            
            $output("firmware.hex")
            
            #define CA 1
            use r21 as a
            
            proc main() {
              a = CA
              ret
            }
            
            proc proc2() {
               jmp main
            }
        """.trimIndent()).map { it.toString() }
        list[0] shouldBe "file.art:8\t00000000: main: ; 4 bytes"
        list[1] shouldBe "file.art:9\t00000000: ldi    R21, 1"
        list[2] shouldBe "file.art:10\t00000002: ret"
        list[3] shouldBe "file.art:13\t00000004: proc2: ; 4 bytes"
        list[4] shouldBe "file.art:14\t00000004: jmp    main"
    }

    @Test
    fun `gcc listing`() {
        val list = compile("""
            extern proc extproc()
            
            #define CPU "atmega8"
            #define CA 1
            use r21 as a
            
            proc main() {
              a = CA
              ret
            }
            
            proc proc2() {
               jmp extproc
            }
        """.trimIndent(), gcc = true).map { it.toString() }
        list[0] shouldBe "file.art:7\t00000000: main: ; 4 bytes"
        list[1] shouldBe "file.art:8\t00000000: ldi    R21, 1"
        list[2] shouldBe "file.art:9\t00000002: ret"
        list[3] shouldBe "file.art:12\t00000004: proc2: ; 4 bytes"
        list[4] shouldBe "file.art:13\t00000004: jmp    extproc"
    }

    @Test
    fun `gcc listing with externals`() {
        val list = compile("""
            extern proc extproc()
            extern var extvar: word
            
            proc main() {
              Z = extproc
              X = extvar
            }
        """.trimIndent(), gcc = true).map { it.toString() }
        list[0] shouldBe "file.art:4\t00000000: main: ; 12 bytes"
        list[1] shouldBe "file.art:5\t00000000: ldi    R31, hi8(extproc)"
        list[2] shouldBe "file.art:5\t00000002: ldi    R30, lo8(extproc)"
        list[3] shouldBe "file.art:6\t00000004: lds    R27, extvar+1"
        list[4] shouldBe "file.art:6\t00000008: lds    R26, extvar"
    }

    @Test
    fun `listing toString with db directive`() {
        val lines = compileToStrings(
            $$"""
            #define CPU "atmega8"
            proc main() {
                byte[] { 42 }
                ret
            }
        """.trimIndent())

        lines[0] shouldBe "file.art:2\t00000000: main: ; 4 bytes"
        lines[1] shouldBe "file.art:3\t00000000: .db 0x2A"
        lines[2] shouldBe "file.art:3\t00000001: .db 0x00"
        lines[3] shouldBe "file.art:4\t00000002: ret"
    }

    @Test
    fun `listing toString with multiple db directives`() {
        val lines = compileToStrings(
            $$"""
            #define CPU "atmega8"
            proc main() {
                byte[] { 0, 255, 128, 1 }
                ret
            }
        """.trimIndent())

        lines[0] shouldBe "file.art:2\t00000000: main: ; 6 bytes"
        lines[1] shouldBe "file.art:3\t00000000: .db 0x00"
        lines[2] shouldBe "file.art:3\t00000001: .db 0xFF"
        lines[3] shouldBe "file.art:3\t00000002: .db 0x80"
        lines[4] shouldBe "file.art:3\t00000003: .db 0x01"
        lines[5] shouldBe "file.art:4\t00000004: ret"
    }

    // ── addLabel with ?probe_ prefix — should be skipped ───────────

    @Test
    fun `listing toString skips probe labels`() {
        val lines = compileToStrings(
            $$"""
            #define CPU "atmega8"
            proc main() {
                r24 = 1
            }
        """.trimIndent())

        lines[0] shouldBe "file.art:2\t00000000: main: ; 2 bytes"
        lines.none { it.contains("?probe_") } shouldBe true
    }

    // ── commandDump with binaryGenerator == null — no hex dump ─────

    @Test
    fun `listing toString with no hex dump area`() {
        val lines = compileToStrings(
            $$"""
            #define CPU "atmega8"
            proc main() {
                r24 = 42
                ret
            }
        """.trimIndent())

        // No hex dump between offset and asmCode
        lines[0] shouldBe "file.art:2\t00000000: main: ; 4 bytes"
        lines[1] shouldBe "file.art:3\t00000000: ldi    R24, 42"
        lines[2] shouldBe "file.art:4\t00000002: ret"
    }

    // ── Multiple command types ─────────────────────────────────────

    @Test
    fun `listing toString with various commands`() {
        val lines = compileToStrings(
            $$"""
            #define CPU "atmega8"
            proc main() {
                r24 = 1
                r25 = 2
                r24 = r24 + r25
                r24 = r24 - r25
                ret
            }
        """.trimIndent())

        lines[0] shouldBe "file.art:2\t00000000: main: ; 10 bytes"
        lines[1] shouldBe "file.art:3\t00000000: ldi    R24, 1"
        lines[2] shouldBe "file.art:4\t00000002: ldi    R25, 2"
        lines[3] shouldBe "file.art:5\t00000004: add    R24, R25"
        lines[4] shouldBe "file.art:6\t00000006: sub    R24, R25"
        lines[5] shouldBe "file.art:7\t00000008: ret"
    }

    // ── Offset progression ─────────────────────────────────────────

    @Test
    fun `listing toString offset for 2-byte commands`() {
        val lines = compileToStrings(
            $$"""
            #define CPU "atmega8"
            proc main() {
                r24 = 1
                r24 = 2
                r24 = 3
                ret
            }
        """.trimIndent())

        // Each ldi is 2 bytes
        lines[0] shouldBe "file.art:2\t00000000: main: ; 8 bytes"
        lines[1] shouldBe "file.art:3\t00000000: ldi    R24, 1"
        lines[2] shouldBe "file.art:4\t00000002: ldi    R24, 2"
        lines[3] shouldBe "file.art:5\t00000004: ldi    R24, 3"
        lines[4] shouldBe "file.art:6\t00000006: ret"
    }

    // ── Label formatting ───────────────────────────────────────────

    @Test
    fun `listing toString label format`() {
        val lines = compileToStrings(
            $$"""
            #define CPU "atmega8"
            proc main() {
                r24 = 1
            }
            proc proc2() {
                jmp main
            }
        """.trimIndent())

        lines[0] shouldBe "file.art:2\t00000000: main: ; 2 bytes"
        lines[2] shouldBe "file.art:5\t00000002: proc2: ; 4 bytes"
        lines[3] shouldBe "file.art:6\t00000002: jmp    main"
    }

    @Test
    fun `listing toString with named label`() {
        val lines = compileToStrings(
            $$"""
            #define CPU "atmega8"
            proc main() {
                @loop:
                r24 = r24 - 1
                brne loop
                ret
            }
        """.trimIndent())

        lines[0] shouldBe "file.art:2\t00000000: main: ; 6 bytes"
        lines[1] shouldBe "file.art:3\t00000000: loop:"
        lines[2] shouldBe "file.art:4\t00000000: dec    R24"
        lines[3] shouldBe "file.art:5\t00000002: brne   main@loop ; loop"
        lines[4] shouldBe "file.art:6\t00000004: ret"
    }

    // ── GCC mode ───────────────────────────────────────────────────

    @Test
    fun `listing toString gcc mode`() {
        val listing = mutableListOf<ListingRec>()
        compileCode(
            $$"""
            extern proc extproc()
            #define CPU "atmega8"
            proc main() {
                Z = extproc
            }
        """.trimIndent(),
            gcc = true,
            listing = listing
        )
        val lines = listing.map { it.toString() }

        lines[0] shouldBe "file.art:3\t00000000: main: ; 4 bytes"
        lines[1] shouldBe "file.art:4\t00000000: ldi    R31, hi8(extproc)"
        lines[2] shouldBe "file.art:4\t00000002: ldi    R30, lo8(extproc)"
    }

    // ── Complex code ───────────────────────────────────────────────

    @Test
    fun `listing toString complex procedure`() {
        val lines = compileToStrings(
            $$"""
            #define CPU "atmega8"
            proc main() {
                r24 = 0
                @loop:
                r24 = r24 + 1
                cpi r24, 10
                brne loop
                ret
            }
        """.trimIndent())

        lines[0] shouldBe "file.art:2\t00000000: main: ; 10 bytes"
        lines[1] shouldBe "file.art:3\t00000000: clr    R24"
        lines[2] shouldBe "file.art:4\t00000002: loop:"
        lines[3] shouldBe "file.art:5\t00000002: inc    R24"
        lines[4] shouldBe "file.art:6\t00000004: cpi    R24, 10"
        lines[5] shouldBe "file.art:7\t00000006: brne   main@loop ; loop"
        lines[6] shouldBe "file.art:8\t00000008: ret"
    }
    private fun compile(code: String, gcc: Boolean = false): List<ListingRec> {
        val listing = mutableListOf<ListingRec>()
        compileCode(code, gcc = gcc, listing = listing)
        return listing
    }

    private fun compileToStrings(code: String): List<String> {
        return compile(code).map { it.toString() }
    }

}