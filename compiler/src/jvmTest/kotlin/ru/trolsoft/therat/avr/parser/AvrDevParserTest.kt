package ru.trolsoft.therat.avr.parser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.therat.arch.avr.parseAvrDeviceDefinitionFile
import kotlinx.io.Buffer
import ru.trolsoft.therat.avr.AvrScanner

class AvrDevParserTest {

    private fun parseDev(content: String): ru.trolsoft.therat.arch.avr.AvrDevice {
        val loader = FileLoader {
            val scanner = AvrScanner()
            val buf = Buffer().apply { write(content.toByteArray()) }
            TokenStream(scanner.scan("test.dev", buf).toMutableList(), "test.dev")
        }
        return parseAvrDeviceDefinitionFile("test.dev", loader)
    }

    @Test
    fun `parse minimal device definition`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
        """.trimIndent())

        with (device) {
            name shouldBe "atmega8"
            flashSize shouldBe 4096
            ramSize shouldBe 1024
            ramStart shouldBe 96
            eepromSize shouldBe 512
            interrupts.size() shouldBe 0
            registers shouldHaveSize 0
        }
    }

    @Test
    fun `parse device with all properties`() {
        val device = parseDev("""
            device {
                name = "atmega16"
                prog_size = 8192
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            interrupts {
                INT0 = 1
                TIMER0_COMP = 2
            }
            registers {
                SREG(0x5f) {
                    I = 7
                    T = 6
                    H = 5
                    S = 4
                    V = 3
                    N = 2
                    ZF = 1
                    C = 0
                }
            }
        """.trimIndent())

        with (device) {
            name shouldBe "atmega16"
            flashSize shouldBe 8192
            ramSize shouldBe 1024
            ramStart shouldBe 96
            eepromSize shouldBe 512
        }
    }

    // ====== Парсинг секции interrupts ======

    @Test
    fun `parse interrupts section`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            
            interrupts {
                RESET = 0
                INT0 = 1
                INT1 = 2
                TIMER2_COMP = 3
                TIMER2_OVF = 4
                TIMER1_CAPT = 5
                TIMER1_COMPA = 6
                TIMER1_COMPB = 7
                TIMER1_OVF = 8
                TIMER0_OVF = 9
                SPI_STC = 10
                UART_RX = 11
                UART_UDRE = 12
                UART_TX = 13
                ADC = 14
                EE_READY = 15
                ANA_COMP = 16
                TWI = 17
                SPM_READY = 18
            }
        """.trimIndent())

