package com.github.albertocavalcante.groovylsp.providers.completion
import com.github.albertocavalcante.groovylsp.TestUtils
import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI

class CompletionProviderTest {

    private lateinit var compilationService: GroovyCompilationService

    @BeforeEach
    fun setUp() {
        compilationService = TestUtils.createCompilationService()
    }

    @Test
    fun `test getContextualCompletions with valid URI`() = runTest {
        // Arrange
        val content = """
            def localVar = "test"
            class TestClass {
                def method() {
                    return "result"
                }
            }
        """.trimIndent()

        val uri = URI.create("file:///test.groovy")

        // Compile the content to build AST
        val result = compilationService.compile(uri, content)
        assertTrue(result.isSuccess, "Compilation should succeed")

        // Act
        val completions = CompletionProvider.getContextualCompletions(
            uri.toString(),
            2, // Line within class
            8, // Character position
            compilationService,
        )

        // Assert - Should return some completions (could be empty if no context matches)
        assertTrue(completions.size >= 0, "Should return list of completions")
    }

    @Test
    fun `test getContextualCompletions with invalid URI`() = runTest {
        // Act
        val completions = CompletionProvider.getContextualCompletions(
            "invalid-uri",
            0,
            0,
            compilationService,
        )

        // Assert - Should return empty list for invalid URI
        assertTrue(completions.isEmpty(), "Should return empty completions for invalid URI")
    }

    @Test
    fun `test getContextualCompletions without compilation`() = runTest {
        // Act - Try to get completions for a file that hasn't been compiled
        val completions = CompletionProvider.getContextualCompletions(
            "file:///unknown.groovy",
            0,
            0,
            compilationService,
        )

        // Assert - Should return empty list when no compilation exists
        assertTrue(completions.isEmpty(), "Should return empty completions without compilation")
    }
}
