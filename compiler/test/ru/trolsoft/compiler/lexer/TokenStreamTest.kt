package ru.trolsoft.compiler.lexer

import org.junit.jupiter.api.Test
import ru.trolsoft.therat.lang.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenStreamTest {
    @Test fun testNextToken() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                GroupToken(mutableListOf(
                        IdentifierToken("i", loc),
                        OperatorToken("=", loc),
                        NumberToken(123, loc),
                        GroupToken(mutableListOf(
                                IdentifierToken("ch", loc),
                                OperatorToken("-", loc),
                                NumberToken(0xAB, loc, 16)
                        ), loc)
                ), loc),
                NewLineToken(loc)
        )
        val stream = TokenStream(tokens, "")
        assertTrue(stream.nextToken() is PreprocessorToken)
        assertEquals("ABC", (stream.nextToken() as IdentifierToken).name)
        assertEquals("i", (stream.nextToken() as IdentifierToken).name)
        assertTrue(stream.nextToken() is OperatorToken)
        assertTrue(stream.nextToken() is NumberToken)
        assertEquals("ch", (stream.nextToken() as IdentifierToken).name)
        assertTrue(stream.nextToken() is OperatorToken)
        assertTrue(stream.nextToken() is NumberToken)
        assertTrue(!stream.eof())
        assertTrue(stream.nextToken() is NewLineToken)
        assertTrue(stream.eof())
    }

    @Test fun testSimpleDelete() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                NumberToken(0xAB, loc, 16)
        )
        val stream = TokenStream(tokens, "")
        assertTrue(stream.nextToken() is PreprocessorToken)
        stream.markLast()
        stream.removeMarked()

        assertTrue(stream.nextToken() is IdentifierToken)
        stream.removeLast()
        assertTrue(stream.nextToken() is NumberToken)
        stream.removeLast()

        stream.reset()
        assertTrue(stream.eof())
    }

    @Test fun testRemoveTokens() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                GroupToken(mutableListOf(
                        IdentifierToken("i", loc),
                        OperatorToken("=", loc),
                        NumberToken(123, loc),
                        GroupToken(mutableListOf(
                                IdentifierToken("ch", loc),
                                OperatorToken("-", loc),
                                NumberToken(0xAB, loc, 16)
                        ), loc)
                ), loc),
                NewLineToken(loc)
        )
        val stream = TokenStream(tokens, "")

        assertTrue(stream.nextToken() is PreprocessorToken)
        assertEquals("ABC", (stream.nextToken() as IdentifierToken).name)
        assertEquals("i", (stream.nextToken() as IdentifierToken).name)
        stream.markLast()
        assertEquals("=", (stream.nextToken() as OperatorToken).name)
        stream.markLast()
        assertEquals(123, (stream.nextToken() as NumberToken).value)
        stream.markLast()
        stream.removeMarked()
        assertEquals("ch", (stream.nextToken() as IdentifierToken).name)
        assertEquals("-", (stream.nextToken() as OperatorToken).name)
        assertEquals(0xAB, (stream.nextToken() as NumberToken).value)
        assertTrue(stream.nextToken() is NewLineToken)

        stream.reset()
        assertTrue(stream.nextToken() is PreprocessorToken)
        assertEquals("ABC", (stream.nextToken() as IdentifierToken).name)

        assertEquals("ch", (stream.nextToken() as IdentifierToken).name)
        assertEquals("-", (stream.nextToken() as OperatorToken).name)
        assertEquals(0xAB, (stream.nextToken() as NumberToken).value)
        assertTrue(stream.nextToken() is NewLineToken)
    }

    @Test fun replaceTest() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                IdentifierToken("xyz", loc)
        )
        val stream = TokenStream(tokens, "")

        stream.nextToken()
        stream.markLast()
        stream.nextToken()
        stream.markLast()
        stream.replaceMarked(mutableListOf<Token>(NumberToken(123, loc)))

        stream.reset()
        //assertTrue(stream.nextToken() is PreprocessorToken)
        assertEquals(123, (stream.nextToken() as NumberToken).value)
        assertEquals("xyz", (stream.nextToken() as IdentifierToken).name)

        assertEquals("123 xyz", stream.str())
    }


    @Test fun testEmpty() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                EmptyToken(loc),
                EmptyToken(loc),
                EmptyToken(loc),
                GroupToken(mutableListOf(
                ), loc)
        )
        val stream = TokenStream(tokens, "")
        assertTrue(stream.eof())
    }

    @Test fun testUnreadLast() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                NewLineToken(loc)
        )
        val stream = TokenStream(tokens, "")
        assertTrue(stream.nextToken() is PreprocessorToken)
        val t = stream.nextToken()
        assertTrue(t is IdentifierToken)
        stream.unreadLast(t)
        assertTrue(stream.nextToken() is IdentifierToken)
        assertTrue(stream.nextToken() is NewLineToken)
    }

    @Test fun testUnreadNew() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                NewLineToken(loc)
        )
        val stream = TokenStream(tokens, "")
        assertTrue(stream.nextToken() is PreprocessorToken)
        assertTrue(stream.nextToken() is IdentifierToken)
        stream.unreadLast()
        assertTrue(stream.nextToken() is IdentifierToken)
        assertTrue(stream.nextToken() is NewLineToken)
    }
}