        with (device.interrupts) {
            size() shouldBe 19
            getOffset("RESET") shouldBe 0
            getOffset("INT0") shouldBe 1
            getOffset("TIMER1_OVF") shouldBe 8
            hasVector("INT0") shouldBe true
            hasVector("NONEXIST") shouldBe false
            getOffset("NONEXIST") shouldBe -1
        }
    }

    @Test
    fun `parse empty interrupts section`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            
            interrupts {
            }
        """.trimIndent())

        device.interrupts.size() shouldBe 0
    }
    
    @Test
    fun `parse registers section`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            
            registers {
                SREG(0x5f) {
                    I = 7
                    T = 6
                }
                SFIOR(0x50) {
                    TSV = 7
                }
            }
        """.trimIndent())

        device.registers shouldHaveSize 2
        
        val sreg = device.registers.find { it.name == "SREG" }
        with (sreg) {
            shouldNotBeNull()
            offset shouldBe 0x5f
            size shouldBe 1
            bits shouldHaveSize 2
            getBitNumber("I") shouldBe 7
            getBitNumber("T") shouldBe 6
            getBitNumber("NONEXIST") shouldBe -1
        }

        val sfior = device.registers.find { it.name == "SFIOR" }
        with (sfior) {
            shouldNotBeNull()
            offset shouldBe 0x50
            size shouldBe 1
            bits shouldHaveSize 1
        }
    }

    @Test
    fun `parse register without bits`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            
            registers {
                UDR(0x0c)
            }
        """.trimIndent())

        with (device) {
            registers shouldHaveSize 1
            with (registers.first()) {
                name shouldBe "UDR"
                offset shouldBe 0x0c
                size shouldBe 1
            }
            registers.first().bits.shouldBeEmpty()
        }
    }

    @Test
    fun `parse multiple registers`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            registers {
                PORTC(0x15) {
                    PC0 = 0
                    PC1 = 1
                    PC2 = 2
                    PC3 = 3
                    PC4 = 4
                    PC5 = 5
                    PC6 = 6
                    PC7 = 7
                }
                DDRC(0x14) {
                    DDC0 = 0
                    DDC1 = 1
                }
                PINC(0x13)
            }
        """.trimIndent())

        with (device) {
            registers shouldHaveSize 3
            
            with (registers.find { it.name == "PORTC" }) {
                shouldNotBeNull()
                bits shouldHaveSize 8
                getBitNumber("PC0") shouldBe 0
                getBitNumber("PC7") shouldBe 7
            }

            with (registers.find { it.name == "DDRC" }) {
                shouldNotBeNull()
                bits shouldHaveSize 2
            }

            with (registers.find { it.name == "PINC" }) {
                shouldNotBeNull()
            }
            (registers.find { it.name == "PINC" }!!).bits.shouldBeEmpty()
        }
    }

    @Test
    fun `parse empty registers section`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            
            registers {
            }
        """.trimIndent())

        device.registers shouldHaveSize 0
    }
    
    @Test
    fun `parse complete device definition with all sections`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            interrupts {
                RESET = 0
                INT0 = 1
                TIMER1_OVF = 8
            }
            registers {
                SREG(0x5f) {
                    I = 7
                    C = 0
                }
                PORTC(0x15)
                DDRC(0x14) {
                    DDC0 = 0
                }
            }
        """.trimIndent())

        with (device) {
            name shouldBe "atmega8"
            flashSize shouldBe 4096
            ramStart shouldBe 96
            ramSize shouldBe 1024
            eepromSize shouldBe 512
            interrupts.size() shouldBe 3
            registers shouldHaveSize 3
        }
    }

    @Test
    fun `error on unknown section`() {
        shouldThrow<Exception> {
            parseDev("""
                device {
                    name = "atmega8"
                    prog_size = 4096
                    ram_size = 1024
                    ram_start = 96
                    eeprom_size = 512
                }
                
                unknown_section {
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on unknown device property`() {
        shouldThrow<Exception> {
            parseDev("""
                device {
                    name = "atmega8"
                    prog_size = 4096
                    ram_size = 1024
                    ram_start = 96
                    eeprom_size = 512
                    unknown_prop = 123
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on missing equals in device block`() {
        shouldThrow<Exception> {
            parseDev("""
                device {
                    name atmega8
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on missing value in device block`() {
        shouldThrow<Exception> {
            parseDev("""
                device {
                    name =
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on non-identifier token in device block`() {
        shouldThrow<Exception> {
            parseDev("""
                device {
                    123 = 456
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on non-number interrupt offset`() {
        shouldThrow<Exception> {
            parseDev("""
                interrupts {
                    INT0 = "text"
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on bit number out of range`() {
        shouldThrow<Exception> {
            parseDev("""
                registers {
                    SREG(0x5f) {
                        I = 8
                    }
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on non-binary-operator in register bits`() {
        shouldThrow<Exception> {
            parseDev("""
                registers {
                    SREG(0x5f) {
                        I
                    }
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on missing opening bracket in registers`() {
        shouldThrow<Exception> {
            parseDev("""
                registers {
                    PORTC 0x15 1 {
                    }
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on register with three args`() {
        shouldThrow<Exception> {
            parseDev("""
                registers {
                    PORTC(0x15, 1, extra) {
                    }
                }
            """.trimIndent())
        }
    }

    @Test
    fun `error on missing device section`() {
        shouldThrow<Exception> {
            parseDev("""
                interrupts {
                    INT0 = 1
                }
            """.trimIndent())
        }
    }
    
    @Test
    fun `parse device with string prog_size`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
        """.trimIndent())

        device.flashSize shouldBe 4096
    }

    @Test
    fun `parse sections in any order`() {
        val device1 = parseDev("""
            interrupts { INT0 = 1 }
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            registers {
                SREG(0x5f) { I = 7 }
            }
        """.trimIndent())

        val device2 = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            interrupts { INT0 = 1 }
            registers {
                SREG(0x5f) { I = 7 }
            }
        """.trimIndent())

        device1.name shouldBe device2.name
        device1.interrupts.size() shouldBe device2.interrupts.size()
        device1.registers.size shouldBe device2.registers.size
    }

    @Test
    fun `parse device with only device section`() {
        val device = parseDev("""
            device {
                name = "tiny"
                prog_size = 512
                ram_size = 32
                ram_start = 0
                eeprom_size = 0
            }
        """.trimIndent())

        with (device) {
            name shouldBe "tiny"
            flashSize shouldBe 512
            ramSize shouldBe 32
            ramStart shouldBe 0
            eepromSize shouldBe 0
            interrupts.size() shouldBe 0
            registers shouldHaveSize 0
        }
    }

    @Test
    fun `parse register with multiple bits`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            registers {
                UCSRC(0x3b) {
                    URSEL = 7
                    UPM1 = 6
                    UPM0 = 5
                    USBS = 4
                    UCSZ1 = 3
                    UCSZ0 = 2
                    UCPOL = 0
                }
            }
        """.trimIndent())

        with (device.registers.find { it.name == "UCSRC" }) {
            shouldNotBeNull()
            bits shouldHaveSize 7
            getBitNumber("URSEL") shouldBe 7
            getBitNumber("UPM1") shouldBe 6
            getBitNumber("UPM0") shouldBe 5
            getBitNumber("USBS") shouldBe 4
            getBitNumber("UCSZ1") shouldBe 3
            getBitNumber("UCSZ0") shouldBe 2
            getBitNumber("UCPOL") shouldBe 0
        }
    }

    @Test
    fun `parse interrupt with zero offset`() {
        val device = parseDev("""
            device {
                name = "atmega8"
                prog_size = 4096
                ram_size = 1024
                ram_start = 96
                eeprom_size = 512
            }
            interrupts {
                RESET = 0
            }
        """.trimIndent())

        device.interrupts.size() shouldBe 1
        device.interrupts.getOffset("RESET") shouldBe 0
    }

    @Test
    fun `parse large interrupt offset`() {
        val device = parseDev("""
            device {
                name = "atmega128"
                prog_size = 65536
                ram_size = 4096
                ram_start = 256
                eeprom_size = 4096
            }
            interrupts {
                EEPROM_READY = 1
                ANALOG_COMP = 2
                TIMER2_CAPT = 3
                TIMER2_COMP = 4
                TIMER2_OVF = 5
                TIMER1_CAPT = 6
                TIMER1_COMPA = 7
                TIMER1_COMPB = 8
                TIMER1_OVF = 9
                TIMER0_OVF = 10
                SPI_STC = 11
                UART_RX = 12
                UART_UDRE = 13
                UART_TX = 14
                ADC = 15
                EE_READY = 16
            }
        """.trimIndent())

        device.interrupts.size() shouldBe 16
        device.interrupts.getOffset("TIMER1_COMPA") shouldBe 7
        device.interrupts.getOffset("UART_TX") shouldBe 14
    }
}
