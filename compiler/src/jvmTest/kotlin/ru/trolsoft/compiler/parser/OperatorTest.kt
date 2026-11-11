package ru.trolsoft.compiler.parser

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OperatorTest {

    // ============ Type tests ============

    @Test
    fun `ASSIGN should be binary`() {
        Operator.ASSIGN.isBinary() shouldBe true
    }

    @Test
    fun `PLUS should be binary`() {
        Operator.PLUS.isBinary() shouldBe true
    }

    @Test
    fun `MUL should be binary`() {
        Operator.MUL.isBinary() shouldBe true
    }

    @Test
    fun `INC should be prefix`() {
        Operator.INC.isPrefix() shouldBe true
    }

    @Test
    fun `DEC should be prefix`() {
        Operator.DEC.isPrefix() shouldBe true
    }

    @Test
    fun `BINARY_NOT should be prefix`() {
        Operator.BINARY_NOT.isPrefix() shouldBe true
    }

    @Test
    fun `LOGICAL_NOT should be prefix`() {
        Operator.LOGICAL_NOT.isPrefix() shouldBe true
    }

    @Test
    fun `INC should be suffix`() {
        Operator.INC.isSuffix() shouldBe true
    }

    @Test
    fun `DEC should be suffix`() {
        Operator.DEC.isSuffix() shouldBe true
    }

    @Test
    fun `PLUS should not be prefix`() {
        Operator.PLUS.isPrefix() shouldBe false
    }

    @Test
    fun `PLUS should not be suffix`() {
        Operator.PLUS.isSuffix() shouldBe false
    }

    // ============ isMinusOrPlus tests ============

    @Test
    fun `PLUS should be minusOrPlus`() {
        Operator.PLUS.isMinusOrPlus() shouldBe true
    }

    @Test
    fun `MINUS should be minusOrPlus`() {
        Operator.MINUS.isMinusOrPlus() shouldBe true
    }

    @Test
    fun `MUL should not be minusOrPlus`() {
        Operator.MUL.isMinusOrPlus() shouldBe false
    }

    @Test
    fun `ASSIGN should not be minusOrPlus`() {
        Operator.ASSIGN.isMinusOrPlus() shouldBe false
    }

    // ============ isSelfAssign tests ============

    @Test
    fun `PLUS_ASSIGN should be selfAssign`() {
        Operator.PLUS_ASSIGN.isSelfAssign() shouldBe true
    }

    @Test
    fun `MINUS_ASSIGN should be selfAssign`() {
        Operator.MINUS_ASSIGN.isSelfAssign() shouldBe true
    }

    @Test
    fun `MUL_ASSIGN should be selfAssign`() {
        Operator.MUL_ASSIGN.isSelfAssign() shouldBe true
    }

    @Test
    fun `DIV_ASSIGN should be selfAssign`() {
        Operator.DIV_ASSIGN.isSelfAssign() shouldBe true
    }

    @Test
    fun `SHL_ASSIGN should be selfAssign`() {
        Operator.SHL_ASSIGN.isSelfAssign() shouldBe true
    }

    @Test
    fun `AND_ASSIGN should be selfAssign`() {
        Operator.AND_ASSIGN.isSelfAssign() shouldBe true
    }

    @Test
    fun `OR_ASSIGN should be selfAssign`() {
        Operator.OR_ASSIGN.isSelfAssign() shouldBe true
    }

    @Test
    fun `ASSIGN should not be selfAssign`() {
        Operator.ASSIGN.isSelfAssign() shouldBe false
    }

    @Test
    fun `PLUS should not be selfAssign`() {
        Operator.PLUS.isSelfAssign() shouldBe false
    }

    // ============ isDecOrInc tests ============

    @Test
    fun `INC should be decOrInc`() {
        Operator.INC.isDecOrInc() shouldBe true
    }

    @Test
    fun `DEC should be decOrInc`() {
        Operator.DEC.isDecOrInc() shouldBe true
    }

    @Test
    fun `PLUS should not be decOrInc`() {
        Operator.PLUS.isDecOrInc() shouldBe false
    }

    // ============ isMorePriorityThanRight tests ============

    @Test
    fun `MUL should have more priority than PLUS`() {
        Operator.MUL.isMorePriorityThanRight(Operator.PLUS) shouldBe true
    }

    @Test
    fun `PLUS should not have more priority than MUL`() {
        Operator.PLUS.isMorePriorityThanRight(Operator.MUL) shouldBe false
    }

    @Test
    fun `ASSIGN should not have more priority than PLUS`() {
        Operator.ASSIGN.isMorePriorityThanRight(Operator.PLUS) shouldBe false
    }

    @Test
    fun `PLUS should not have more priority than ASSIGN`() {
        Operator.PLUS.isMorePriorityThanRight(Operator.ASSIGN) shouldBe true
    }

    @Test
    fun `EQ should not have more priority than EQ`() {
        Operator.EQ.isMorePriorityThanRight(Operator.EQ) shouldBe true
    }

    @Test
    fun `PLUS with equal priority should return true left assoc`() {
        Operator.PLUS.isMorePriorityThanRight(Operator.MINUS) shouldBe true
    }

    @Test
    fun `ASSIGN with equal priority should return false right assoc`() {
        Operator.ASSIGN.isMorePriorityThanRight(Operator.ASSIGN) shouldBe false
    }

    // ============ isMorePriorityThanLeft tests ============

    @Test
    fun `MUL should have more priority than PLUS from left`() {
        Operator.MUL.isMorePriorityThanLeft(Operator.PLUS) shouldBe true
    }

    @Test
    fun `PLUS should not have more priority than MUL from left`() {
        Operator.PLUS.isMorePriorityThanLeft(Operator.MUL) shouldBe false
    }

    @Test
    fun `ASSIGN with equal priority should return true from left`() {
        Operator.ASSIGN.isMorePriorityThanLeft(Operator.ASSIGN) shouldBe true
    }

    @Test
    fun `PLUS with equal priority should return false from left`() {
        Operator.PLUS.isMorePriorityThanLeft(Operator.MINUS) shouldBe false
    }

    // ============ String representation tests ============

    @Test
    fun `ASSIGN str should be equals`() {
        Operator.ASSIGN.str shouldBe "="
    }

    @Test
    fun `PLUS str should be equals`() {
        Operator.PLUS.str shouldBe "+"
    }

    @Test
    fun `MINUS str should be equals`() {
        Operator.MINUS.str shouldBe "-"
    }

    @Test
    fun `MUL str should be equals`() {
        Operator.MUL.str shouldBe "*"
    }

    @Test
    fun `LOGICAL_AND str should be equals`() {
        Operator.LOGICAL_AND.str shouldBe "&&"
    }

    @Test
    fun `LOGICAL_OR str should be equals`() {
        Operator.LOGICAL_OR.str shouldBe "||"
    }

    @Test
    fun `SHL str should be equals`() {
        Operator.SHL.str shouldBe "<<"
    }

    @Test
    fun `SHR str should be equals`() {
        Operator.SHR.str shouldBe ">>"
    }

    @Test
    fun `ARROW_RIGHT str should be equals`() {
        Operator.ARROW_RIGHT.str shouldBe "->"
    }

    @Test
    fun `ARROW_LEFT str should be equals`() {
        Operator.ARROW_LEFT.str shouldBe "<-"
    }

    @Test
    fun `INC str should be equals`() {
        Operator.INC.str shouldBe "++"
    }

    @Test
    fun `DEC str should be equals`() {
        Operator.DEC.str shouldBe "--"
    }

    @Test
    fun `BINARY_NOT str should be equals`() {
        Operator.BINARY_NOT.str shouldBe "~"
    }

    @Test
    fun `LOGICAL_NOT str should be equals`() {
        Operator.LOGICAL_NOT.str shouldBe "!"
    }

    // ============ All operators should have unique strings ============

    @Test
    fun `all operators should have unique string representations`() {
        val strings = Operator.entries.map { it.str }
        strings.distinct().size shouldBe strings.size
    }

    // ============ Bitwise operators ============

    @Test
    fun `OR should have less priority than XOR`() {
        Operator.OR.isMorePriorityThanRight(Operator.XOR) shouldBe false
    }

    @Test
    fun `XOR should have less priority than AND`() {
        Operator.XOR.isMorePriorityThanRight(Operator.AND) shouldBe false
    }

    @Test
    fun `AND should have less priority than EQ`() {
        Operator.AND.isMorePriorityThanRight(Operator.EQ) shouldBe false
    }

    @Test
    fun `EQ should have less priority than LESS`() {
        Operator.EQ.isMorePriorityThanRight(Operator.LESS) shouldBe false
    }

    @Test
    fun `LESS should have less priority than SHL`() {
        Operator.LESS.isMorePriorityThanRight(Operator.SHL) shouldBe false
    }

    // ============ Assignment operators should all have same priority ============

    @Test
    fun `all assignment operators should have same priority`() {
        Operator.ASSIGN.isMorePriorityThanRight(Operator.PLUS_ASSIGN) shouldBe false
        Operator.ASSIGN.isMorePriorityThanRight(Operator.MINUS_ASSIGN) shouldBe false
        Operator.ASSIGN.isMorePriorityThanRight(Operator.MUL_ASSIGN) shouldBe false
    }

    // ============ Comparison operators should have same priority ============

    @Test
    fun `EQ and NOT_EQ should have same priority`() {
        Operator.EQ.isMorePriorityThanRight(Operator.NOT_EQ) shouldBe true
        Operator.NOT_EQ.isMorePriorityThanRight(Operator.EQ) shouldBe true
    }

    @Test
    fun `LESS and GREAT should have same priority`() {
        Operator.LESS.isMorePriorityThanRight(Operator.GREAT) shouldBe true
        Operator.GREAT.isMorePriorityThanRight(Operator.LESS) shouldBe true
    }

    @Test
    fun `SHL and SHR should have same priority`() {
        Operator.SHL.isMorePriorityThanRight(Operator.SHR) shouldBe true
        Operator.SHR.isMorePriorityThanRight(Operator.SHL) shouldBe true
    }

    @Test
    fun `PLUS and MINUS should have same priority`() {
        Operator.PLUS.isMorePriorityThanRight(Operator.MINUS) shouldBe true
        Operator.MINUS.isMorePriorityThanRight(Operator.PLUS) shouldBe true
    }

    @Test
    fun `MUL and DIV and MOD should have same priority`() {
        Operator.MUL.isMorePriorityThanRight(Operator.DIV) shouldBe true
        Operator.DIV.isMorePriorityThanRight(Operator.MOD) shouldBe true
    }

    @Test
    fun `LESS_EQ and GREAT_EQ should have same priority as LESS`() {
        Operator.LESS.isMorePriorityThanRight(Operator.LESS_EQ) shouldBe true
        Operator.LESS.isMorePriorityThanRight(Operator.GREAT_EQ) shouldBe true
    }
}
