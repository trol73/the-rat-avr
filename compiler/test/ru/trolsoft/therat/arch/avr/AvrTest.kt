package ru.trolsoft.therat.arch.avr

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvrTest {
    @Test fun testRegisters() {
        assertTrue(isAvrRegister("r0"))
        assertTrue(isAvrRegister("R31"))
        assertTrue(isAvrRegister("ZH"))
    }

    @Test fun testNotRegisters() {
        assertFalse(isAvrRegister("r32"))
        assertFalse(isAvrRegister("Z"))
        assertFalse(isAvrRegister(""))
    }

    @Test fun testPair() {
        assertTrue(isAvrPair("X"))
        assertTrue(isAvrPair("Y"))
        assertTrue(isAvrPair("Z"))
    }

    @Test fun testNotPair() {
        assertFalse(isAvrPair("x"))
        assertFalse(isAvrPair(""))
        assertFalse(isAvrPair("y"))
        assertFalse(isAvrPair("z"))
        assertFalse(isAvrPair("r11"))
    }

    @Test fun testAvrPairLow() {
        assertTrue(isAvrPairLow("R26"))
        assertTrue(isAvrPairLow("r28"))
        assertTrue(isAvrPairLow("R30"))
        assertTrue(isAvrPairLow("XL"))
        assertTrue(isAvrPairLow("yl"))
        assertTrue(isAvrPairLow("zl"))
    }

    @Test fun testAvrEvenRegister() {
        assertTrue(isAvrEvenRegister("r0"))
        assertTrue(isAvrEvenRegister("r2"))
        assertTrue(isAvrEvenRegister("r30"))
        assertTrue(isAvrEvenRegister("zl"))
        assertTrue(isAvrEvenRegister("XL"))
    }

    @Test fun testAvrUpperRegister() {
        assertTrue(isAvrUpperRegister("r16"))
        assertTrue(isAvrUpperRegister("XL"))
        assertTrue(isAvrUpperRegister("ZL"))
        assertTrue(isAvrUpperRegister("yl"))
        assertTrue(isAvrUpperRegister("r31"))
    }
}