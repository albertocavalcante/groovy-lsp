package com.github.albertocavalcante.groovylsp.test

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import com.github.albertocavalcante.groovylsp.providers.completion.CompletionProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI

/**
 * Reusable fixture for testing LSP features against compiled Groovy code.
 */
class LspTestFixture {
    val compilationService = GroovyCompilationService()
    val uri = URI.create("file:///test.groovy")

    fun compile(content: String) = runBlocking {
        val result = compilationService.compile(uri, content)
        if (!result.isSuccess) {
            throw RuntimeException("Compilation failed: ${result.diagnostics}")
        }
    }

    fun assertCompletionContains(line: Int, char: Int, vararg expectedLabels: String) {
        val completions = CompletionProvider.getContextualCompletions(
            uri.toString(),
            line,
            char,
            compilationService,
        )

        val labels = completions.map { it.label }.toSet()
        val missing = expectedLabels.filter { it !in labels }

        assertTrue(missing.isEmpty(), "Missing completions at $line:$char. Expected $missing to be in $labels")
    }

    fun assertCompletionDoesNotContain(line: Int, char: Int, vararg unexpectedLabels: String) {
        val completions = CompletionProvider.getContextualCompletions(
            uri.toString(),
            line,
            char,
            compilationService,
        )

        val labels = completions.map { it.label }.toSet()
        val present = unexpectedLabels.filter { it in labels }

        assertTrue(present.isEmpty(), "Unexpected completions at $line:$char. Found $present in $labels")
    }
}
