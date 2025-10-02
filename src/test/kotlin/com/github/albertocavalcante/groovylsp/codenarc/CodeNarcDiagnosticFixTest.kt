package com.github.albertocavalcante.groovylsp.codenarc

import com.github.albertocavalcante.groovylsp.config.ServerConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test to verify the CodeNarc diagnostic triplication fix.
 * This test ensures that CodeNarc violations are only reported once per occurrence.
 */
class CodeNarcDiagnosticFixTest {

    @Test
    fun `should not triplicate diagnostics when analyzing groovy code with violations`() = runTest {
        // Given: A sample Groovy code with known violations
        val groovyCodeWithViolations = """
            class TestClass {
                def method() {
                    def x = 1   ; // Unnecessary semicolon
                    return x
                }
            }
        """.trimIndent()

        // Mock configuration provider
        val configProvider = object : ConfigurationProvider {
            override fun getServerConfiguration() = ServerConfiguration()
            override fun getWorkspaceRoot() = Paths.get(".")
        }

        // When: We analyze the code
        val analysisService = CodeAnalysisService(configProvider)
        val diagnostics = analysisService.analyzeAndGetDiagnostics(groovyCodeWithViolations, "TestClass.groovy")

        // Then: Each violation should appear exactly once (no triplication)
        // We expect some diagnostics but they should be unique
        assertTrue(diagnostics.isNotEmpty(), "Should detect at least one diagnostic")

        // Verify no duplicate diagnostics for the same line/rule
        val violationsByLineAndRule = diagnostics.groupBy { "${it.range.start.line}:${it.code.left}" }
        violationsByLineAndRule.forEach { (lineAndRule, violations) ->
            assertEquals(
                1,
                violations.size,
                "Violation $lineAndRule should appear exactly once, but found ${violations.size} times",
            )
        }

        println("✅ CodeNarc diagnostic triplication fix verified!")
        println("   Found ${diagnostics.size} unique diagnostics")
        diagnostics.forEach { diagnostic ->
            println("   - Line ${diagnostic.range.start.line + 1}: ${diagnostic.code.left} - ${diagnostic.message}")
        }
    }

    @Test
    fun `should handle empty source code gracefully`() = runTest {
        val configProvider = object : ConfigurationProvider {
            override fun getServerConfiguration() = ServerConfiguration()
            override fun getWorkspaceRoot() = Paths.get(".")
        }

        val analysisService = CodeAnalysisService(configProvider)
        val diagnostics = analysisService.analyzeAndGetDiagnostics("", "empty.groovy")

        // Should not crash and should return empty diagnostics
        assertTrue(diagnostics.isEmpty(), "Empty source should produce no diagnostics")
    }

    @Test
    fun `should handle basic groovy code without violations`() = runTest {
        val cleanGroovyCode = """
            class CleanClass {
                def cleanMethod() {
                    return "clean"
                }
            }
        """.trimIndent()

        val configProvider = object : ConfigurationProvider {
            override fun getServerConfiguration() = ServerConfiguration()
            override fun getWorkspaceRoot() = Paths.get(".")
        }

        val analysisService = CodeAnalysisService(configProvider)
        val diagnostics = analysisService.analyzeAndGetDiagnostics(cleanGroovyCode, "CleanClass.groovy")

        // Clean code might still have some style violations, but should not crash
        println("Clean code analysis found ${diagnostics.size} diagnostics")
        diagnostics.forEach { diagnostic ->
            println("  - ${diagnostic.code.left}: ${diagnostic.message}")
        }
    }
}
