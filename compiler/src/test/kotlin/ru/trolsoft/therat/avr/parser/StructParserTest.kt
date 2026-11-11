package ru.trolsoft.therat.avr.parser

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.general.*

class StructParserTest {
    @Test fun `simple struct`() {
        val r = parse(
            """
            struct {
                byte b1
                byte b2
                word w
            } my_struct_t
        """.trimIndent())
        val s = r.structures["my_struct_t"]!!
        s.name shouldBe "my_struct_t"
        s.type shouldBe StructUnion.STRUCT
        s.items shouldHaveSize 3
        with (s.items[0]) {
            name shouldBe "b1"
            size shouldBe 1
            shouldBeInstanceOf<PrimitiveItem>()
        }
        with (s.items[1]) {
            name shouldBe "b2"
            size shouldBe 1
            shouldBeInstanceOf<PrimitiveItem>()
        }
        with (s.items[2]) {
            name shouldBe "w"
            size shouldBe 2
            shouldBeInstanceOf<PrimitiveItem>()
        }
        s.getSize() shouldBe 4
        s.getOffset("b1") shouldBe 0
        s.getOffset("b2") shouldBe 1
        s.getOffset("w") shouldBe 2
        s.getOffset("wrong") shouldBe -1
    }

    @Test fun `simple struct with pointer`() {
        val r = parse("""
            struct {
                byte b
                byte *pb
                word w
            } my_struct_t
        """.trimIndent())
        val s = r.structures["my_struct_t"]!!
        s.items shouldHaveSize 3
        s.type shouldBe StructUnion.STRUCT

        with (s.items[0]) {
            name shouldBe "b"
            size shouldBe 1
            shouldBeInstanceOf<PrimitiveItem>()
        }
        with (s.items[1]) {
            name shouldBe "pb"
            size shouldBe 2
            shouldBeInstanceOf<PointerItem>()
            source.shouldBeInstanceOf<PrimitiveItem>()
        }
        with (s.items[2]) {
            name shouldBe "w"
            size shouldBe 2
            shouldBeInstanceOf<PrimitiveItem>()
        }
        s.getSize() shouldBe 5
        s.getOffset("b") shouldBe 0
        s.getOffset("pb") shouldBe 1
        s.getOffset("w") shouldBe 3
        s.getOffset("wrong") shouldBe -1
    }

    @Test fun `simple struct with array`() {
        val r = parse("""
            struct {
                byte b1[5]
                byte b2[10]
                word w[3]
            } my_struct_t
        """.trimIndent())
        val s = r.structures["my_struct_t"]!!
        s.items shouldHaveSize 3
        s.type shouldBe StructUnion.STRUCT
        with (s.items[0]) {
            name shouldBe "b1"
            size shouldBe 5
            shouldBeInstanceOf<ArrayItem>()
            source.shouldBeInstanceOf<PrimitiveItem>()
            source.size shouldBe 1
            arraySize shouldBe 5
        }
        with (s.items[1]) {
            name shouldBe "b2"
            size shouldBe 10
            shouldBeInstanceOf<ArrayItem>()
            source.shouldBeInstanceOf<PrimitiveItem>()
            source.size shouldBe 1
            arraySize shouldBe 10
        }
        with (s.items[2]) {
            name shouldBe "w"
            size shouldBe 6
            shouldBeInstanceOf<ArrayItem>()
            source.shouldBeInstanceOf<PrimitiveItem>()
            source.size shouldBe 2
            arraySize shouldBe 3
        }
        s.getSize() shouldBe 21
        s.getOffset("b1") shouldBe 0
        s.getOffset("b2") shouldBe 5
        s.getOffset("w") shouldBe 15
        s.getOffset("wrong") shouldBe -1
    }

    @Test fun `nested structs`() {
        val r = parse("""
            struct {
                byte x
                byte y
                word color
            } point_t
            struct {
                point_t center
                point_t vertex[4]
                point_t *next
            } s2_t
        """.trimIndent())
        r.structures shouldHaveSize 2
        with (r.structures["point_t"]) {
            shouldNotBeNull()
            getSize() shouldBe 4
            type shouldBe StructUnion.STRUCT
        }
        with (r.structures["s2_t"]) {
            shouldNotBeNull()
            getSize() shouldBe 4 + 4 * 4 + 2
            type shouldBe StructUnion.STRUCT
            getOffset("center") shouldBe 0
            getSize("center") shouldBe 4
            getOffset("vertex") shouldBe 4
            getSize("vertex") shouldBe 16
            getOffset("next") shouldBe 20
            getSize("next") shouldBe 2
            with (getItem("center")) {
                shouldBeInstanceOf<StructItem>()
                structuredData.name shouldBe "point_t"
            }
            with (getItem("vertex")) {
                shouldBeInstanceOf<ArrayItem>()
                with (source) {
                    shouldBeInstanceOf<StructItem>()
                    structuredData.name shouldBe "point_t"
                }
            }
            with (getItem("next")) {
                shouldBeInstanceOf<PointerItem>()
                with (source) {
                    shouldBeInstanceOf<StructItem>()
                    structuredData.name shouldBe "point_t"
                }
            }
        }
    }

    @Test fun `typedef struct`() {
        val r = parse("""
                    typedef struct {
                        byte x
                        byte y
                        word color
                    } point_t
            """.trimIndent())
        r.structures shouldHaveSize 1
        with (r.structures["point_t"]) {
            shouldNotBeNull()
            type shouldBe StructUnion.STRUCT
            getSize() shouldBe 4
        }
    }

    @Test fun `simple union`() {
        val r = parse("""
                    typedef union {
                        byte x[2]
                        word color[2]
                    } abc_t
            """.trimIndent())
        r.structures shouldHaveSize 1

        with (r.structures["abc_t"]) {
            shouldNotBeNull()
            type shouldBe StructUnion.UNION
            getSize() shouldBe 4
            getOffset("x") shouldBe 0
            getOffset("color") shouldBe 0
        }
    }

//    @Test fun `union with embedded struct`() {
//        val r = parse("""
//                    typedef union {
//                        struct {
//                            uint8_t id
//                            uint16_t color
//                            char name[10]
//                        }
//                        struct {
//                            uint32_t size
//                            int8_t data[16]
//                        }
//                    } abc_t
//            """.trimIndent())
//    }


        // TODO проверить валидность типов (prgptr)

}