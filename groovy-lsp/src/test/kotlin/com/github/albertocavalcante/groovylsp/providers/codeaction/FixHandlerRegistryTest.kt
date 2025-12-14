package com.github.albertocavalcante.groovylsp.providers.codeaction

import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.constraints.AlphaChars
import net.jqwik.api.constraints.StringLength
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for FixHandlerRegistry.
 * **Feature: codenarc-lint-fixes, Property 1: Registry Lookup Consistency**
 * **Validates: Requirements 1.1, 1.2**
 */
class FixHandlerRegistryTest {

    @Test
    fun `registered handler returns non-null for TrailingWhitespace`() {
        val handler = FixHandlerRegistry.getHandler("TrailingWhitespace")
        assertNotNull(handler, "TrailingWhitespace should have a registered handler")
    }

    @Test
    fun `unregistered handler returns null`() {
        val handler = FixHandlerRegistry.getHandler("NonExistentRule")
        assertNull(handler, "Non-existent rule should return null handler")
    }

    @Test
    fun `getTitle returns correct title for registered rule`() {
        val title = FixHandlerRegistry.getTitle("TrailingWhitespace")
        assertEquals("Remove trailing whitespace", title)
    }

    @Test
    fun `getTitle returns default title for unregistered rule`() {
        val title = FixHandlerRegistry.getTitle("NonExistentRule")
        assertEquals("Fix NonExistentRule", title)
    }

    /**
     * Property test: Registry Lookup Consistency
     * **Feature: codenarc-lint-fixes, Property 1: Registry Lookup Consistency**
     * **Validates: Requirements 1.1, 1.2**
     *
     * For any registered rule name, looking up the handler should return a non-null handler,
     * and for any unregistered rule name, the lookup should return null.
     */
    @Property(tries = 100)
    fun `property - unregistered random rule names return null handler`(
        @ForAll @AlphaChars @StringLength(min = 1, max = 50) randomRuleName: String,
    ): Boolean {
        // Skip if the random name happens to match a registered rule
        val registeredRules = setOf(
            "TrailingWhitespace",
            "UnnecessarySemicolon",
            "ConsecutiveBlankLines",
            "BlankLineBeforePackage",
            "UnusedImport",
            "DuplicateImport",
            "UnnecessaryGroovyImport",
            "ImportFromSamePackage",
            "UnnecessaryPublicModifier",
            "UnnecessaryDefInVariableDeclaration",
            "UnnecessaryGetter",
            "UnnecessarySetter",
            "UnnecessaryDotClass",
        )

        if (registeredRules.contains(randomRuleName)) {
            // For registered rules, handler should be non-null
            return FixHandlerRegistry.getHandler(randomRuleName) != null
        } else {
            // For unregistered rules, handler should be null
            return FixHandlerRegistry.getHandler(randomRuleName) == null
        }
    }

    @Test
    fun `all registered rules have handlers`() {
        val registeredRules = listOf(
            "TrailingWhitespace",
            "UnnecessarySemicolon",
            "ConsecutiveBlankLines",
            "BlankLineBeforePackage",
            "UnusedImport",
            "DuplicateImport",
            "UnnecessaryGroovyImport",
            "ImportFromSamePackage",
            "UnnecessaryPublicModifier",
            "UnnecessaryDefInVariableDeclaration",
            "UnnecessaryGetter",
            "UnnecessarySetter",
            "UnnecessaryDotClass",
        )

        for (ruleName in registeredRules) {
            val handler = FixHandlerRegistry.getHandler(ruleName)
            assertNotNull(handler, "Rule '$ruleName' should have a registered handler")
        }
    }

    @Test
    fun `all registered rules have titles`() {
        val registeredRules = listOf(
            "TrailingWhitespace" to "Remove trailing whitespace",
            "UnnecessarySemicolon" to "Remove unnecessary semicolon",
            "ConsecutiveBlankLines" to "Remove consecutive blank lines",
            "BlankLineBeforePackage" to "Remove blank lines before package",
            "UnusedImport" to "Remove unused import",
            "DuplicateImport" to "Remove duplicate import",
            "UnnecessaryGroovyImport" to "Remove unnecessary import",
            "ImportFromSamePackage" to "Remove same-package import",
            "UnnecessaryPublicModifier" to "Remove unnecessary 'public'",
            "UnnecessaryDefInVariableDeclaration" to "Remove unnecessary 'def'",
            "UnnecessaryGetter" to "Use property access",
            "UnnecessarySetter" to "Use property assignment",
            "UnnecessaryDotClass" to "Remove '.class'",
        )

        for ((ruleName, expectedTitle) in registeredRules) {
            val title = FixHandlerRegistry.getTitle(ruleName)
            assertEquals(expectedTitle, title, "Rule '$ruleName' should have title '$expectedTitle'")
        }
    }
}
