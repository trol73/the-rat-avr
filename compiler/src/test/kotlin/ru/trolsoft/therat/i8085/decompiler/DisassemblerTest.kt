package ru.trolsoft.therat.i8085.decompiler

import io.kotest.matchers.shouldBe
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import ru.trolsoft.compiler.utils.RkrInputStream
import kotlin.test.Test


class DisassemblerTest {
    private val dec = I8085Disassembler()

    @Test fun decompileAll() {
        val resPath = Path("src/jvmTest/resources/8080/86rk/soft")
        var errCnt = 0
        for (filePath in SystemFileSystem.list(resPath))
            try {
                SystemFileSystem.source(filePath).buffered().use {
                    RkrInputStream(it)
                }
            } catch (e: Exception) {
                errCnt++
                println("${filePath} ${e.message} $errCnt")
            }
    }


    @Test fun decompileRom() {
        val entryPoints = listOf(
            0xf800,
            0xf803,
            0xf806,
            0xf809,
            0xf80C,
            0xf80F,
            0xf812,
            0xf815,
            0xf818,
            0xf81B,
            0xf81E,
            0xf821,
            0xf824,
            0xf827,
            0xf82A,
            0xf82D,
            0xf830,
            0xf833,
            0xFFA2,

            0xFD85

//            extern proc biosReadKeyboard() absolute 0xF803
//            extern proc biosPrintChar(chr: C) absolute 0xF809
//            extern proc biosPrintMessage(msg: HL) absolute 0xF818
//            extern proc biosCheckKeyboard() absolute 0xF81B	; Ввод кода нажатой клавиши (A=0FFH - не нажата, A=0FEH - РУС/ЛАТ, ИНАЧЕ - код клавиши)

            // F803H    Ввод символа с клавиатуры       А - введенный код
            // F806Н    Ввод байта с магнитофона        А=0FFН с поиском синхробайта    A=08Н без поиска синхробайта    А - введенный байт
            // F809Н    Вывод символа на экран          С - выводимый символ
            // F80CH    Запись байта на магнитофон      С - выводимый байт
        )
        val options = Options(
            romOffset = 0xf800,
            entryPoints = entryPoints,
            ignorePoints = listOf(),
            optimizeZero = true,
            optimizeIncDec = true,
            ratChains = true
        )
        I8085Decompiler(false).loadFile("src/jvmTest/resources/8080/86rk/rom/RK86-32.rom", options)
    }

