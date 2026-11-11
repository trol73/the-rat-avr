package ru.trolsoft.compiler.generator

import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldNotThrowAny
import ru.trolsoft.therat.arch.CompilerOutput
import kotlin.test.Test

class CompilerOutputAdapterTest {

    private fun createAdapter(): CompilerOutput {
        return CompilerOutputAdapter()
    }

    // ============ CompilerOutputAdapter basic tests ============

    @Test
    fun `CompilerOutputAdapter should be instantiable`() {
        val adapter = createAdapter()
        adapter shouldNotBe null
    }

    @Test
    fun `CompilerOutputAdapter implements CompilerOutput`() {
        val adapter: CompilerOutput = createAdapter()
        adapter shouldNotBe null
    }

    // ============ addByte tests ============

    @Test
    fun `CompilerOutputAdapter addByte should not throw`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addByte(0xFF)
        }
    }

    @Test
    fun `CompilerOutputAdapter addByte with zero`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addByte(0)
        }
    }

    @Test
    fun `CompilerOutputAdapter addByte with positive value`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addByte(42)
        }
    }

    @Test
    fun `CompilerOutputAdapter addByte with max byte value`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addByte(255)
        }
    }

    @Test
    fun `CompilerOutputAdapter addByte with negative value`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addByte(-1)
        }
    }

    @Test
    fun `CompilerOutputAdapter addMultipleBytes`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addByte(0x00)
            adapter.addByte(0xFF)
            adapter.addByte(0x55)
            adapter.addByte(0xAA)
        }
    }

    // ============ addWord tests ============

    @Test
    fun `CompilerOutputAdapter addWord should not throw`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addWord(0xFFFF)
        }
    }

    @Test
    fun `CompilerOutputAdapter addWord with zero`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addWord(0)
        }
    }

    @Test
    fun `CompilerOutputAdapter addWord with max word value`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addWord(65535)
        }
    }

    @Test
    fun `CompilerOutputAdapter addMultipleWords`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addWord(0x0000)
            adapter.addWord(0xFFFF)
            adapter.addWord(0x1234)
        }
    }

    // ============ addDword tests ============

    @Test
    fun `CompilerOutputAdapter addDword should not throw`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addDword(0x7FFFFFFF)
        }
    }

    @Test
    fun `CompilerOutputAdapter addDword with zero`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addDword(0)
        }
    }

    @Test
    fun `CompilerOutputAdapter addDword with max dword value`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addDword(4294967295L.toInt())
        }
    }

    @Test
    fun `CompilerOutputAdapter addMultipleDwords`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addDword(0x00000000)
            adapter.addDword(0x7FFFFFFF)
            adapter.addDword(0x12345678)
        }
    }

    // ============ Mixed operations tests ============

    @Test
    fun `CompilerOutputAdapter mixed add operations should not throw`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addByte(0x01)
            adapter.addWord(0x0203)
            adapter.addDword(0x04050607)
            adapter.addByte(0x08)
            adapter.addWord(0x0910)
        }
    }

    @Test
    fun `CompilerOutputAdapter many operations should not throw`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            repeat(100) { i ->
                adapter.addByte(i.toByte().toInt())
                adapter.addWord(i)
                adapter.addDword(i)
            }
        }
    }

    // ============ No-op behavior tests ============

    @Test
    fun `CompilerOutputAdapter should be a no-op adapter`() {
        val adapter = createAdapter()
        // All methods should be no-ops - they don't store or process data
        // Just verify they don't throw
        adapter.addByte(0xFF)
        adapter.addWord(0xFFFF)
        adapter.addDword(0x7FFFFFFF)
        // No state to verify, just ensuring no exceptions
    }

    @Test
    fun `CompilerOutputAdapter multiple instances should be independent`() {
        val adapter1 = createAdapter()
        val adapter2 = createAdapter()

        shouldNotThrowAny {
            adapter1.addByte(0x01)
            adapter2.addByte(0x02)
            adapter1.addWord(0x0304)
            adapter2.addWord(0x0506)
        }
    }

    @Test
    fun `CompilerOutputAdapter should handle edge case values`() {
        val adapter = createAdapter()
        shouldNotThrowAny {
            adapter.addByte(Int.MIN_VALUE)
            adapter.addWord(Int.MIN_VALUE)
            adapter.addDword(Int.MIN_VALUE)
            adapter.addByte(Int.MAX_VALUE)
            adapter.addWord(Int.MAX_VALUE)
            adapter.addDword(Int.MAX_VALUE)
        }
    }
}
