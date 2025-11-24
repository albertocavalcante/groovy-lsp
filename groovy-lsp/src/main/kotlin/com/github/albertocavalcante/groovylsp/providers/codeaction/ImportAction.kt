package com.github.albertocavalcante.groovylsp.providers.codeaction

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.slf4j.LoggerFactory

/**
 * Provides import actions for missing symbols.
 * Only offers actions when there's a single unambiguous match.
 */
class ImportAction(private val compilationService: GroovyCompilationService) {
    private val logger = LoggerFactory.getLogger(ImportAction::class.java)

    /**
     * Creates import actions for missing symbols identified in diagnostics.
     * Only returns actions for unambiguous single matches.
     */
    fun createImportActions(uriString: String, diagnostics: List<Diagnostic>, content: String): List<CodeAction> {
        val actions = mutableListOf<CodeAction>()

        // Find diagnostics that indicate missing symbols
        val missingSymbolDiagnostics = diagnostics.filter { isMissingSymbolDiagnostic(it) }

        for (diagnostic in missingSymbolDiagnostics) {
            val symbolName = extractSymbolName(diagnostic) ?: continue
            logger.debug("Looking for import candidates for symbol: $symbolName")

            // Find all possible imports for this symbol
            val candidates = findImportCandidates(symbolName)

            // Only offer action if exactly one candidate exists (unambiguous)
            if (candidates.size == 1) {
                val fullyQualifiedName = candidates.first()
                val action = createImportAction(uriString, symbolName, fullyQualifiedName, content, diagnostic)
                actions.add(action)
                logger.debug("Created import action for $symbolName -> $fullyQualifiedName")
            } else if (candidates.isEmpty()) {
                logger.debug("No import candidates found for $symbolName")
            } else {
                logger.debug("Ambiguous import for $symbolName: ${candidates.size} candidates found, declining")
            }
        }

        return actions
    }

    /**
     * Finds all possible fully-qualified names for a symbol from workspace and dependencies.
     */
    @Suppress("NestedBlockDepth") // Simple nested iteration, acceptable for symbol search
    private fun findImportCandidates(symbolName: String): List<String> {
        val candidates = mutableSetOf<String>()

        // Search in workspace symbols
        val workspaceSymbols = compilationService.getAllSymbolStorages()
        for ((symbolUri, symbolIndex) in workspaceSymbols) {
            // Find all class symbols with matching name
            val allClasses = symbolIndex.findByCategory(
                symbolUri,
                com.github.albertocavalcante.groovyparser.ast.symbols.SymbolCategory.CLASS,
            )

            for (symbol in allClasses) {
                if (symbol is com.github.albertocavalcante.groovyparser.ast.symbols.Symbol.Class) {
                    if (symbol.name == symbolName) {
                        val fqn = symbol.fullyQualifiedName
                        if (fqn.isNotEmpty() && fqn.contains('.')) {
                            candidates.add(fqn)
                        }
                    }
                }
            }
        }

        // Search in dependencies (classpath)
        // This would require scanning the classpath for matching class names
        // For now, we'll focus on workspace symbols as a minimal implementation
        // TODO: Add dependency scanning in future enhancement

        return candidates.toList()
    }

    /**
     * Creates a code action to add an import statement.
     */
    @Suppress("UnusedParameter") // symbolName kept for future diagnostics enhancement
    private fun createImportAction(
        uriString: String,
        symbolName: String,
        fullyQualifiedName: String,
        content: String,
        diagnostic: Diagnostic,
    ): CodeAction {
        // Find where to insert the import
        val insertPosition = findImportInsertionPoint(content)

        // Create the import statement
        val importStatement = "import $fullyQualifiedName\n"

        val edit = TextEdit(
            Range(insertPosition, insertPosition),
            importStatement,
        )

        val workspaceEdit = WorkspaceEdit().apply {
            changes = mapOf(uriString to listOf(edit))
        }

        return CodeAction("Import '$fullyQualifiedName'").apply {
            kind = CodeActionKind.QuickFix
            this.edit = workspaceEdit
            diagnostics = listOf(diagnostic)
        }
    }

    /**
     * Finds the position where a new import should be inserted.
     * Returns the position after the package declaration or at the beginning.
     */
    private fun findImportInsertionPoint(content: String): Position {
        val lines = content.split("\n")
        var insertLine = 0

        // Find the last import or package declaration
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("package ")) {
                insertLine = index + 1
            } else if (trimmed.startsWith("import ")) {
                insertLine = index + 1
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")) {
                // First non-comment, non-import line
                break
            }
        }

        return Position(insertLine, 0)
    }

    /**
     * Checks if a diagnostic indicates a missing symbol that might need an import.
     */
    private fun isMissingSymbolDiagnostic(diagnostic: Diagnostic): Boolean {
        val message = diagnostic.message.lowercase()
        return message.contains("unable to resolve class") ||
            message.contains("cannot find symbol") ||
            message.contains("cannot resolve symbol") ||
            message.contains("unresolved reference")
    }

    /**
     * Extracts the symbol name from a diagnostic message.
     */
    @Suppress("ReturnCount") // Multiple extraction patterns necessitate early returns
    private fun extractSymbolName(diagnostic: Diagnostic): String? {
        val message = diagnostic.message

        // Try to extract from "unable to resolve class X" pattern
        val unableToResolvePattern = Regex("unable to resolve class\\s+(\\w+)", RegexOption.IGNORE_CASE)
        unableToResolvePattern.find(message)?.let { return it.groupValues[1] }

        // Try to extract from "cannot find symbol X" pattern
        val cannotFindPattern = Regex("cannot find symbol\\s+(\\w+)", RegexOption.IGNORE_CASE)
        cannotFindPattern.find(message)?.let { return it.groupValues[1] }

        // Try to extract from "Unresolved reference: X" pattern
        val unresolvedPattern = Regex("unresolved reference:\\s+(\\w+)", RegexOption.IGNORE_CASE)
        unresolvedPattern.find(message)?.let { return it.groupValues[1] }

        return null
    }
}
