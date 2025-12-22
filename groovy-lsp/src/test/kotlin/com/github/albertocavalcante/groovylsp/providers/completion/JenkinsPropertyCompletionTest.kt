package com.github.albertocavalcante.groovylsp.providers.completion

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import com.github.albertocavalcante.groovylsp.config.ServerConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD: Tests for Jenkins property completions (env., currentBuild.).
 *
 * These tests verify that when typing `env.` or `currentBuild.` in a Jenkins file,
 * the completion provider returns Jenkins-specific properties instead of generic
 * Object methods.
 */
class JenkinsPropertyCompletionTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var compilationService: GroovyCompilationService

    @BeforeEach
    fun setUp() {
        compilationService = GroovyCompilationService()
        // Initialize workspace with Jenkins file detection
        compilationService.workspaceManager.initializeWorkspace(tempDir)
        // Initialize Jenkins context to load enrichment metadata
        compilationService.workspaceManager.initializeJenkinsWorkspace(ServerConfiguration())
    }

    @Test
    fun `env dot should provide BUILD_ID completion in Jenkinsfile`() = runTest {
        // Create a Jenkinsfile with simple property access
        val jenkinsfile = tempDir.resolve("Jenkinsfile")
        val code = """
            def x = env.
        """.trimIndent()
        Files.writeString(jenkinsfile, code)

        val uri = jenkinsfile.toUri().toString()
        val content = Files.readString(jenkinsfile)

        val completions = CompletionProvider.getContextualCompletions(
            uri = uri,
            line = 0,
            character = 12, // After "env."
            compilationService = compilationService,
            content = content,
        )

        val labels = completions.map { it.label }
        assertTrue(
            labels.any { it == "BUILD_ID" || it == "BUILD_NUMBER" || it == "JOB_NAME" },
            "Should have Jenkins env properties, got: $labels",
        )
    }

    @Test
    fun `currentBuild dot should provide result completion in Jenkinsfile`() = runTest {
        val jenkinsfile = tempDir.resolve("Jenkinsfile")
        val code = """
            def x = currentBuild.
        """.trimIndent()
        Files.writeString(jenkinsfile, code)

        val uri = jenkinsfile.toUri().toString()
        val content = Files.readString(jenkinsfile)

        val completions = CompletionProvider.getContextualCompletions(
            uri = uri,
            line = 0,
            character = 21, // After "currentBuild."
            compilationService = compilationService,
            content = content,
        )

        val labels = completions.map { it.label }
        assertTrue(
            labels.any { it == "result" || it == "number" || it == "displayName" },
            "Should have currentBuild properties, got: $labels",
        )
    }

    @Test
    fun `env dot should NOT provide Jenkins properties in regular Groovy file`() = runTest {
        val groovyFile = tempDir.resolve("Script.groovy")
        Files.writeString(groovyFile, "def env = [:]; env.")

        val uri = groovyFile.toUri().toString()
        val content = Files.readString(groovyFile)

        val completions = CompletionProvider.getContextualCompletions(
            uri = uri,
            line = 0,
            character = 20, // After "env."
            compilationService = compilationService,
            content = content,
        )

        val labels = completions.map { it.label }
        // Should NOT have Jenkins-specific env properties
        assertFalse(
            labels.any { it == "BUILD_ID" || it == "JOB_NAME" },
            "Should NOT have Jenkins env properties in regular Groovy file, got: $labels",
        )
    }

    @Test
    fun `unknown qualifier should still get normal method completions`() = runTest {
        val jenkinsfile = tempDir.resolve("Jenkinsfile")
        Files.writeString(jenkinsfile, "def myList = []; myList.")

        val uri = jenkinsfile.toUri().toString()
        val content = Files.readString(jenkinsfile)

        val completions = CompletionProvider.getContextualCompletions(
            uri = uri,
            line = 0,
            character = 25, // After "myList."
            compilationService = compilationService,
            content = content,
        )

        val labels = completions.map { it.label }
        // Should have normal list/collection methods
        assertTrue(
            labels.any { it == "size" || it == "each" || it == "collect" },
            "Unknown qualifier should still get normal completions, got: $labels",
        )
    }
}
