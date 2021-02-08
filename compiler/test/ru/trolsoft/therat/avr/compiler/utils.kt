package ru.trolsoft.therat.avr.compiler

import ru.trolsoft.compiler.generator.Endian
import ru.trolsoft.compiler.lexer.FileLoader
import ru.trolsoft.compiler.lexer.TokenStream
import ru.trolsoft.therat.avr.AvrRatCompiler
import ru.trolsoft.therat.avr.AvrScanner
import ru.trolsoft.therat.lang.Scanner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream


class TestFileLoaderStub(private val text: String) : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = AvrScanner()
        val stream = ByteArrayInputStream(text.toByteArray())
        return TokenStream(scanner.scan(path, stream).toMutableList(), path)
    }
}

class TestDevFileLoaderStub : FileLoader {
    override fun loadSource(path: String): TokenStream {
        val scanner = Scanner()
        val stream = ByteArrayInputStream(mega8Dev.toByteArray())
        return TokenStream(scanner.scan(path, stream).toMutableList(), path)
    }
}


fun compile(expr: String, initCode: String = ""): List<String> {
    val code = "$initCode\nproc main() {\n$expr\n}"
    return compileCode(code)
}


fun compileCode(code: String, gcc: Boolean = false): List<String> {
    val loader = TestFileLoaderStub(code)
    val compiler =  AvrRatCompiler("", loader)
    compiler.deviceLoader = TestDevFileLoaderStub()
    compiler.addAsmGenerator()
    compiler.addGccAsmGenerator()
    compiler.compile("file.art")
    val outStream = ByteArrayOutputStream()
    if (gcc) {
        compiler.saveGccAsm(outStream)
    } else {
        compiler.saveAsm(outStream)
    }
    val out = outStream.toString().split("\n")
    val res = mutableListOf<String>()
    for (s in out) {
        if (res.size == 0 && s == "main:") {
            continue
        }
        if (s.isEmpty()) {
            continue
        }
        var cleaned = s.replace('\t', ' ').trim()
        if (!gcc) {
            cleaned = cleaned.toLowerCase()
        }
        while (cleaned.contains("  ")) {
            cleaned = cleaned.replace("  ", " ")
        }
        res.add(cleaned)
    }

    return res
}

fun compileHex(code: String): String {
    val loader = TestFileLoaderStub(code)
    val compiler =  AvrRatCompiler("", loader)
    compiler.deviceLoader = TestDevFileLoaderStub()
    compiler.addIntelHexGenerator(Endian.LITTLE)
    compiler.compile("file.art")
    val outStream = ByteArrayOutputStream()
    compiler.saveHex(outStream)
    return outStream.toString()
}

fun compileM8(expr: String): List<String> = compile(expr, "#define CPU \"mega8\"")

