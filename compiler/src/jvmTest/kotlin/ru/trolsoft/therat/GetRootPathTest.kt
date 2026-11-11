package ru.trolsoft.therat

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import ru.trolsoft.compiler.utils.getRootPath

class GetRootPathTest {

    @Test
    fun `getRootPath should return non-empty string`() {
        val rootPath = getRootPath()
        rootPath shouldNotBe null
        rootPath.length shouldBeGreaterThan 0
    }

    @Test
    fun `getRootPath should start with slash`() {
        val rootPath = getRootPath()
        rootPath shouldStartWith "/"
    }

    @Test
    fun `getRootPath should be valid path`() {
        val rootPath = getRootPath()
        // Path should be either "./" or "/absolute/path/"
        val isValid = rootPath == "./" || rootPath.startsWith("/")
        isValid shouldBe true
    }
}
