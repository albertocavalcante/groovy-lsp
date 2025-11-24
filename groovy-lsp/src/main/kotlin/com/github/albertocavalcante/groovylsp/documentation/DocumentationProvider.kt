package com.github.albertocavalcante.groovylsp.documentation

import com.github.albertocavalcante.groovylsp.services.DocumentProvider
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Service for retrieving documentation for AST nodes.
 * Extracts groovydoc/javadoc from workspace sources.
 */
class DocumentationProvider(private val documentProvider: DocumentProvider) {
    private val logger = LoggerFactory.getLogger(DocumentationProvider::class.java)

    // Simple cache to avoid recomputing documentation
    private val cache = mutableMapOf<String, Documentation>()

    /**
     * Get documentation for an AST node from the given document.
     *
     * @param node The AST node
     * @param documentUri The URI of the document containing the node
     * @return Documentation if found, or Documentation.EMPTY
     */
    fun getDocumentation(node: ASTNode, documentUri: URI): Documentation {
        // Only annotated nodes have line information we can use
        if (node !is AnnotatedNode) {
            return Documentation.EMPTY
        }

        val lineNumber = node.lineNumber
        if (lineNumber <= 0) {
            return Documentation.EMPTY
        }

        // Create a cache key
        val cacheKey = "$documentUri:$lineNumber"

        // Check cache first
        cache[cacheKey]?.let { return it }

        // Get the source text
        val sourceText = try {
            documentProvider.get(documentUri) ?: return Documentation.EMPTY
        } catch (e: Exception) {
            logger.warn("Failed to get document content for $documentUri", e)
            return Documentation.EMPTY
        }

        // Extract documentation
        val doc = DocExtractor.extractDocumentation(sourceText, lineNumber)

        // Cache the result
        cache[cacheKey] = doc

        return doc
    }

    /**
     * Clear the documentation cache.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Clear cached documentation for a specific document.
     */
    fun clearCache(documentUri: URI) {
        cache.entries.removeIf { it.key.startsWith("$documentUri:") }
    }
}
