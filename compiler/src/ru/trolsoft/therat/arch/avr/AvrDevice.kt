package ru.trolsoft.therat.arch.avr

data class Interrupt(
        val name: String,
        val offset: Int
//        val description: String
)

data class RegisterBit(
        val name: String,
        val number: Int
//        val description: String
)

data class IoRegister(
        val name: String,
//        val description: String,
        val offset: Int,
        val size: Int,
        val bits: List<RegisterBit>
) {
    fun getBitNumber(name: String): Int {
        for (bit in bits) {
            if (bit.name == name) {
                return bit.number
            }
        }
        return -1
    }
}

data class Interrupts (
        val vectors: List<Interrupt>
) {
    fun hasVector(name: String): Boolean {
        for (v in vectors) {
            if (v.name == name) {
                return true
            }
        }
        return false
    }

    fun getOffset(name: String): Int {
        for (v in vectors) {
            if (v.name == name) {
                return v.offset
            }
        }
        return -1
    }

    fun size() = vectors.size
    operator fun get(offset: Int) = vectors[offset]
    operator fun iterator(): Iterator<Interrupt> = vectors.iterator()

}

data class AvrDevice(
        val name: String,

        val flashSize: Int,
        val ramStart: Int,
        val ramSize: Int,
        val eepromSize: Int,

        val interrupts: Interrupts,
        val registers: List<IoRegister>
) {

    fun findIo(name: String): IoRegister? {
        for (io in registers) {
            if (io.name == name) {
                return io
            }
        }
        return null
    }
//    fun hasIo(name: String): Boolean = getIoAddr(name) >= 0


    fun getIoAddr(name: String): Int {
        val r1 = findIo(name)
        if (r1 != null) {
            return r1.offset
        }
        if (name.toUpperCase().endsWith("H")) {
            val r = findIo(name.substring(0, name.length-1))
            if (r != null) {
                return r.offset + 1
            }
        }
        if (name.toUpperCase().endsWith("L")) {
            val r = findIo(name.substring(0, name.length-1))
            if (r != null) {
                return r.offset
            }
        }
        return -1
    }

//    fun getIoSize(name: String): Int {
//        val io = findIo(name)
//        if (io != null) {
//            return io.size
//        }
//        if (getIoAddr(name) >= 0) {
//            return 1
//        }
//        return -1
//    }

    fun hasPort(name: String): Boolean {
        return findIo("PORT" + name.toUpperCase()) != null
    }
    fun hasPort(name: Char): Boolean = hasPort(name.toString())


    fun findIoWithBit(bitName: String): IoRegister? {
        for (io in registers) {
            if (io.getBitNumber(bitName) >= 0) {
                return io
            }
        }
        return null
    }

}








