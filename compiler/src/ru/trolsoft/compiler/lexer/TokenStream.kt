package ru.trolsoft.compiler.lexer

import ru.trolsoft.therat.lang.EmptyToken
import ru.trolsoft.therat.lang.GroupToken
import ru.trolsoft.therat.lang.Token
import ru.trolsoft.therat.lang.strTokens
import ru.trolsoft.compiler.utils.Stack

class TokenStream(root: MutableList<Token>, val path: String) {

    data class Group(val tokens: MutableList<Token>, val pos: Int)
    class State(
        var pos: Int,
        var lastIndex: Int,
        val lastToken: Token?,
        val history: Stack<Group>
    )

    private val history = Stack<Group>()
    private var pos: Int = 0
    private var tokens = root
    private var lastIndex = -1
    private val markedTokenIndexes = mutableListOf<Int>()

    private var lastToken: Token? = null

    constructor(tokenStream: TokenStream) :
            this(tokenStream.tokens.toMutableList(), tokenStream.path)

    fun eof(): Boolean {
        if (lastToken != null) {
            return false
        }
        lastToken = nextTokenOrNull()
        return lastToken == null
    }

    fun nextToken(): Token {
        val last = lastToken
        if (last != null) {
            lastToken = null
            return last
        }
        return nextTokenOrNull()!!
    }


    fun unreadLast(t: Token? = null) {
        assert(lastToken == null)
        lastToken = t ?: tokens[lastIndex]
    }

    private fun nextTokenOrNull(): Token? {
        if (pos >= tokens.size) {
            val parent = history.pop() ?: return null
            pos = parent.pos
            tokens = parent.tokens
            assert(markedTokenIndexes.isEmpty())
            return nextTokenOrNull()
        }
        while (pos < tokens.size) {
            val savePos = pos
            val res = tokens[pos++]
            if (res is EmptyToken) {
                continue
            } else if (res is GroupToken) {
                assert(markedTokenIndexes.isEmpty())
                history.push(Group(tokens, pos))
                pos = 0
                tokens = res.tokens
                return nextTokenOrNull()
            }
            lastIndex = savePos
            return res
        }
        return nextTokenOrNull()
    }

    fun nextMarkedToken(): Token {
        val result = nextToken()
        markLast()
        return result
    }

    fun reset() {
        pos = 0
        while (!history.isEmpty()) {
            tokens = history.pop()!!.tokens
        }
        lastIndex = -1
    }

    fun markLast() {
        assert(lastIndex >= 0)
        markedTokenIndexes.add(lastIndex)
    }

    fun removeMarked() {
        for (index in markedTokenIndexes) {
            tokens[index] = EmptyToken(tokens[index].location)
        }
        markedTokenIndexes.clear()
    }

    fun unmarkAll() = markedTokenIndexes.clear()

    fun removeLast() {
        assert(lastIndex >= 0)
        tokens[lastIndex] = EmptyToken(tokens[lastIndex].location)
        lastIndex = -1
    }

    fun replaceMarked(group: MutableList<Token>) {
        var first = true
        for (index in markedTokenIndexes) {
            if (first) {
                tokens[index] = GroupToken(group, tokens[index].location)
                first = false
            } else {
                tokens[index] = EmptyToken(tokens[index].location)
            }
        }
        markedTokenIndexes.clear()
    }

    fun replaceMarked(stream: TokenStream) {
        replaceMarked(stream.tokens)
    }


    fun str(): String = strTokens(tokens)

    fun copyOf(): TokenStream {
        val newList = mutableListOf<Token>()
        newList.addAll(tokens)
        return TokenStream(newList, path)
    }

    fun getEofLocation(): SourceLocation {
        reset()
        var last: Token? = null
        while (!eof()) {
            last = nextToken()
        }
        return last?.location ?: SourceLocation("", 0, 0)
    }

    fun remove(function: (t: Token) -> Boolean) {
        reset()
        while (!eof()) {
            val t = nextToken()
            if (function(t)) {
                removeLast()
            }
        }
        reset()
    }

    fun saveState(): State {
        assert(markedTokenIndexes.isEmpty())
        val historyCopy = Stack<Group>()
        history.copyTo(historyCopy)
        return State(pos, lastIndex, lastToken, historyCopy)
    }

    fun restoreState(state: State) {
        assert(markedTokenIndexes.isEmpty())
//        lastIndex = state.lastIndex
        lastToken = null//state.lastToken
        //pos = state.pos
        pos = state.lastIndex

        history.clear()
        state.history.copyTo(history)
    }
}