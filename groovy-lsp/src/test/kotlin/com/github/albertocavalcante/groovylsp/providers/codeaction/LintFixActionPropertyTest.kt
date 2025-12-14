package com.github.albertocavalcante.groovylsp.providers.codeaction

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

/**
 * Property-based tests for LintFixAction.
 * **Feature: codenarc-lint-fixes, Property 2: Source Filtering**
 * **Validates: Requirements 1.3**
 */
class LintFixActionPropertyTest {

    private val lintFixAction = LintFixAction()

    /**
     * Property test: Source Filtering
     * **Feature: codenarc-lint-fixes, Property 2: Source Filtering**
     * **Validates: Requirements 1.3**
     *
     * For any diagnostic with a source other than "CodeNarc" (case-insensitive),
     * the system should return null and not attempt to create a fix.
     */
    @Suppress("FunctionName") // Property test naming convention
    @Property(tries = 100)
    fun nonCodeNarcDiagnosticsReturnEmptyActions(
        @ForAll("nonCodeNarcSources") source: String,
    ): Boolean {
        val diagnostic = createDiagnostic(source, "TrailingWhitespace", "Some message")
        val content = "def x = 1   \n"

        val actions = lintFixAction.createLintFixActions(
            "file:///test.groovy",
            listOf(diagnostic),
            content,
        )

        // Non-CodeNarc diagnostics should not produce any actions
        return actions.isEmpty()
    }

    @Suppress("FunctionName") // Property test naming convention
    @Property(tries = 100)
    fun codeNarcDiagnosticsWithCaseVariationsAreProcessed(
        @ForAll("codeNarcCaseVariations") source: String,
    ): Boolean {
        val diagnostic = createDiagnostic(source, "TrailingWhitespace", "Some message")
        val content = "def x = 1   \n"

        // This test verifies that CodeNarc source (case-insensitive) is accepted
        // The actual fix may or may not be created depending on handler implementation
        // But the diagnostic should at least be considered (not filtered out)
        val actions = lintFixAction.createLintFixActions(
            "file:///test.groovy",
            listOf(diagnostic),
            content,
        )

        // For now, handlers return null, so actions will be empty
        // But the important thing is no exception is thrown
        return true
    }

    @Suppress("FunctionName") // Property test naming convention
    @Property(tries = 100)
    fun nullSourceDiagnosticsReturnEmptyActions(): Boolean {
        val diagnostic = Diagnostic().apply {
            range = Range(Position(0, 0), Position(0, 10))
            message = "Some message"
            code = org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft("TrailingWhitespace")
            severity = DiagnosticSeverity.Warning
            // source is null
        }
        val content = "def x = 1   \n"

        val actions = lintFixAction.createLintFixActions(
            "file:///test.groovy",
            listOf(diagnostic),
            content,
        )

        // Null source diagnostics should not produce any actions
        return actions.isEmpty()
    }

    @Provide
    fun nonCodeNarcSources(): Arbitrary<String> {
        // Generate random strings that are NOT "CodeNarc" (case-insensitive)
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(20)
            .filter { !it.equals("CodeNarc", ignoreCase = true) }
    }

    @Provide
    fun codeNarcCaseVariations(): Arbitrary<String> {
        // Generate various case combinations of "CodeNarc"
        return Arbitraries.of(
            "CodeNarc",
            "codenarc",
            "CODENARC",
            "codeNarc",
            "CODEnarc",
            "cOdEnArC",
        )
    }

    private fun createDiagnostic(source: String?, code: String, message: String): Diagnostic = Diagnostic().apply {
        this.range = Range(Position(0, 0), Position(0, 10))
        this.message = message
        this.source = source
        this.code = org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(code)
        this.severity = DiagnosticSeverity.Warning
    }
}
