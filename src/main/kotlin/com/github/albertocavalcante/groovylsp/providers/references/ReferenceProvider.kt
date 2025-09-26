package com.github.albertocavalcante.groovylsp.providers.references

import com.github.albertocavalcante.groovylsp.ast.resolveToDefinition
import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import com.github.albertocavalcante.groovylsp.converters.LocationConverter
import com.github.albertocavalcante.groovylsp.errors.GroovyLspException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Provider for finding references to symbols in Groovy code.
 */
class ReferenceProvider(private val compilationService: GroovyCompilationService) {
    private val logger = LoggerFactory.getLogger(ReferenceProvider::class.java)

    /**
     * Find all references to the symbol at the given position.
     *
     * @param uri The URI of the document
     * @param position The position in the document
     * @param includeDeclaration Whether to include the declaration in the results
     * @return Flow of locations where the symbol is referenced
     */
    @Suppress("TooGenericExceptionCaught") // TODO: Review if catch-all is needed - currently serves as final fallback
    fun provideReferences(uri: String, position: Position, includeDeclaration: Boolean): Flow<Location> = channelFlow {
        logger.debug("Finding references for $uri at ${position.line}:${position.character}")

        try {
            val context = createReferenceContext(uri, position) ?: return@channelFlow
            val definition = resolveTargetDefinition(context) ?: return@channelFlow

            logger.debug("Found definition: ${definition.javaClass.simpleName}")
            findReferences(definition, context.visitor, context.symbolTable, includeDeclaration)
        } catch (e: GroovyLspException) {
            logger.error("LSP error finding references", e)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid arguments for finding references", e)
        } catch (e: IllegalStateException) {
            logger.error("Invalid state while finding references", e)
        } catch (e: Exception) {
            logger.error("Unexpected error finding references", e)
        }
    }

    /**
     * Context for reference resolution.
     */
    private data class ReferenceContext(
        val documentUri: URI,
        val visitor: com.github.albertocavalcante.groovylsp.ast.AstVisitor,
        val symbolTable: com.github.albertocavalcante.groovylsp.ast.SymbolTable,
        val targetNode: ASTNode,
    )

    /**
     * Parameters for processing a node reference.
     */
    private data class ProcessNodeParams(
        val node: ASTNode,
        val definition: ASTNode,
        val visitor: com.github.albertocavalcante.groovylsp.ast.AstVisitor,
        val symbolTable: com.github.albertocavalcante.groovylsp.ast.SymbolTable,
        val includeDeclaration: Boolean,
        val emittedLocations: MutableSet<String>,
    )

    /**
     * Create reference context from URI and position.
     */
    private fun createReferenceContext(uri: String, position: Position): ReferenceContext? =
        createReferenceContextInternal(uri, position) ?: logAndReturnNull("No referenceable node found at position")

    private fun createReferenceContextInternal(uri: String, position: Position): ReferenceContext? {
        val documentUri = URI.create(uri)
        val visitor = compilationService.getAstVisitor(documentUri) ?: return null
        val symbolTable = compilationService.getSymbolTable(documentUri) ?: return null

        val targetNode = visitor.getNodeAt(documentUri, position.line, position.character)
            ?.takeIf { it.isReferenceableSymbol() }

        return targetNode?.let { ReferenceContext(documentUri, visitor, symbolTable, it) }
    }

    /**
     * Helper to log debug message and return null.
     */
    private fun logAndReturnNull(message: String): ReferenceContext? {
        logger.debug(message)
        return null
    }

    /**
     * Resolve the target node to its definition.
     */
    private fun resolveTargetDefinition(context: ReferenceContext): ASTNode? {
        val definition = context.targetNode.resolveToDefinition(context.visitor, context.symbolTable, strict = false)
        if (definition == null) {
            logger.debug("Could not resolve definition for node")
            return null
        }
        return definition
    }