data class Device(
        val name: String,
        val flashSize: Int,
        val ramStart: Int,
        val ramSize: Int,
        val eepromSize: Int,
        val flags: Int) {

    companion object {
        const val NO_MUL    = 0x00000001
        const val NO_JMP    = 0x00000002	// No JMP, CALL
        const val NO_XREG   = 0x00000004	// No X register
        const val NO_YREG   = 0x00000008	// No Y register
        const val TINY1X    = 0x00000010	// AT90S1200, ATtiny10-12  set: No ADIW, SBIW, IJMP, ICALL, LDD, STD, LDS, STS, PUSH, POP
        const val NO_LPM    = 0x00000020	// No LPM instruction
        const val NO_LPM_X  = 0x00000040    // No LPM Rd,Z or LPM Rd,Z+ instruction
        const val NO_ELPM   = 0x00000080	// No ELPM instruction
        const val NO_ELPM_X = 0x00000100    // No ELPM Rd,Z or LPM Rd,Z+ instruction
        const val NO_SPM    = 0x00000200    // No SPM instruction
        const val NO_ESPM   = 0x00000400    // No ESPM instruction
        const val NO_MOVW   = 0x00000800    // No MOVW instruction
        const val NO_BREAK  = 0x00001000    // No BREAK instruction
        const val NO_EICALL = 0x00002000    // No EICALL instruction
        const val NO_EIJMP  = 0x00004000    // No EIJMP instruction

        fun getDevice(name: String): Device? {
            return when (name.toLowerCase()) {
                // ATtiny4
                // ATtiny5
                // ATtiny9
                "attiny10" -> Device("ATtiny10", 512, 0x00, 0, 0,
                        NO_MUL + NO_JMP + TINY1X + NO_XREG + NO_YREG + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "attiny11" -> Device("ATtiny11", 512,0x00,0,0,
                        NO_MUL + NO_JMP + TINY1X + NO_XREG + NO_YREG + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM +  NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "attiny12" -> Device("ATtiny12",512,0x00,0,64,
                        NO_MUL + NO_JMP + TINY1X + NO_XREG + NO_YREG + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "attiny13" -> Device("ATtiny13", 512,0x60,64,64,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny13a" -> Device("ATtiny13A",512,0x60,64,64,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny15" -> Device("ATtiny15", 512,0x00,0,64,
                        NO_MUL + NO_JMP + TINY1X + NO_XREG + NO_YREG + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                // ATtiny20
                "attiny22" -> Device("ATtiny22", 1024,0x60,128,128,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "attiny24" -> Device("ATtiny24",1024,0x60, 128,128,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny24a" -> Device("ATtiny24A",1024,0x60,128,128,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny25" -> Device("ATtiny25", 1024, 0x60, 128, 128,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny26" -> Device("ATtiny26", 1024, 0x60, 128, 128,
                        NO_MUL + NO_JMP + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "attiny28" -> Device("ATtiny28", 1024, 0x00, 0, 0,
                        NO_MUL + NO_JMP + TINY1X + NO_XREG + NO_YREG + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                // ATtiny43U
                "attiny44" -> Device("ATtiny44",2048,0x60,256,256,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny44a" -> Device("ATtiny44A",2048,0x60,256,256,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny45" -> Device("ATtiny45",2048,0x60,256,256,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny84" -> Device("ATtiny84",4096,0x60,512,512,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny85" -> Device("ATtiny85",4096,0x60,512,512,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                // ATtiny87
                // ATtiny167
                // ATtiny261A
                // ATtiny461A
                // ATtiny861A
                "attiny2313" -> Device("ATtiny2313", 1024, 0x60, 128, 128,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny2313a" -> Device("ATtiny2313A", 1024, 0x60, 128, 128,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                "attiny4313" -> Device("ATtiny4313", 2048, 0x60, 256, 256,
                        NO_MUL + NO_JMP + NO_ELPM + NO_ESPM + NO_EICALL + NO_EIJMP)
                // AT90 series
                "at90s1200" -> Device("AT90S1200", 512, 0x00, 0, 64,
                        NO_MUL + NO_JMP + TINY1X + NO_XREG + NO_YREG + NO_LPM + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP) // 137 - MUL(6) - JMP(2) - TINY(10)
                "at90s2313" -> Device("AT90S2313", 1024, 0x60, 128, 128,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "at90s2323" -> Device("AT90S2323", 1024, 0x60, 128, 128,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "at90s2333" -> Device("AT90S2333", 1024, 0x60, 128, 128,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "at90s2343" -> Device("AT90S2343", 1024, 0x60, 128, 128,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "at90s4414" -> Device("AT90S4414", 2048, 0x60, 256, 256,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "at90s4433" -> Device("AT90S4433", 2048, 0x60, 128, 256,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "at90s4434" -> Device("AT90S4434", 2048, 0x60, 256, 256,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "at90s8515" -> Device("AT90S8515", 4096, 0x60, 512, 512,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP) // 137 - MUL(6) - JMP(2) - LPM_X(2) - ELPM(3) - SPM - ESPM - MOVW - BREAK - EICALL - EIJMP = 118
                "at90c8534" -> Device("AT90C8534", 4096, 0x60, 256, 512,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                "at90s8535" -> Device("AT90S8535", 4096, 0x60, 512, 512,
                        NO_MUL + NO_JMP + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_MOVW + NO_BREAK + NO_EICALL + NO_EIJMP)
                // AT90USB series
                // AT90USB168
                // AT90USB1287
                // ATmega series
                "atmega8" -> Device("ATmega8", 4096, 0x60, 1024, 512,
                        NO_JMP + NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega161" -> Device("ATmega161", 8192, 0x60, 1024, 512,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega162" -> Device("ATmega162", 8192, 0x100, 1024, 512,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega163" -> Device("ATmega163", 8192, 0x60, 1024, 512,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega16" -> Device("ATmega16", 8192, 0x60, 1024, 512,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega323" -> Device("ATmega323", 16384, 0x60, 2048, 1024,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM) // 137 - EICALL - EIJMP - ELPM(3) - ESPM = 131 (Data sheet says 130 but it's wrong)
                "atmega328p" -> Device("ATmega328P", 16384, 0x100, 2048, 1024,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega32" -> Device("ATmega32", 16384, 0x60, 2048, 1024,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega603" -> Device("ATmega603", 32768, 0x60, 4096, 2048,
                        NO_EICALL + NO_EIJMP + NO_MUL + NO_MOVW + NO_LPM_X + NO_ELPM + NO_SPM + NO_ESPM + NO_BREAK)
                "atmega103" -> Device("ATmega103", 65536, 0x60, 4096, 4096,
                        NO_EICALL + NO_EIJMP + NO_MUL + NO_MOVW + NO_LPM_X + NO_ELPM_X + NO_SPM + NO_ESPM + NO_BREAK) // 137 - EICALL - EIJMP - MUL(6) - MOVW - LPM_X(2) - ELPM_X(2) - SPM - ESPM - BREAK = 121
                "atmega104" -> Device("ATmega104", 65536, 0x60, 4096, 4096,
                        NO_EICALL + NO_EIJMP + NO_ESPM) // Old name for mega128
                "atmega128" -> Device("ATmega128", 65536, 0x100, 4096, 4096,
                        NO_EICALL + NO_EIJMP + NO_ESPM) // 137 - EICALL - EIJMP - ESPM = 134 (Data sheet says 133 but it's wrong)
                "atmega48" -> Device("ATmega48", 2048, 0x100, 512, 256,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega88" -> Device("ATmega88", 4096, 0x100, 1024, 512,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega168" -> Device("ATmega168", 8192, 0x100, 1024, 512,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                "atmega8515" -> Device("ATmega8515", 8192, 0x60, 512, 512,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_ESPM)
                // Other
                "at94k" -> Device("AT94K", 8192, 0x60, 16384, 0,
                        NO_EICALL + NO_EIJMP + NO_ELPM + NO_SPM + NO_ESPM + NO_BREAK) // 137 - EICALL - EIJMP - ELPM(3) - SPM - ESPM - BREAK = 129
                else -> null
            }
        }
    }
}



