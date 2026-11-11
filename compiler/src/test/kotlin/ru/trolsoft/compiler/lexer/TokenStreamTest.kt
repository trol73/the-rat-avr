package ru.trolsoft.compiler.lexer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import ru.trolsoft.therat.lang.*

class TokenStreamTest {
    @Test fun `nextToken() test`() {
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
        with (TokenStream(tokens, "")) {
            nextToken().shouldBeInstanceOf<PreprocessorToken>().name shouldBe "define"
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "i"
            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe "="
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ch"
            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe "-"
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 0xAB
            eof() shouldBe false
            nextToken().shouldBeInstanceOf<NewLineToken>()
            eof() shouldBe true
        }
    }

    @Test fun `simple delete test`() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                NumberToken(0xAB, loc, 16)
        )
        with (TokenStream(tokens, "")) {
            nextToken().shouldBeInstanceOf<PreprocessorToken>().name shouldBe "define"
            markLast()
            removeMarked()

            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"
            removeLast()
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 0xAB
            removeLast()

            reset()
            eof() shouldBe true
        }
    }

    @Test fun `remove tokens test`() {
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
        with (TokenStream(tokens, "")) {
            nextToken().shouldBeInstanceOf<PreprocessorToken>().name shouldBe "define"
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "i"
            markLast()
            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe "="
            markLast()
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
            markLast()
            removeMarked()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ch"
            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe "-"
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 0xAB
            nextToken().shouldBeInstanceOf<NewLineToken>()
            eof() shouldBe true

            reset()
            nextToken().shouldBeInstanceOf<PreprocessorToken>().name shouldBe "define"
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"

            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ch"
            nextToken().shouldBeInstanceOf<OperatorToken>().name shouldBe "-"
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 0xAB

            eof() shouldBe false
            nextToken().shouldBeInstanceOf<NewLineToken>()
            eof() shouldBe true
        }
    }

    @Test fun `replace tokens test`() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                IdentifierToken("xyz", loc)
        )
        with (TokenStream(tokens, "")) {
            nextToken().shouldBeInstanceOf<PreprocessorToken>().name shouldBe "define"
            markLast()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"
            markLast()
            replaceMarked(mutableListOf(NumberToken(123, loc)))

            reset()
            nextToken().shouldBeInstanceOf<NumberToken>().value shouldBe 123
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "xyz"

            str() shouldBe "123 xyz"
        }
    }


    @Test fun `empty stream test`() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                EmptyToken(loc),
                EmptyToken(loc),
                EmptyToken(loc),
                GroupToken(mutableListOf(), loc)
        )
        val stream = TokenStream(tokens, "")
        stream.eof() shouldBe true
    }

    @Test fun `unreadLast() test`() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                NewLineToken(loc)
        )
        with (TokenStream(tokens, "")) {
            nextToken().shouldBeInstanceOf<PreprocessorToken>().name shouldBe "define"
            val t = nextToken()
            t.shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"
            unreadLast(t)
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"
            nextToken().shouldBeInstanceOf<NewLineToken>()
        }
    }

    @Test fun `unreadLast() after EOL test`() {
        val loc = SourceLocation("", 1, 1)
        val tokens = mutableListOf(
                PreprocessorToken("define", loc),
                IdentifierToken("ABC", loc),
                NewLineToken(loc)
        )
        with (TokenStream(tokens, "")) {
            nextToken().shouldBeInstanceOf<PreprocessorToken>().name shouldBe "define"
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"
            unreadLast()
            nextToken().shouldBeInstanceOf<IdentifierToken>().name shouldBe "ABC"
            nextToken().shouldBeInstanceOf<NewLineToken>()
        }
    }
}