    @Test
    fun decompileUt88() {
        val entryPoints = listOf(
//            0x0000,
//            0x01E1,
//            0x01E8,
//            0x0212,
//            0x025E,
//            0x0265,
//            0x01DA,
//            0x0800,
//            0x1000,
//            0x100D,
//            0x1024

//            0xf800,
//            0xf803,
//            0xf806,
//            0xf809,
//            0xf80C,
//            0xf80F,
//            0xf812,
//            0xf815,
//            0xf818,
//            0xf81B,
//            0xf81E,
//            0xf821,
//            0xf824,
//            0xf827,
//            0xf82A,
//            0xf82D,
//            0xf830,
//            0xf833,

//            0x3000,
//            0x3038,
//            0x30B0,
//            0x30B3,
//            0x30B6,
//            0x30B6,
//
//            0x30B9,
//            0x30BC,
//            0x30BF,
//            0x30C2,
//            0x30C5,
//            0x30C8,
//            0x30CB,
//            0x311B,
//            0x3141,
//            0x318A,
//            0x3192,
//            0x3195,
//            0x31A0,
//            0x31BD,
//            0x31CF,
//            0x31DB,
//            0x31E4,
//            0x323B,
//            0x3263,
//            0x3270,
//            0x3281,
//            0x328D,
//            0x3299,
//            0x32A0,
//            0x32BC,
//            0x32C6,
//            0x32CE,
//            0x32E3,
//            0x32F0,
//            0x32FF,
//            0x330A,
//            0x3333,
//            0x333B,
//            0x334C,
//            0x3393,
//            0x339C,
//            0x33B8,
//            0x33EE,
//            0x33FB,
//            0x3403,
//            0x3412,
//            0x341E,
//            0x343D,
//            0x3451,
//            0x346F,
//            0x3476,
//            0x3489,

//            0x30D1,
//            0x30D4,
//            0x30D7,
//            0x30DA,
//            0x30DD,
//            0x30E0,
//            0x30E3,

            0xf800,
            0xF838,
            0xF83B,
            0xF8B0,
            0xF8B3,

            0xF8B6,
            0xf8B9,
            0xf8BC,
            0xf8BF,
            0xf8C2,
            0xf8C5,
            0xf8C8,
            0xf8CB,

            0xF91B,
            0xF98A,
            0xF941,
            0xF992,
            0xF995,
            0xF9A0,
            0xF9BD,
            0xF9DB,
            0xF9CF,
            0xF9E4,

            0xFFF7,
            0xFFF8,
//            0xf8CE,
//            0xf8D1,
//            0xf8D4,
//            0xf8D7,
//            0xf8DA,

        )
        val ignorePoints = mutableListOf(
            0xF865,
            0xF86B,
            0xF876,
            0xFA8B
        )
        for (i in 0xf840..0xf8a9)
            ignorePoints.add(i)

        //I8085Decompiler(false).loadFile("/Users/trol/dev/rat/bin/ut88/D201 (27512).bin", 0x0000, entryPoints)
        //I8085Decompiler(false).loadFile("/Users/trol/dev/rat/ut88/bin/unknown.bin", 0x3000, entryPoints)
        //I8085Decompiler(false).loadFile("/Users/trol/dev/rat/ut88/bin/unknown.bin", 0xf800, entryPoints, ignorePoints)

    }

    @Test fun `LXI SP, 0x75ff`() {
        val r = dec.decompileNext(data(0x31, 0xff, 0x75))
        r.toString() shouldBe "lxi\tSP, 0x75FF"
    }

    @Test fun `RST 7`() {
        val r = dec.decompileNext(data(0xFF))
        r.toString() shouldBe "rst\t7"
    }

//    @Test fun testRkr() {
//        val rkr = RkrInputStream(FileInputStream("/Users/trol/tetris6.gam"))
//
//        println("START: ${rkr.getStartAddress().toString(16)}")
//        println("END: ${rkr.getEndAddress().toString(16)}")
//        println("CRC: ${rkr.getCrc().toString(16)}\n")
//        while (true) {
//            println("${hexWord(rkr.getAddress())}: ${dec.decompileNext(rkr)}")
//            //println("${dec.decompileNext(rkr)}")
//            if (rkr.eof()) break
//        }
//    }

//    @Test fun testDecompiler() {
//        val options = Options(
//            romOffset = 0,
//            entryPoints = listOf(),
//            ignorePoints = listOf(),
//            optimizeZero = true,
//            optimizeIncDec = true,
//            ratChains = true
//        )
//        I8085Decompiler(false).loadFile("/Users/trol/tetris6.gam", options)
//    }

//    @Test fun testUT88Tetris() {
//        val options = Options(
//            romOffset = 0,
//            entryPoints = listOf(),
//            ignorePoints = listOf(),
//            optimizeZero = true,
//            optimizeIncDec = true,
//            ratChains = true
//        )
//        val out = I8085Decompiler(false).loadFile("/Users/trol/dev/rat/ut88/tetris/TETRIS.RKU", options)
//        for (s in out)
//            println(s)
//    }

//    @Test fun ttt() {
//        compileIntel808x(
//            "/Users/trol/dev/rat/ut88/tetris/tetris.8080",
//            "/Users/trol/dev/rat/ut88/tetris/t.rkr",
//            false,
//            "/Users/trol/bin/avr-builder/tools/encodings/",
//            listOf(),
//            System.out, System.err, null
//        )
//    }

    private fun data(vararg bytes: Int) = Buffer().apply { write(bytes.map { x -> x.toByte() }.toByteArray()) }


}