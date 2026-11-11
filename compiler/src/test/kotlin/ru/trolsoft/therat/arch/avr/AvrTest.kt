package ru.trolsoft.therat.arch.avr

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Test

class AvrTest {
    @Test fun testRegisters() {
        isAvrRegister("r0").shouldBeTrue()
        isAvrRegister("R31").shouldBeTrue()
        isAvrRegister("ZH").shouldBeTrue()
    }

    @Test fun testNotRegisters() {
        isAvrRegister("r32").shouldBeFalse()
        isAvrRegister("Z").shouldBeFalse()
        isAvrRegister("").shouldBeFalse()
    }

    @Test fun testPair() {
        isAvrPairName("X").shouldBeTrue()
        isAvrPairName("Y").shouldBeTrue()
        isAvrPairName("Z").shouldBeTrue()
    }

    @Test fun testNotPair() {
        isAvrPairName("x").shouldBeFalse()
        isAvrPairName("").shouldBeFalse()
        isAvrPairName("y").shouldBeFalse()
        isAvrPairName("z").shouldBeFalse()
        isAvrPairName("r11").shouldBeFalse()
    }

    @Test fun testAvrPairLow() {
        isAvrPairLow("R26").shouldBeTrue()
        isAvrPairLow("r28").shouldBeTrue()
        isAvrPairLow("R30").shouldBeTrue()
        isAvrPairLow("XL").shouldBeTrue()
        isAvrPairLow("yl").shouldBeTrue()
        isAvrPairLow("zl").shouldBeTrue()
    }

    @Test fun testAvrEvenRegister() {
        isAvrEvenRegister("r0").shouldBeTrue()
        isAvrEvenRegister("r2").shouldBeTrue()
        isAvrEvenRegister("r30").shouldBeTrue()
        isAvrEvenRegister("zl").shouldBeTrue()
        isAvrEvenRegister("XL").shouldBeTrue()
    }

    @Test fun testAvrUpperRegister() {
        isAvrUpperRegister("r16").shouldBeTrue()
        isAvrUpperRegister("XL").shouldBeTrue()
        isAvrUpperRegister("ZL").shouldBeTrue()
        isAvrUpperRegister("yl").shouldBeTrue()
        isAvrUpperRegister("r31").shouldBeTrue()
    }
}