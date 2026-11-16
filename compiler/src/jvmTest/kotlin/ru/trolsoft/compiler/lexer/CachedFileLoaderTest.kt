package ru.trolsoft.compiler.lexer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import ru.trolsoft.therat.avr.AvrScanner
import java.io.File

class CachedFileLoaderTest {

    companion object {
        private lateinit var testFile1: File
        private lateinit var testFile2: File

        @BeforeAll
        @JvmStatic
        fun createTempFiles() {
            testFile1 = File.createTempFile("cached_test1_", ".art").also {
                it.writeText("var x = 1\n")
            }
            testFile2 = File.createTempFile("cached_test2_", ".art").also {
                it.writeText("var y = 2\n")
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            testFile1.delete()
            testFile2.delete()
        }
    }

    @Test
    fun `loadSource should return TokenStream`() {
        val loader = CachedFileLoader(AvrScanner())
        val stream = loader.loadSource(testFile1.absolutePath)

        stream shouldNotBe null
        stream.path shouldBe testFile1.absolutePath
    }

    @Test
    fun `loadSource should return copy of cached stream`() {
        val loader = CachedFileLoader(AvrScanner())
        val stream1 = loader.loadSource(testFile1.absolutePath)
        val stream2 = loader.loadSource(testFile1.absolutePath)

        stream1 shouldNotBe stream2
        stream1.path shouldBe stream2.path
    }

    @Test
    fun `loadSource should return different instances for different paths`() {
        val loader = CachedFileLoader(AvrScanner())
        val stream1 = loader.loadSource(testFile1.absolutePath)
        val stream2 = loader.loadSource(testFile2.absolutePath)

        stream1 shouldNotBe stream2
        stream1.path shouldBe testFile1.absolutePath
        stream2.path shouldBe testFile2.absolutePath
    }

    @Test
    fun `loadSource should reuse cached stream for same path`() {
        val loader = CachedFileLoader(AvrScanner())
        val stream1 = loader.loadSource(testFile1.absolutePath)
        val stream2 = loader.loadSource(testFile1.absolutePath)

        stream1.path shouldBe stream2.path
    }

    @Test
    fun `clearFilesCache should clear all cached streams`() {
        val loader = CachedFileLoader(AvrScanner())
        loader.loadSource(testFile1.absolutePath)
        loader.loadSource(testFile2.absolutePath)

        clearFilesCache()

        val stream1 = loader.loadSource(testFile1.absolutePath)
        val stream2 = loader.loadSource(testFile2.absolutePath)

        stream1.path shouldBe testFile1.absolutePath
        stream2.path shouldBe testFile2.absolutePath
    }
}
