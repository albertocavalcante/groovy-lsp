package com.github.albertocavalcante.groovylsp.providers.codeaction

import org.eclipse.lsp4j.TextEdit

/**
 * A function that creates a TextEdit for a specific CodeNarc violation.
 * Returns null if the fix cannot be applied.
 */
typealias FixHandler = (FixContext) -> TextEdit?

/**
 * Registry of fix handlers keyed by CodeNarc rule name.
 * Provides O(1) lookup for fix handlers and their associated titles.
 */
object FixHandlerRegistry {
    private val handlers: Map<String, FixHandler> = mapOf(
        // Phase 1: Whitespace/Formatting
        "TrailingWhitespace" to ::fixTrailingWhitespace,
        "UnnecessarySemicolon" to ::fixUnnecessarySemicolon,
        "ConsecutiveBlankLines" to ::fixConsecutiveBlankLines,
        "BlankLineBeforePackage" to ::fixBlankLineBeforePackage,

        // Phase 2: Import Cleanup
        "UnusedImport" to ::fixRemoveImportLine,
        "DuplicateImport" to ::fixRemoveImportLine,
        "UnnecessaryGroovyImport" to ::fixRemoveImportLine,
        "ImportFromSamePackage" to ::fixRemoveImportLine,

        // Phase 3: Convention Fixes
        "UnnecessaryPublicModifier" to ::fixUnnecessaryPublicModifier,
        "UnnecessaryDefInVariableDeclaration" to ::fixUnnecessaryDef,
        "UnnecessaryGetter" to ::fixUnnecessaryGetter,
        "UnnecessarySetter" to ::fixUnnecessarySetter,
        "UnnecessaryDotClass" to ::fixUnnecessaryDotClass,
    )

    private val titles: Map<String, String> = mapOf(
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

    init {
        // Validate: every handler has a title
        val handlersWithoutTitles = handlers.keys - titles.keys
        require(handlersWithoutTitles.isEmpty()) {
            "Handlers registered without titles: $handlersWithoutTitles"
        }
    }

    /**
     * Gets the fix handler for a given CodeNarc rule name.
     * @param ruleName The CodeNarc rule name (e.g., "TrailingWhitespace")
     * @return The fix handler function, or null if no handler is registered
     */
    fun getHandler(ruleName: String): FixHandler? = handlers[ruleName]

    /**
     * Returns all registered rule names.
     * Useful for testing and introspection.
     */
    fun getRegisteredRules(): Set<String> = handlers.keys

    /**
     * Gets the title for a fix action for a given rule.
     * @param ruleName The CodeNarc rule name
     * @return The human-readable title for the fix action
     */
    fun getTitle(ruleName: String): String = titles[ruleName] ?: "Fix $ruleName"
}

// Placeholder fix handler implementations - will be implemented in later tasks
// These functions return null until their respective implementation tasks are completed.
// The FunctionOnlyReturningConstant warning is expected for placeholders.

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixTrailingWhitespace(context: FixContext): TextEdit? = null

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixUnnecessarySemicolon(context: FixContext): TextEdit? = null

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixConsecutiveBlankLines(context: FixContext): TextEdit? = null

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixBlankLineBeforePackage(context: FixContext): TextEdit? = null

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixRemoveImportLine(context: FixContext): TextEdit? = null

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixUnnecessaryPublicModifier(context: FixContext): TextEdit? = null

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixUnnecessaryDef(context: FixContext): TextEdit? = null

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixUnnecessaryGetter(context: FixContext): TextEdit? = null

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixUnnecessarySetter(context: FixContext): TextEdit? = null

@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
private fun fixUnnecessaryDotClass(context: FixContext): TextEdit? = null
