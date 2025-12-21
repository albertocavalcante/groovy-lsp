package com.github.albertocavalcante.groovylsp.providers.completion

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import com.github.albertocavalcante.groovylsp.services.ClasspathService
import com.github.albertocavalcante.groovylsp.utils.ImportUtils
import com.github.albertocavalcante.groovyparser.ast.symbols.Symbol
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * Provides auto-import completion for types not yet imported.
 * Searches both workspace symbol index and classpath for matching types.
 */
object AutoImportCompletionProvider {
    private val logger = LoggerFactory.getLogger(AutoImportCompletionProvider::class.java)
    private const val MAX_RESULTS = 20

    /**
     * Gets type completions with auto-import support.
     *
     * @param prefix The prefix typed by the user (e.g., "ArrayL")
     * @param content The file content to determine import insertion position
     * @param compilationService The compilation service for workspace index access
     * @param classpathService The classpath service for classpath type search
     * @return List of completion items with additionalTextEdits for imports
     */
    fun getTypeCompletions(
        prefix: String,
        content: String,
        compilationService: GroovyCompilationService,
        classpathService: ClasspathService,
    ): List<CompletionItem> {
        if (prefix.isBlank()) return emptyList()

        val existingImports = ImportUtils.extractExistingImports(content)
        val importPosition = ImportUtils.findImportInsertPosition(content)

        val candidates = mutableListOf<TypeCandidate>()

        // Search workspace symbol index
        val workspaceIndex = compilationService.getAllSymbolStorages()
        workspaceIndex.values.forEach { index ->
            index.getSymbols().filterIsInstance<Symbol.Class>()
                .filter { it.name.startsWith(prefix, ignoreCase = true) }
                .forEach { symbol ->
                    val fqn = symbol.fullyQualifiedName ?: return@forEach
                    if (fqn !in existingImports) {
                        candidates.add(
                            TypeCandidate(
                                simpleName = symbol.name,
                                fqn = fqn,
                                source = TypeSource.WORKSPACE,
                            ),
                        )
                    }
                }
        }

        // Search classpath
        // NOTE: ClasspathService.findClassesByPrefix may throw various runtime exceptions
        // (ClassNotFoundException, NoClassDefFoundError, etc.) - catch all to prevent completion failure
        @Suppress("TooGenericExceptionCaught")
        try {
            classpathService.findClassesByPrefix(prefix, MAX_RESULTS)
                .filter { it.fullName !in existingImports }
                .forEach { classInfo ->
                    candidates.add(
                        TypeCandidate(
                            simpleName = classInfo.simpleName,
                            fqn = classInfo.fullName,
                            source = TypeSource.CLASSPATH,
                        ),
                    )
                }
        } catch (e: Exception) {
            logger.warn("Failed to search classpath for types", e)
        }

        // Deduplicate by FQN (workspace takes precedence)
        val deduplicated = candidates
            .groupBy { it.fqn }
            .mapValues { (_, list) -> list.first { it.source == TypeSource.WORKSPACE } ?: list.first() }
            .values
            .sortedWith(compareBy({ it.source }, { it.simpleName }))
            .take(MAX_RESULTS)

        return deduplicated.map { candidate ->
            candidate.toCompletionItem(importPosition, existingImports.contains(candidate.fqn))
        }
    }

    private data class TypeCandidate(val simpleName: String, val fqn: String, val source: TypeSource)

    private enum class TypeSource {
        WORKSPACE,
        CLASSPATH,
    }

    private fun TypeCandidate.toCompletionItem(importPosition: Position, alreadyImported: Boolean): CompletionItem {
        val packageName = fqn.substringBeforeLast('.', "")

        return CompletionItem().apply {
            label = simpleName
            kind = CompletionItemKind.Class
            detail = if (packageName.isNotEmpty()) "$packageName.$simpleName" else simpleName
            insertText = simpleName
            documentation = Either.forRight(
                MarkupContent(
                    MarkupKind.MARKDOWN,
                    "**Package:** `$packageName`\n\nAuto-import: `import $fqn`",
                ),
            )

            // Add import edit if not already imported
            if (!alreadyImported) {
                additionalTextEdits = listOf(
                    TextEdit(
                        Range(importPosition, importPosition),
                        "import $fqn\n",
                    ),
                )
            }

            // Sort workspace types before classpath types
            sortText = when (source) {
                TypeSource.WORKSPACE -> "0_$simpleName"
                TypeSource.CLASSPATH -> "1_$simpleName"
            }
        }
    }
}
