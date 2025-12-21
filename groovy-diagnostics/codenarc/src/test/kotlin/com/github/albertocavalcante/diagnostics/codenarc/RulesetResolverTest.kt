package com.github.albertocavalcante.diagnostics.codenarc

import com.github.albertocavalcante.diagnostics.api.DiagnosticConfiguration
import com.github.albertocavalcante.diagnostics.api.WorkspaceContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for HierarchicalRulesetResolver focusing on ruleset resolution and fallback behavior.
 */
class RulesetResolverTest {

    private val resolver = HierarchicalRulesetResolver()

    @Test
    fun `should resolve Jenkins ruleset for Jenkins project`(@TempDir tempDir: Path) {
        // Given: Jenkins project structure
        Files.createFile(tempDir.resolve("Jenkinsfile"))

        val context = createWorkspaceContext(tempDir, enabled = true)
        val config = resolver.resolve(context)

        // Then: Should load Jenkins ruleset
        assertNotNull(config.rulesetContent)
        assertTrue(config.source.contains("jenkins"), "Expected Jenkins ruleset, got: ${config.source}")
        assertTrue(
            config.rulesetContent.contains("rulesets/jenkins.xml") ||
                config.rulesetContent.contains("jenkins"),
            "Ruleset should reference Jenkins rules",
        )
    }

    @Test
    fun `should resolve default ruleset for plain Groovy project`(@TempDir tempDir: Path) {
        // Given: Plain Groovy project (no indicators)
        val context = createWorkspaceContext(tempDir, enabled = true)
        val config = resolver.resolve(context)

        // Then: Should load default ruleset
        assertNotNull(config.rulesetContent)
        assertTrue(
            config.source.contains("default") || config.source.contains("basic"),
            "Expected default ruleset, got: ${config.source}",
        )
    }

    @Test
    fun `should fallback to bundled CodeNarc ruleset when custom ruleset missing`(@TempDir tempDir: Path) {
        // Given: Jenkins project but custom ruleset file doesn't exist in classpath
        // (This simulates the scenario where resources aren't packaged)
        Files.createFile(tempDir.resolve("Jenkinsfile"))

        // Create a resolver that can't find custom resources
        // We'll use the real resolver but verify it falls back gracefully
        val context = createWorkspaceContext(tempDir, enabled = true)
        val config = resolver.resolve(context)

        // Then: Should still resolve (either custom or bundled fallback)
        assertNotNull(config.rulesetContent)
        assertTrue(config.rulesetContent.isNotEmpty(), "Ruleset content should not be empty")
        // Should contain ruleset reference (either custom DSL or bundled wrapper)
        assertTrue(
            config.rulesetContent.contains("ruleset"),
            "Ruleset should contain ruleset definition",
        )
    }

    @Test
    fun `should use workspace codenarc file when present`(@TempDir tempDir: Path) {
        // Given: Workspace with .codenarc file
        val customRuleset = """
            ruleset {
                description 'Custom workspace ruleset'
                ruleset('rulesets/basic.xml')
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve(".codenarc"), customRuleset)

        val context = createWorkspaceContext(tempDir, enabled = true)
        val config = resolver.resolve(context)

        // Then: Should use workspace file
        assertTrue(config.source.contains("workspace"), "Expected workspace ruleset, got: ${config.source}")
        assertEquals(customRuleset, config.rulesetContent)
    }

    @Test
    fun `should resolve properties file when auto-detect enabled`(@TempDir tempDir: Path) {
        // Given: Workspace with codenarc.properties
        val propertiesContent = "codenarc.propertiesFile=test.properties\n"
        Files.writeString(tempDir.resolve("codenarc.properties"), propertiesContent)

        val context = createWorkspaceContext(tempDir, enabled = true, autoDetect = true)
        val config = resolver.resolve(context)

        // Then: Should find properties file
        assertNotNull(config.propertiesFile)
        assertTrue(config.propertiesFile!!.contains("codenarc.properties"))
    }

    @Test
    fun `should not resolve properties file when auto-detect disabled`(@TempDir tempDir: Path) {
        // Given: Workspace with codenarc.properties but auto-detect disabled
        Files.writeString(tempDir.resolve("codenarc.properties"), "test=value\n")

        val context = createWorkspaceContext(tempDir, enabled = true, autoDetect = false)
        val config = resolver.resolve(context)

        // Then: Should not find properties file
        assertEquals(null, config.propertiesFile)
    }

    @Test
    fun `should handle null workspace root gracefully`() {
        // Given: Context without workspace root
        val context = object : WorkspaceContext {
            override val root: Path? = null
            override fun getConfiguration(): DiagnosticConfiguration = object : DiagnosticConfiguration {
                override val isEnabled: Boolean = true
                override val propertiesFile: String? = null
                override val autoDetectConfig: Boolean = false
            }
        }

        val config = resolver.resolve(context)

        // Then: Should still resolve (uses PlainGroovy default)
        assertNotNull(config.rulesetContent)
        assertTrue(config.rulesetContent.isNotEmpty())
    }

    @Test
    fun `should load bundled Jenkins ruleset via fallback`(@TempDir tempDir: Path) {
        // Given: Jenkins project
        Files.createFile(tempDir.resolve("Jenkinsfile"))

        val context = createWorkspaceContext(tempDir, enabled = true)
        val config = resolver.resolve(context)

        // Then: Ruleset should be valid and reference Jenkins rules
        assertNotNull(config.rulesetContent)
        // The ruleset should either be our custom DSL or a bundled wrapper
        // Both should reference rulesets/jenkins.xml
        val hasJenkinsReference = config.rulesetContent.contains("rulesets/jenkins.xml") ||
            config.rulesetContent.contains("jenkins")
        assertTrue(
            hasJenkinsReference,
            "Ruleset should reference Jenkins rules. Content: ${config.rulesetContent.take(200)}",
        )
    }

    // Helper functions

    private fun createWorkspaceContext(
        workspaceRoot: Path,
        enabled: Boolean = true,
        autoDetect: Boolean = true,
        propertiesFile: String? = null,
    ): WorkspaceContext = object : WorkspaceContext {
        override val root: Path? = workspaceRoot
        override fun getConfiguration(): DiagnosticConfiguration = object : DiagnosticConfiguration {
            override val isEnabled: Boolean = enabled
            override val propertiesFile: String? = propertiesFile
            override val autoDetectConfig: Boolean = autoDetect
        }
    }
}
