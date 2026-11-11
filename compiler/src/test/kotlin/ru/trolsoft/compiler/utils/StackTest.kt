package ru.trolsoft.compiler.utils

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class StackTest {

    @Test
    fun `new stack should be empty`() {
        val stack = Stack<Int>()
        stack.isEmpty() shouldBe true
        stack.size() shouldBe 0
    }

    @Test
    fun `push item then stack should not be empty`() {
        val stack = Stack<String>()
        stack.push("item")
        stack.isEmpty() shouldBe false
        stack.size() shouldBe 1
    }

    @Test
    fun `push multiple items then size should be correct`() {
        val stack = Stack<Int>()
        stack.push(1)
        stack.push(2)
        stack.push(3)
        stack.size() shouldBe 3
    }

    @Test
    fun `pop should return last pushed item LIFO`() {
        val stack = Stack<Int>()
        stack.push(10)
        stack.push(20)
        stack.push(30)
        stack.pop() shouldBe 30
        stack.pop() shouldBe 20
        stack.pop() shouldBe 10
    }

    @Test
    fun `pop from empty stack should return null`() {
        val stack = Stack<Any>()
        stack.pop() shouldBe null
    }

    @Test
    fun `peek should return top item without removing it`() {
        val stack = Stack<String>()
        stack.push("first")
        stack.push("second")
        stack.peek() shouldBe "second"
        stack.size() shouldBe 2 // size should not change
    }

    @Test
    fun `peek from empty stack should return null`() {
        val stack = Stack<Int>()
        stack.peek() shouldBe null
    }

    @Test
    fun `clear should remove all items`() {
        val stack = Stack<Int>()
        stack.push(1)
        stack.push(2)
        stack.push(3)
        stack.size() shouldBe 3
        stack.clear()
        stack.isEmpty() shouldBe true
        stack.size() shouldBe 0
    }

    @Test
    fun `pop all items then clear should not throw`() {
        val stack = Stack<Int>()
        stack.push(1)
        stack.push(2)
        stack.pop()
        stack.pop()
        stack.clear()
        stack.isEmpty() shouldBe true
    }

    @Test
    fun `copyTo should copy all items`() {
        val stack = Stack<Int>()
        stack.push(100)
        stack.push(200)
        stack.push(300)

        val other = Stack<Int>()
        stack.copyTo(other)

        other.size() shouldBe 3
        other.pop() shouldBe 300
        other.pop() shouldBe 200
        other.pop() shouldBe 100
    }

    @Test
    fun `copyTo empty stack should work`() {
        val stack = Stack<String>()
        val other = Stack<String>()
        stack.copyTo(other)
        other.isEmpty() shouldBe true
    }

    @Test
    fun `copyTo should not affect source stack`() {
        val stack = Stack<Int>()
        stack.push(1)
        stack.push(2)

        val other = Stack<Int>()
        stack.copyTo(other)

        stack.size() shouldBe 2
        other.size() shouldBe 2
    }

    @Test
    fun `toString should return items representation`() {
        val stack = Stack<Int>()
        stack.push(1)
        stack.push(2)
        stack.push(3)
        val str = stack.toString()
        str shouldNotBe null
        str.contains("1") shouldBe true
        str.contains("2") shouldBe true
        str.contains("3") shouldBe true
    }

    @Test
    fun `mixed operations should work correctly`() {
        val stack = Stack<Int>()

        stack.isEmpty() shouldBe true

        stack.push(42)
        stack.peek() shouldBe 42
        stack.size() shouldBe 1

        stack.push(84)
        stack.pop() shouldBe 84
        stack.size() shouldBe 1

        stack.clear()
        stack.isEmpty() shouldBe true

        stack.pop() shouldBe null
        stack.peek() shouldBe null
    }

    @Test
    fun `stack with null values should work`() {
        val stack: Stack<String?> = Stack()
        stack.push(null)
        stack.push("value")
        stack.size() shouldBe 2
        stack.pop() shouldBe "value"
        stack.pop() shouldBe null
    }

    @Test
    fun `stack should support generic objects`() {
        data class Person(val name: String, val age: Int)
        val stack = Stack<Person>()

        stack.push(Person("Alice", 30))
        stack.push(Person("Bob", 25))

        stack.peek() shouldBe Person("Bob", 25)
        stack.pop() shouldBe Person("Bob", 25)
        stack.pop() shouldBe Person("Alice", 30)
    }
}