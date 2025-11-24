package com.github.albertocavalcante.groovylsp.providers.codeaction

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import com.github.albertocavalcante.groovylsp.services.DocumentProvider
import com.github.albertocavalcante.groovylsp.services.Formatter
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Main provider for code actions (quick fixes) in Groovy files.
 * Supports:
 * - Missing import resolution (unambiguous only)
 * - Formatting actions
 * - Deterministic lint fixes from CodeNarc
 */
class CodeActionProvider(
    private val compilationService: GroovyCompilationService,
    private val documentProvider: DocumentProvider,
    private val formatter: Formatter,
) {
    private val logger = LoggerFactory.getLogger(CodeActionProvider::class.java)

    /**
     * Provides code actions for the given context.
     * Only returns deterministic, safe actions; ambiguous or risky fixes are declined.
     */
    suspend fun provideCodeActions(params: CodeActionParams): List<CodeAction> {
        val uri = URI.create(params.textDocument.uri)
        logger.debug("Providing code actions for ${params.textDocument.uri}")

        val actions = mutableListOf<CodeAction>()

        // Get document content
        val content = documentProvider.get(uri)
        if (content == null) {
            logger.debug("Document not found in provider: $uri")
            return emptyList()
        }

        // Add formatting action if available
        val formattingAction = FormattingAction(formatter)
            .createFormattingAction(params.textDocument.uri, content)
        if (formattingAction != null) {
            actions.add(formattingAction)
        }

        // Add import actions for missing symbols
        val importActions = ImportAction(compilationService)
            .createImportActions(params.textDocument.uri, params.context.diagnostics, content)
        actions.addAll(importActions)

        // Add lint fix actions for deterministic CodeNarc fixes
        val lintActions = LintFixAction()
            .createLintFixActions(params.textDocument.uri, params.context.diagnostics, content)
        actions.addAll(lintActions)

        logger.debug("Returning ${actions.size} code actions for ${params.textDocument.uri}")
        return actions
    }
}