    /**
     * Find all references to the given definition node.
     */
    private suspend fun ProducerScope<Location>.findReferences(
        definition: ASTNode,
        visitor: com.github.albertocavalcante.groovylsp.ast.AstVisitor,
        symbolTable: com.github.albertocavalcante.groovylsp.ast.SymbolTable,
        includeDeclaration: Boolean,
    ) {
        val emittedLocations = mutableSetOf<String>()

        visitor.getAllNodes()
            .filter { it.hasValidPosition() }
            .forEach { node ->
                val params = ProcessNodeParams(
                    node = node,
                    definition = definition,
                    visitor = visitor,
                    symbolTable = symbolTable,
                    includeDeclaration = includeDeclaration,
                    emittedLocations = emittedLocations,
                )
                processNode(params)
            }
    }

    /**
     * Process a single node to check if it references our target definition.
     */
    private suspend fun ProducerScope<Location>.processNode(params: ProcessNodeParams) {
        val nodeDefinition = params.node.resolveToDefinition(params.visitor, params.symbolTable, strict = false)
        if (nodeDefinition != params.definition) return

        val isPartOfDeclaration = params.node.isPartOfDeclaration(params.visitor)
        logger.debug(
            "Node ${params.node.javaClass.simpleName} at ${params.node.lineNumber}:${params.node.columnNumber} - " +
                "isPartOfDeclaration: $isPartOfDeclaration, includeDeclaration: ${params.includeDeclaration}",
        )

        if (params.includeDeclaration || !isPartOfDeclaration) {
            emitUniqueLocation(params.node, params.visitor, params.emittedLocations)
        }
    }

    /**
     * Check if a node is part of a declaration.
     */
    private fun ASTNode.isPartOfDeclaration(
        visitor: com.github.albertocavalcante.groovylsp.ast.AstVisitor,
    ): Boolean = when {
        this is org.codehaus.groovy.ast.Parameter -> true
        this is org.codehaus.groovy.ast.MethodNode -> true
        this is org.codehaus.groovy.ast.FieldNode -> true
        this is org.codehaus.groovy.ast.PropertyNode -> true
        this is org.codehaus.groovy.ast.ClassNode -> true
        this is org.codehaus.groovy.ast.expr.VariableExpression -> {
            val parent = visitor.getParent(this)
            val isDecl = parent is org.codehaus.groovy.ast.expr.DeclarationExpression &&
                parent.leftExpression == this
            if (isDecl) {
                logger.debug("Found variable ${this.name} as part of declaration")
            }
            isDecl
        }
        else -> false
    }

    /**
     * Check if this node has valid position information for LSP.
     */
    private fun ASTNode.hasValidPosition(): Boolean = lineNumber > 0 && columnNumber > 0

    /**
     * Check if this node represents a referenceable symbol.
     */
    private fun ASTNode.isReferenceableSymbol(): Boolean = when (this) {
        is org.codehaus.groovy.ast.expr.VariableExpression -> true
        is org.codehaus.groovy.ast.expr.MethodCallExpression -> true
        is org.codehaus.groovy.ast.MethodNode -> true
        is org.codehaus.groovy.ast.FieldNode -> true
        is org.codehaus.groovy.ast.PropertyNode -> true
        is org.codehaus.groovy.ast.Parameter -> true
        is org.codehaus.groovy.ast.ClassNode -> true
        is org.codehaus.groovy.ast.expr.ConstructorCallExpression -> true
        is org.codehaus.groovy.ast.expr.PropertyExpression -> true
        is org.codehaus.groovy.ast.expr.ClassExpression -> true
        else -> false
    }

    /**
     * Emit a location for this node if it hasn't been seen before.
     */
    private suspend fun ProducerScope<Location>.emitUniqueLocation(
        node: ASTNode,
        visitor: com.github.albertocavalcante.groovylsp.ast.AstVisitor,
        seen: MutableSet<String>,
    ) {
        val location = LocationConverter.nodeToLocation(node, visitor) ?: return
        val key = "${location.uri}:${location.range.start.line}:${location.range.start.character}"
        if (seen.add(key)) {
            send(location)
        }
    }
}
