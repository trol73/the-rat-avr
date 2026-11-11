package ru.trolsoft.compiler.utils

class Stack<T> {
    private val items: MutableList<T> = mutableListOf()

    fun push(item: T) {
        items.add(item)
    }

    fun isEmpty(): Boolean = items.isEmpty()

    fun size(): Int = items.size

    fun clear() = items.clear()

    fun pop(): T? {
        if (items.isEmpty()) {
            return null
        }
        return items.removeAt(items.size - 1)
    }

    fun peek(): T? {
        if (items.isEmpty()) {
            return null
        }
        return this.items[this.items.count() - 1]
    }

    fun copyTo(other: Stack<T>) {
        for (item in items) {
            other.items.add(item)
        }
    }

    override fun toString() = this.items.toString()
}