package ru.trolsoft.compiler.general

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class DataTypeTest {

    @Test
    fun `BYTE ram size should be 1`() {
        DataType.BYTE.ramSize shouldBe 1
    }

    @Test
    fun `WORD ram size should be 2`() {
        DataType.WORD.ramSize shouldBe 2
    }

    @Test
    fun `DWORD ram size should be 4`() {
        DataType.DWORD.ramSize shouldBe 4
    }

    @Test
    fun `QWORD ram size should be 8`() {
        DataType.QWORD.ramSize shouldBe 8
    }

    @Test
    fun `PTR ram size should be 2`() {
        DataType.PTR.ramSize shouldBe 2
    }

    @Test
    fun `PRGPTR ram size should be 0`() {
        DataType.PRGPTR.ramSize shouldBe 0
    }

    @Test
    fun `STRUCTURED ram size should be 0`() {
        DataType.STRUCTURED.ramSize shouldBe 0
    }

    @Test
    fun `all data types should have unique names`() {
        val names = DataType.entries.map { it.name }
        names.distinct().size shouldBe names.size
    }
}

class VarTest {

    @Test
    fun `ram size for simple byte variable`() {
        val v = Var("x", DataType.BYTE, null, 0, null)
        v.ramSize() shouldBe 1
    }

    @Test
    fun `ram size for word variable`() {
        val v = Var("x", DataType.WORD, null, 0, null)
        v.ramSize() shouldBe 2
    }

    @Test
    fun `ram size for dword variable`() {
        val v = Var("x", DataType.DWORD, null, 0, null)
        v.ramSize() shouldBe 4
    }

    @Test
    fun `ram size for array of bytes`() {
        val v = Var("arr", DataType.BYTE, 10, 0, null)
        v.ramSize() shouldBe 10
    }

    @Test
    fun `ram size for array of words`() {
        val v = Var("arr", DataType.WORD, 5, 0, null)
        v.ramSize() shouldBe 10
    }

    @Test
    fun `ram size for array of dwords`() {
        val v = Var("arr", DataType.DWORD, 3, 0, null)
        v.ramSize() shouldBe 12
    }

    @Test
    fun `ram size for pointer variable`() {
        val v = Var("ptr", DataType.PTR, null, 0, null)
        v.ramSize() shouldBe 2
    }

    @Test
    fun `ram size for struct variable should throw`() {
        val v = Var("s", DataType.STRUCTURED, null, 0, "MyStruct")
        kotlin.test.assertFailsWith<RuntimeException> {
            v.ramSize()
        }
    }

    @Test
    fun `var with struct name`() {
        val v = Var("myStruct", DataType.STRUCTURED, null, 10, "MyStruct")
        v.name shouldBe "myStruct"
        v.structName shouldBe "MyStruct"
        v.offset shouldBe 10
    }
}

class StructuredDataTest {

    private val byteItem = PrimitiveItem("field1", 1, DataType.BYTE)
    private val wordItem = PrimitiveItem("field2", 2, DataType.WORD)
    private val dwordItem = PrimitiveItem("field3", 4, DataType.DWORD)