private const val mega8Dev = """
device {
	name = "ATmega8"
	prog_size = 0x2000
	eeprom_size = 0x0200
	ram_size = 0x0400
	ram_start = 0x0060
}

interrupts {
	RESET = 0x00            ; External Pin, Power-on Reset, Brown-out Reset and Watchdog Reset
	INT0 = 0x01             ; External Interrupt Request 0
	INT1 = 0x02             ; External Interrupt Request 1
	TIMER2_COMP = 0x03      ; Timer/Counter2 Compare Match
	TIMER2_OVF = 0x04       ; Timer/Counter2 Overflow
	TIMER1_CAPT = 0x05      ; Timer/Counter1 Capture Event
	TIMER1_COMPA = 0x06     ; Timer/Counter1 Compare Match A
	TIMER1_COMPB = 0x07     ; Timer/Counter1 Compare Match B
	TIMER1_OVF = 0x08       ; Timer/Counter1 Overflow
	TIMER0_OVF = 0x09       ; Timer/Counter0 Overflow
	SPI_STC = 0x0a          ; Serial Transfer Complete
	USART_RXC = 0x0b        ; USART, Rx Complete
	USART_UDRE = 0x0c       ; USART Data Register Empty
	USART_TXC = 0x0d        ; USART, Tx Complete
	ADC = 0x0e              ; ADC Conversion Complete
	EE_RDY = 0x0f           ; EEPROM Ready
	ANA_COMP = 0x10         ; Analog Comparator
	TWI = 0x11              ; 2-wire Serial Interface
	SPM_RDY = 0x12          ; Store Program Memory Ready
}

registers {
	TWBR(0x00)              ; TWI Bit Rate register
	TWSR(0x01) {            ; TWI Status Register
		TWPS0 = 0               ; TWI Prescaler bit 0
		TWPS1 = 1               ; TWI Prescaler bit 1
		TWS0 = 3                ; TWI Status bit 0
		TWS1 = 4                ; TWI Status bit 1
		TWS2 = 5                ; TWI Status bit 2
		TWS3 = 6                ; TWI Status bit 3
		TWS4 = 7                ; TWI Status bit 4
	}
	TWAR(0x02) {            ; TWI (Slave) Address register
		TWGCE = 0               ; TWI General Call Recognition Enable Bit
		TWA0 = 1                ; TWI (Slave) Address register Bits bit 0
		TWA1 = 2                ; TWI (Slave) Address register Bits bit 1
		TWA2 = 3                ; TWI (Slave) Address register Bits bit 2
		TWA3 = 4                ; TWI (Slave) Address register Bits bit 3
		TWA4 = 5                ; TWI (Slave) Address register Bits bit 4
		TWA5 = 6                ; TWI (Slave) Address register Bits bit 5
		TWA6 = 7                ; TWI (Slave) Address register Bits bit 6
	}
	TWDR(0x03)              ; TWI Data register
	ADC(0x04, 0x05)         ; ADC Data Register  Bytes
	ADCSRA(0x06) {          ; The ADC Control and Status register
		ADPS0 = 0               ; ADC  Prescaler Select Bits bit 0
		ADPS1 = 1               ; ADC  Prescaler Select Bits bit 1
		ADPS2 = 2               ; ADC  Prescaler Select Bits bit 2
		ADIE = 3                ; ADC Interrupt Enable
		ADIF = 4                ; ADC Interrupt Flag
		ADFR = 5                ; ADC  Free Running Select
		ADSC = 6                ; ADC Start Conversion
		ADEN = 7                ; ADC Enable
	}
	ADMUX(0x07) {           ; The ADC multiplexer Selection Register
		MUX0 = 0                ; Analog Channel and Gain Selection Bits bit 0
		MUX1 = 1                ; Analog Channel and Gain Selection Bits bit 1
		MUX2 = 2                ; Analog Channel and Gain Selection Bits bit 2
		MUX3 = 3                ; Analog Channel and Gain Selection Bits bit 3
		ADLAR = 5               ; Left Adjust Result
		REFS0 = 6               ; Reference Selection Bits bit 0
		REFS1 = 7               ; Reference Selection Bits bit 1
	}
	ACSR(0x08) {            ; Analog Comparator Control And Status Register
		ACIS0 = 0               ; Analog Comparator Interrupt Mode Select bits bit 0
		ACIS1 = 1               ; Analog Comparator Interrupt Mode Select bits bit 1
		ACIC = 2                ; Analog Comparator Input Capture Enable
		ACIE = 3                ; Analog Comparator Interrupt Enable
		ACI = 4                 ; Analog Comparator Interrupt Flag
		ACO = 5                 ; Analog Compare Output
		ACBG = 6                ; Analog Comparator Bandgap Select
		ACD = 7                 ; Analog Comparator Disable
	}
	UBRRL(0x09)             ; USART Baud Rate Register Low Byte
	UCSRB(0x0a) {           ; USART Control and Status Register B
		TXB8 = 0                ; Transmit Data Bit 8
		RXB8 = 1                ; Receive Data Bit 8
		UCSZ2 = 2               ; Character Size
		TXEN = 3                ; Transmitter Enable
		RXEN = 4                ; Receiver Enable
		UDRIE = 5               ; USART Data register Empty Interrupt Enable
		TXCIE = 6               ; TX Complete Interrupt Enable
		RXCIE = 7               ; RX Complete Interrupt Enable
	}
	UCSRA(0x0b) {           ; USART Control and Status Register A
		MPCM = 0                ; Multi-processor Communication Mode
		U2X = 1                 ; Double the USART transmission speed
		UPE = 2                 ; Parity Error
		DOR = 3                 ; Data overRun
		FE = 4                  ; Framing Error
		UDRE = 5                ; USART Data Register Empty
		TXC = 6                 ; USART Transmitt Complete
		RXC = 7                 ; USART Receive Complete
	}
	UDR(0x0c)               ; USART I/O Data Register
	SPCR(0x0d) {            ; SPI Control Register
		SPR0 = 0                ; SPI Clock Rate Selects bit 0
		SPR1 = 1                ; SPI Clock Rate Selects bit 1
		CPHA = 2                ; Clock Phase
		CPOL = 3                ; Clock polarity
		MSTR = 4                ; Master/Slave Select
		DORD = 5                ; Data Order
		SPE = 6                 ; SPI Enable
		SPIE = 7                ; SPI Interrupt Enable
	}
	SPSR(0x0e) {            ; SPI Status Register
		SPI2X = 0               ; Double SPI Speed Bit
		WCOL = 6                ; Write Collision Flag
		SPIF = 7                ; SPI Interrupt Flag
	}
	SPDR(0x0f)              ; SPI Data Register
	PIND(0x10)              ; Port D Input Pins
	DDRD(0x11)              ; Port D Data Direction Register
	PORTD(0x12)             ; Port D Data Register
	PINC(0x13)              ; Port C Input Pins
	DDRC(0x14)              ; Port C Data Direction Register
	PORTC(0x15)             ; Port C Data Register
	PINB(0x16)              ; Port B Input Pins
	DDRB(0x17)              ; Port B Data Direction Register
	PORTB(0x18)             ; Port B Data Register
	EECR(0x1c) {            ; EEPROM Control Register
		EERE = 0                ; EEPROM Read Enable
		EEWE = 1                ; EEPROM Write Enable
		EEMWE = 2               ; EEPROM Master Write Enable
		EERIE = 3               ; EEPROM Ready Interrupt Enable
	}
	EEDR(0x1d)              ; EEPROM Data Register
	EEAR(0x1e, 0x1f)        ; EEPROM Address Register  Bytes
	UCSRC(0x20) {           ; USART Control and Status Register C
		UCPOL = 0               ; Clock Polarity
		UCSZ0 = 1               ; Character Size bit 0
		UCSZ1 = 2               ; Character Size bit 1
		USBS = 3                ; Stop Bit Select
		UPM0 = 4                ; Parity Mode Bits bit 0
		UPM1 = 5                ; Parity Mode Bits bit 1
		UMSEL = 6               ; USART Mode Select
		URSEL = 7               ; Register Select
	}
	UBRRH(0x20)             ; USART Baud Rate Register Hight Byte
	WDTCR(0x21) {           ; Watchdog Timer Control Register
		WDP0 = 0                ; Watch Dog Timer Prescaler bits bit 0
		WDP1 = 1                ; Watch Dog Timer Prescaler bits bit 1
		WDP2 = 2                ; Watch Dog Timer Prescaler bits bit 2
		WDE = 3                 ; Watch Dog Enable
		WDCE = 4                ; Watchdog Change Enable
	}
	ASSR(0x22) {            ; Asynchronous Status Register
		TCR2UB = 0              ; Timer/counter Control Register2 Update Busy
		OCR2UB = 1              ; Output Compare Register2 Update Busy
		TCN2UB = 2              ; Timer/Counter2 Update Busy
		AS2 = 3                 ; Asynchronous Timer/counter2
	}
	OCR2(0x23)              ; Timer/Counter2 Output Compare Register
	TCNT2(0x24)             ; Timer/Counter2
	TCCR2(0x25) {           ; Timer/Counter2 Control Register
		CS20 = 0                ; Clock Select bits bit 0
		CS21 = 1                ; Clock Select bits bit 1
		CS22 = 2                ; Clock Select bits bit 2
		WGM21 = 3               ; Waveform Generation Mode
		COM20 = 4               ; Compare Output Mode bits bit 0
		COM21 = 5               ; Compare Output Mode bits bit 1
		WGM20 = 6               ; Waveform Genration Mode
		FOC2 = 7                ; Force Output Compare
	}
	ICR1(0x26, 0x27)        ; Timer/Counter1 Input Capture Register  Bytes
	OCR1B(0x28, 0x29)       ; Timer/Counter1 Output Compare Register  Bytes
	OCR1A(0x2a, 0x2b)       ; Timer/Counter1 Output Compare Register  Bytes
	TCNT1(0x2c, 0x2d)       ; Timer/Counter1  Bytes
	TCCR1B(0x2e) {          ; Timer/Counter1 Control Register B
		CS10 = 0                ; Prescaler source of Timer/Counter 1 bit 0
		CS11 = 1                ; Prescaler source of Timer/Counter 1 bit 1
		CS12 = 2                ; Prescaler source of Timer/Counter 1 bit 2
		WGM10 = 3               ; Waveform Generation Mode bit 0
		WGM11 = 4               ; Waveform Generation Mode bit 1
		ICES1 = 6               ; Input Capture 1 Edge Select
		ICNC1 = 7               ; Input Capture 1 Noise Canceler
	}
	TCCR1A(0x2f) {          ; Timer/Counter1 Control Register A
		WGM10 = 0               ; Waveform Generation Mode bit 0
		WGM11 = 1               ; Waveform Generation Mode bit 1
		FOC1B = 2               ; Force Output Compare 1B
		FOC1A = 3               ; Force Output Compare 1A
		COM1B0 = 4              ; Compare Output Mode 1B, bits bit 0
		COM1B1 = 5              ; Compare Output Mode 1B, bits bit 1
		COM1A0 = 6              ; Compare Output Mode 1A, bits bit 0
		COM1A1 = 7              ; Compare Output Mode 1A, bits bit 1
	}
	SFIOR(0x30) {           ; Special Function IO Register
		PSR10 = 0               ; Prescaler Reset Timer/Counter1 and Timer/Counter0
		PSR2 = 1                ; Prescaler Reset Timer/Counter2
		PUD = 2                 ; Pull-up Disable
		ACME = 3                ; Analog Comparator Multiplexer Enable
		ADHSM = 4               ; ADC High Speed Mode
	}
	OSCCAL(0x31) {          ; Oscillator Calibration Value
		OSCCAL0 = 0             ; Oscillator Calibration  bit 0
		OSCCAL1 = 1             ; Oscillator Calibration  bit 1
		OSCCAL2 = 2             ; Oscillator Calibration  bit 2
		OSCCAL3 = 3             ; Oscillator Calibration  bit 3
		OSCCAL4 = 4             ; Oscillator Calibration  bit 4
		OSCCAL5 = 5             ; Oscillator Calibration  bit 5
		OSCCAL6 = 6             ; Oscillator Calibration  bit 6
		OSCCAL7 = 7             ; Oscillator Calibration  bit 7
	}
	TCNT0(0x32)             ; Timer Counter 0
	TCCR0(0x33) {           ; Timer/Counter0 Control Register
		CS00 = 0                ; Clock Select0 bit 0
		CS01 = 1                ; Clock Select0 bit 1
		CS02 = 2                ; Clock Select0 bit 2
	}
	MCUCSR(0x34) {          ; MCU Control And Status Register
		PORF = 0                ; Power-on reset flag
		EXTRF = 1               ; External Reset Flag
		BORF = 2                ; Brown-out Reset Flag
		WDRF = 3                ; Watchdog Reset Flag
	}
	MCUCR(0x35) {           ; MCU Control Register
		ISC00 = 0               ; Interrupt Sense Control 0 Bits bit 0
		ISC01 = 1               ; Interrupt Sense Control 0 Bits bit 1
		ISC10 = 2               ; Interrupt Sense Control 1 Bits bit 0
		ISC11 = 3               ; Interrupt Sense Control 1 Bits bit 1
		SM0 = 4                 ; Sleep Mode Select bit 0
		SM1 = 5                 ; Sleep Mode Select bit 1
		SM2 = 6                 ; Sleep Mode Select bit 2
		SE = 7                  ; Sleep Enable
	}
	TWCR(0x36) {            ; TWI Control Register
		TWIE = 0                ; TWI Interrupt Enable
		TWEN = 2                ; TWI Enable Bit
		TWWC = 3                ; TWI Write Collition Flag
		TWSTO = 4               ; TWI Stop Condition Bit
		TWSTA = 5               ; TWI Start Condition Bit
		TWEA = 6                ; TWI Enable Acknowledge Bit
		TWINT = 7               ; TWI Interrupt Flag
	}
	SPMCR(0x37) {           ; Store Program Memory Control Register
		SPMEN = 0               ; Store Program Memory Enable
		PGERS = 1               ; Page Erase
		PGWRT = 2               ; Page Write
		BLBSET = 3              ; Boot Lock Bit Set
		RWWSRE = 4              ; Read-While-Write Section Read Enable
		RWWSB = 6               ; Read-While-Write Section Busy
		SPMIE = 7               ; SPM Interrupt Enable
	}
	TIFR(0x38) {            ; Timer/Counter Interrupt Flag register
		TOV0 = 0                ; Timer/Counter0 Overflow Flag
		TOV1 = 2                ; Timer/Counter1 Overflow Flag
		OCF1B = 3               ; Output Compare Flag 1B
		OCF1A = 4               ; Output Compare Flag 1A
		ICF1 = 5                ; Input Capture Flag 1
		TOV2 = 6                ; Timer/Counter2 Overflow Flag
		OCF2 = 7                ; Output Compare Flag 2
	}
	TIMSK(0x39) {           ; Timer/Counter Interrupt Mask Register
		TOIE0 = 0               ; Timer/Counter0 Overflow Interrupt Enable
		TOIE1 = 2               ; Timer/Counter1 Overflow Interrupt Enable
		OCIE1B = 3              ; Timer/Counter1 Output CompareB Match Interrupt Enable
		OCIE1A = 4              ; Timer/Counter1 Output CompareA Match Interrupt Enable
		TICIE1 = 5              ; Timer/Counter1 Input Capture Interrupt Enable
		TOIE2 = 6               ; Timer/Counter2 Overflow Interrupt Enable
		OCIE2 = 7               ; Timer/Counter2 Output Compare Match Interrupt Enable
	}
	GIFR(0x3a) {            ; General Interrupt Flag Register
		INTF0 = 6               ; External Interrupt Flags bit 0
		INTF1 = 7               ; External Interrupt Flags bit 1
	}
	GICR(0x3b) {            ; General Interrupt Control Register
		IVCE = 0                ; Interrupt Vector Change Enable
		IVSEL = 1               ; Interrupt Vector Select
		INT0 = 6                ; External Interrupt Request 1 Enable bit 0
		INT1 = 7                ; External Interrupt Request 1 Enable bit 1
	}
	SP(0x3d, 0x3e)          ; Stack Pointer
	SREG(0x3f) {            ; Status Register
		C = 0                   ; Carry Flag
		Z = 1                   ; Zero Flag
		N = 2                   ; Negative Flag
		V = 3                   ; Two's Complement Overflow Flag
		S = 4                   ; Sign Bit
		H = 5                   ; Half Carry Flag
		T = 6                   ; Bit Copy Storage
		I = 7                   ; Global Interrupt Enable
	}
}
"""
        //.trimIndent()