    @Test
    fun `struct size with single item`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "Single",
            items = listOf(byteItem)
        )
        data.getSize() shouldBe 1
    }

    @Test
    fun `struct size with multiple items`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "Multi",
            items = listOf(byteItem, wordItem, dwordItem)
        )
        data.getSize() shouldBe 7 // 1 + 2 + 4
    }

    @Test
    fun `struct size with empty items`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "Empty",
            items = emptyList()
        )
        data.getSize() shouldBe 0
    }

    @Test
    fun `union size is max of all items`() {
        val data = StructuredData(
            type = StructUnion.UNION,
            name = "MyUnion",
            items = listOf(byteItem, wordItem, dwordItem)
        )
        data.getSize() shouldBe 4 // max(1, 2, 4)
    }

    @Test
    fun `union size with larger first item`() {
        val data = StructuredData(
            type = StructUnion.UNION,
            name = "MyUnion",
            items = listOf(dwordItem, wordItem, byteItem)
        )
        data.getSize() shouldBe 4 // max(4, 2, 1)
    }

    @Test
    fun `union size with single item`() {
        val data = StructuredData(
            type = StructUnion.UNION,
            name = "Single",
            items = listOf(wordItem)
        )
        data.getSize() shouldBe 2
    }

    @Test
    fun `get offset for struct field`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "MyStruct",
            items = listOf(byteItem, wordItem, dwordItem)
        )
        data.getOffset("field1") shouldBe 0
        data.getOffset("field2") shouldBe 1
        data.getOffset("field3") shouldBe 3
    }

    @Test
    fun `get offset for union field is always 0`() {
        val data = StructuredData(
            type = StructUnion.UNION,
            name = "MyUnion",
            items = listOf(byteItem, wordItem, dwordItem)
        )
        data.getOffset("field1") shouldBe 0
        data.getOffset("field2") shouldBe 0
        data.getOffset("field3") shouldBe 0
    }

    @Test
    fun `get offset for non-existent field returns -1`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "MyStruct",
            items = listOf(byteItem, wordItem)
        )
        data.getOffset("nonexistent") shouldBe -1
    }

    @Test
    fun `get item by name returns item`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "MyStruct",
            items = listOf(byteItem, wordItem, dwordItem)
        )
        val item = data.getItem("field2")
        item shouldNotBe null
        item!!.name shouldBe "field2"
        item.size shouldBe 2
    }

    @Test
    fun `get item by name returns null for non-existent`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "MyStruct",
            items = listOf(byteItem, wordItem)
        )
        data.getItem("nonexistent") shouldBe null
    }

    @Test
    fun `get size by name returns correct size`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "MyStruct",
            items = listOf(byteItem, wordItem, dwordItem)
        )
        data.getSize("field1") shouldBe 1
        data.getSize("field2") shouldBe 2
        data.getSize("field3") shouldBe 4
    }

    @Test
    fun `get size by name returns -1 for non-existent`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "MyStruct",
            items = listOf(byteItem)
        )
        data.getSize("nonexistent") shouldBe -1
    }

    @Test
    fun `struct with nested struct item`() {
        val nested = StructuredData(
            type = StructUnion.STRUCT,
            name = "Nested",
            items = listOf(byteItem)
        )
        val nestedItem = StructItem("inner", 1, nested)
        val outer = StructuredData(
            type = StructUnion.STRUCT,
            name = "Outer",
            items = listOf(nestedItem, dwordItem)
        )
        outer.getSize() shouldBe 5 // 1 + 4
    }

    @Test
    fun `struct name is preserved`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "MyVeryLongStructName",
            items = listOf(byteItem)
        )
        data.name shouldBe "MyVeryLongStructName"
    }

    @Test
    fun `struct type is preserved`() {
        val structData = StructuredData(
            type = StructUnion.STRUCT,
            name = "S",
            items = listOf(byteItem)
        )
        structData.type shouldBe StructUnion.STRUCT

        val unionData = StructuredData(
            type = StructUnion.UNION,
            name = "U",
            items = listOf(byteItem)
        )
        unionData.type shouldBe StructUnion.UNION
    }

    @Test
    fun `complex struct with mixed sizes`() {
        val items = listOf(
            PrimitiveItem("a", 1, DataType.BYTE),
            PrimitiveItem("b", 2, DataType.WORD),
            PrimitiveItem("c", 1, DataType.BYTE),
            PrimitiveItem("d", 4, DataType.DWORD)
        )
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "Complex",
            items = items
        )
        data.getSize() shouldBe 8 // 1 + 2 + 1 + 4
        data.getOffset("a") shouldBe 0
        data.getOffset("b") shouldBe 1
        data.getOffset("c") shouldBe 3
        data.getOffset("d") shouldBe 4
    }

    @Test
    fun `union with equal sized items`() {
        val items = listOf(
            PrimitiveItem("x", 2, DataType.WORD),
            PrimitiveItem("y", 2, DataType.PTR)
        )
        val data = StructuredData(
            type = StructUnion.UNION,
            name = "EqualUnion",
            items = items
        )
        data.getSize() shouldBe 2
    }

    @Test
    fun `struct get offset for first field`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "S",
            items = listOf(wordItem, dwordItem)
        )
        data.getOffset("field2") shouldBe 0
    }

    @Test
    fun `struct get offset for last field`() {
        val data = StructuredData(
            type = StructUnion.STRUCT,
            name = "S",
            items = listOf(byteItem, wordItem)
        )
        data.getOffset("field2") shouldBe 1
    }
}

class DataItemTest {

    @Test
    fun `primitive item properties`() {
        val item = PrimitiveItem("test", 4, DataType.DWORD)
        item.name shouldBe "test"
        item.size shouldBe 4
        item.dataType shouldBe DataType.DWORD
    }

    @Test
    fun `struct item properties`() {
        val sd = StructuredData(
            type = StructUnion.STRUCT,
            name = "Inner",
            items = listOf(PrimitiveItem("x", 2, DataType.WORD))
        )
        val item = StructItem("wrapped", 4, sd)
        item.name shouldBe "wrapped"
        item.size shouldBe 4
        item.structuredData.name shouldBe "Inner"
    }

    @Test
    fun `pointer item properties`() {
        val source = PrimitiveItem("base", 2, DataType.WORD)
        val item = PointerItem("ptr", 2, source)
        item.name shouldBe "ptr"
        item.size shouldBe 2
        item.source.name shouldBe "base"
    }

    @Test
    fun `array item properties`() {
        val source = PrimitiveItem("elem", 1, DataType.BYTE)
        val item = ArrayItem("arr", 10, source, 10)
        item.name shouldBe "arr"
        item.size shouldBe 10
        item.source.name shouldBe "elem"
        item.arraySize shouldBe 10
    }

    @Test
    fun `array item with word elements`() {
        val source = PrimitiveItem("elem", 2, DataType.WORD)
        val item = ArrayItem("arr", 20, source, 10)
        item.size shouldBe 20 // 10 * 2
        item.arraySize shouldBe 10
    }

    @Test
    fun `array item with dword elements`() {
        val source = PrimitiveItem("elem", 4, DataType.DWORD)
        val item = ArrayItem("arr", 16, source, 4)
        item.size shouldBe 16
        item.arraySize shouldBe 4
    }

    @Test
    fun `all data items are sealed class instances`() {
        val primitive = PrimitiveItem("p", 1, DataType.BYTE)
        val sd = StructuredData(StructUnion.STRUCT, "s", emptyList())
        val struct = StructItem("s", 1, sd)
        val ptr = PointerItem("p", 2, primitive)
        val array = ArrayItem("a", 5, primitive, 5)

        listOf(primitive, struct, ptr, array).forEach {
            it shouldNotBe null
        }
    }
}
