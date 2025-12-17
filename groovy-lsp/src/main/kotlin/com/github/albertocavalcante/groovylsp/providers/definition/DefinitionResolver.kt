package com.github.albertocavalcante.groovylsp.providers.definition

import arrow.core.getOrElse
import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import com.github.albertocavalcante.groovylsp.errors.CircularReferenceException
import com.github.albertocavalcante.groovylsp.errors.GroovyLspException
import com.github.albertocavalcante.groovylsp.errors.SymbolNotFoundException
import com.github.albertocavalcante.groovylsp.errors.invalidPosition
import com.github.albertocavalcante.groovylsp.errors.nodeNotFoundAtPosition
import com.github.albertocavalcante.groovylsp.providers.definition.resolution.ClasspathResolutionStrategy
import com.github.albertocavalcante.groovylsp.providers.definition.resolution.GlobalClassResolutionStrategy
import com.github.albertocavalcante.groovylsp.providers.definition.resolution.JenkinsVarsResolutionStrategy
import com.github.albertocavalcante.groovylsp.providers.definition.resolution.LocalSymbolResolutionStrategy
import com.github.albertocavalcante.groovylsp.providers.definition.resolution.ResolutionContext
import com.github.albertocavalcante.groovylsp.providers.definition.resolution.SymbolResolutionStrategy
import com.github.albertocavalcante.groovylsp.sources.SourceNavigator
import com.github.albertocavalcante.groovyparser.ast.GroovyAstModel
import com.github.albertocavalcante.groovyparser.ast.SymbolTable
import com.github.albertocavalcante.groovyparser.ast.findNodeAt
import com.github.albertocavalcante.groovyparser.ast.resolveToDefinition
import com.github.albertocavalcante.groovyparser.ast.symbols.Symbol
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ImportNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.slf4j.LoggerFactory
import java.net.URI

class DefinitionResolver(
    private val astVisitor: GroovyAstModel,
    private val symbolTable: SymbolTable,
    private val compilationService: GroovyCompilationService? = null,
    private val sourceNavigator: SourceNavigator? = null,
) {

    private val logger = LoggerFactory.getLogger(DefinitionResolver::class.java)

    /**
     * Resolution pipeline using Railway-Oriented Programming with Arrow Either.
     *
     * Strategies are tried in priority order:
     * 1. JenkinsVars - Jenkins vars/ directory lookup (highest priority)
     * 2. LocalSymbol - Same-file definitions via AST/symbol table
     * 3. GlobalClass - Cross-file class lookup via symbol index
     * 4. Classpath - JAR/JRT external dependencies (lowest priority)
     *
     * The pipeline short-circuits on first success (Either.Right).
     */
    private val resolutionPipeline: SymbolResolutionStrategy by lazy {
        val strategies = mutableListOf<SymbolResolutionStrategy>()

        // Jenkins vars lookup runs first (highest priority)
        compilationService?.let { service ->
            strategies += JenkinsVarsResolutionStrategy(service)
        }

        // Local symbol resolution
        strategies += LocalSymbolResolutionStrategy(astVisitor, symbolTable)

        // Global class and classpath lookup (require compilationService)
        compilationService?.let { service ->
            strategies += GlobalClassResolutionStrategy(service)
            strategies += ClasspathResolutionStrategy(service, sourceNavigator)
        }

        SymbolResolutionStrategy.pipeline(*strategies.toTypedArray())
    }

    /**
     * Find the definition of the symbol at the given position.
     * Throws specific exceptions for different failure scenarios.
     */

    /**
     * Result of a definition lookup.
     */
    sealed class DefinitionResult {
        data class Source(val node: ASTNode, val uri: URI) : DefinitionResult()
        data class Binary(val uri: URI, val name: String, val range: org.eclipse.lsp4j.Range? = null) :
            DefinitionResult()
    }

    /**
     * Find the definition of the symbol at the given position.
     * Throws specific exceptions for different failure scenarios.
     */
    @Suppress("TooGenericExceptionCaught") // TODO: Review if catch-all is needed as domain errors
    suspend fun findDefinitionAt(
        uri: URI,
        position: com.github.albertocavalcante.groovyparser.ast.types.Position,
    ): DefinitionResult? {
        logger.debug("Finding definition at $uri:${position.line}:${position.character}")

        return try {
            val targetNode = validateAndFindNode(uri, position)
            val result = resolveDefinition(targetNode, uri, position)

            if (result is DefinitionResult.Source) {
                validateDefinition(result.node, uri)
            }
            result
        } catch (e: GroovyLspException) {
            // Re-throw our specific exceptions
            logger.debug("Specific error finding definition: $e")
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error finding definition at $uri:${position.line}:${position.character}", e)
            throw createSymbolNotFoundException("unknown", uri, position)
        }
    }

    /**
     * Validate position and find the target node.
     */
    private fun validateAndFindNode(
        uri: URI,
        position: com.github.albertocavalcante.groovyparser.ast.types.Position,
    ): ASTNode {
        // Validate position
        if (position.line < 0 || position.character < 0) {
            handleValidationError("invalidPosition", uri, ValidationContext.PositionContext(position))
        }

        val trackedNode = astVisitor.getNodeAt(uri, position)

        // Fallback: the visitor-based tracker only records nodes with valid start coordinates and may return
        // broad container nodes for positions inside untracked expressions. Use a direct AST walk as a backup.
        val fallbackNode = (compilationService?.getAst(uri) as? ModuleNode)
            ?.findNodeAt(position.line, position.character)

        val targetNode =
            when {
                trackedNode == null && fallbackNode != null -> fallbackNode
                trackedNode != null && fallbackNode != null && isBroadContainer(trackedNode) &&
                    !isBroadContainer(fallbackNode) -> fallbackNode

                else -> trackedNode
            } ?: handleValidationError("nodeNotFound", uri, ValidationContext.PositionContext(position))

        logger.debug("Found target node: ${targetNode.javaClass.simpleName}")
        return targetNode
    }

    private fun isBroadContainer(node: ASTNode): Boolean = node is org.codehaus.groovy.ast.ClassNode ||
        node is org.codehaus.groovy.ast.MethodNode ||
        node is org.codehaus.groovy.ast.stmt.BlockStatement ||
        node is org.codehaus.groovy.ast.stmt.Statement

    /**
     * Resolve the target node to its definition using the functional resolution pipeline.
     *
     * Uses Railway-Oriented Programming with Arrow Either for clean composition:
     * - Each strategy returns Either<ResolutionError, DefinitionResult>
     * - Pipeline short-circuits on first Right (success)
     * - Strategies are tried in priority order (Jenkins vars → Local → Global → Classpath)
     */
    private suspend fun resolveDefinition(
        targetNode: ASTNode,
        uri: URI,
        position: com.github.albertocavalcante.groovyparser.ast.types.Position,
    ): DefinitionResult? {
        val context = ResolutionContext(
            targetNode = targetNode,
            documentUri = uri,
            position = position,
        )

        val result = resolutionPipeline.resolve(context)

        return result.getOrElse { error ->
            logger.debug("Resolution failed [{}]: {}", error.strategy, error.reason)
            null
        }
    }

    private fun resolveDeclaredClassNode(uri: URI, referenced: ClassNode): ClassNode? {
        val module = compilationService?.getAst(uri) as? ModuleNode ?: return null
        return module.classes.find { it.name == referenced.name }
    }

    /**
     * Check if we should attempt global lookup.
     * This is needed when local resolution returns the reference itself (common for ClassNode)
     * or when no definition was found.
     */
    private fun shouldTryGlobalLookup(targetNode: ASTNode, definition: ASTNode?, uri: URI): Boolean {
        // If no definition found locally, try global
        if (definition == null) return true

        // Import nodes represent a reference to an external type and should resolve via global/classpath lookup.
        if (targetNode is ImportNode || definition is ImportNode) return true

        // MethodCallExpression should try global lookup for Jenkins vars/ global variables
        if (targetNode is MethodCallExpression) return true

        // If definition is a ClassNode, check if it's a real declaration in the current file
        if (definition is ClassNode) {
            // Prefer module declarations over visitor tracking:
            // the visitor may also track referenced types (e.g., `new Foo()`), which should still resolve
            // via classpath/global lookup.
            val module = compilationService?.getAst(uri) as? ModuleNode
            val declaredInFile = module?.classes?.any { it.name == definition.name } == true
            return !declaredInFile
        }
        return false
    }

    /**
     * Attempt to find definition globally using the compilation service.
     */
    private fun findGlobalDefinition(targetNode: ASTNode): DefinitionResult.Source? {
        if (compilationService == null) return null

        val className = getClassName(targetNode) ?: return null

        logger.debug("Attempting global lookup for class: $className")

        // Search all symbol indices
        compilationService.getAllSymbolStorages().values.forEach { index ->
            index.getUris().forEach { uri ->
                // Look for Class symbol with matching name
                val symbol = index.find<Symbol.Class>(uri, className)
                if (symbol != null) {
                    // Found it! Load the AST node
                    val classNode = loadClassNodeFromAst(uri, className)
                    if (classNode != null) {
                        return DefinitionResult.Source(classNode, uri)
                    } else {
                        logger.warn("Symbol found in index but ClassNode not found in AST for $className at $uri")
                    }
                }
            }
        }
        return null
    }

    /**
     * Attempt to find definition on the classpath (JARs).
     * First tries to navigate to source code if available, otherwise returns binary reference.
     *
     * Returns null for URIs that VS Code cannot open (jrt:, jar:) to avoid errors.
     */

    /**
     * Attempt to find definition on the classpath (JARs).
     * First tries to navigate to source code if available, otherwise returns binary reference.
     *
     * Returns null for URIs that VS Code cannot open (jrt:, jar:) to avoid errors.
     */
    private suspend fun findClasspathDefinition(targetNode: ASTNode): DefinitionResult? {
        if (compilationService == null) return null

        val className = getClassName(targetNode) ?: return null

        val classpathUri = compilationService.findClasspathClass(className) ?: return null

        // Try to navigate to source code if SourceNavigator is available
        if (sourceNavigator != null) {
            try {
                val result = sourceNavigator.navigateToSource(classpathUri, className)
                when (result) {
                    is SourceNavigator.SourceResult.SourceLocation -> {
                        // Return Binary result pointing to the extracted source file location.
                        // The client will open this file: URI directly.
                        logger.debug("Found source for $className at ${result.uri}")

                        // Construct range if line number is available
                        val range = if (result.lineNumber != null && result.lineNumber > 0) {
                            val line0 = result.lineNumber - 1
                            org.eclipse.lsp4j.Range(
                                org.eclipse.lsp4j.Position(line0, 0),
                                org.eclipse.lsp4j.Position(line0, 0),
                            )
                        } else {
                            null
                        }

                        return DefinitionResult.Binary(result.uri, className, range)
                    }

                    is SourceNavigator.SourceResult.BinaryOnly -> {
                        logger.debug("No source available for $className: ${result.reason}")
                        // Fall through to check if URI is resolvable
                    }
                }
            } catch (e: Exception) {
                // Re-throw CancellationException to preserve coroutine cancellation semantics
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                logger.warn("Failed to navigate to source for $className: ${e.message}", e)
                // Fall through to check if URI is resolvable
            }
        }

        // Only return binary result for URIs that VS Code can actually open
        // jrt: and jar: URIs are handled above by SourceNavigator
        // If we reach here, source resolution failed and we cannot open these URIs
        return when (classpathUri.scheme) {
            "file" -> DefinitionResult.Binary(classpathUri, className)
            "jrt" -> {
                // JDK source resolution was attempted above via JdkSourceResolver
                // If we reach here, src.zip extraction failed (not found, corrupted, etc.)
                logger.debug("JDK source not available for $className")
                null
            }

            "jar" -> {
                // Maven source JAR resolution was attempted above
                // If we reach here, no source JAR available for this dependency
                logger.debug("No source available for $className - jar: URI not openable")
                null
            }

            else -> {
                logger.debug("Skipping class $className with unsupported URI scheme: ${classpathUri.scheme}")
                null
            }
        }
    }

    private fun getClassName(targetNode: ASTNode): String? = when (targetNode) {
        is ClassNode -> targetNode.name
        is ConstructorCallExpression -> targetNode.type.name
        is ClassExpression -> targetNode.type.name
        is ImportNode -> targetNode.type?.name ?: targetNode.className
        else -> null
    }

    /**
     * Extract a method name from the target node for Jenkins vars/ lookup.
     *
     * This handles the common case where clicking on a method name in a method call
     * like `buildPlugin()` returns the inner ConstantExpression instead of the
     * MethodCallExpression. We use heuristics to extract a valid method name:
     *
     * - MethodCallExpression: Use methodAsString directly
     * - ConstantExpression: If value is a simple identifier (no dots/spaces), treat as method name
     *
     * @return The method name if it looks like a valid global variable name, null otherwise
     */
    private fun extractMethodNameForVarsLookup(targetNode: ASTNode): String? {
        return when (targetNode) {
            is MethodCallExpression -> targetNode.methodAsString
            is org.codehaus.groovy.ast.expr.ConstantExpression -> {
                // HEURISTIC: ConstantExpression.value for method names is a String like "buildPlugin"
                // We filter out values that look like string literals (contain spaces, quotes, etc.)
                val value = targetNode.value as? String ?: return null
                if (value.isBlank() || value.contains(" ") || value.contains(".")) {
                    return null
                }
                // Additional check: identifier-like (starts with letter/underscore, alphanumeric after)
                if (!value.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) {
                    return null
                }
                value
            }

            else -> null
        }
    }

    private fun loadClassNodeFromAst(uri: URI, className: String): ASTNode? {
        val ast = compilationService?.getAst(uri) ?: return null
        // Find ClassNode in the AST
        return (ast as? ModuleNode)?.classes?.find { it.name == className }
    }

    /**
     * Attempt to resolve a method call to a Jenkins vars/ global variable file.
     *
     * Jenkins Shared Libraries define global variables as files in the vars/ directory.
     * When a Jenkinsfile calls `buildPlugin()`, this should navigate to `vars/buildPlugin.groovy`.
     *
     * @param methodName The name of the method being called (e.g., "buildPlugin")
     * @return A DefinitionResult pointing to the vars file, or null if not found
     */
    private fun tryResolveJenkinsGlobalVariable(methodName: String): DefinitionResult.Source? {
        if (compilationService == null || methodName.isBlank()) return null

        val globalVars = compilationService.getJenkinsGlobalVariables()
        // TODO: Remove after debugging - temporary INFO level log
        logger.info(
            "Jenkins vars lookup for '$methodName': found ${globalVars.size} global vars: ${globalVars.map {
                it.name
            }}",
        )
        val matchingVar = globalVars.find { it.name == methodName } ?: return null

        logger.info("Found Jenkins global variable '$methodName' at ${matchingVar.path}")

        // Create a synthetic ClassNode to represent the definition location.
        // Line 1, column 1 points to the start of the file.
        val syntheticNode = org.codehaus.groovy.ast.ClassNode(matchingVar.name, 0, null)
        syntheticNode.lineNumber = 1
        syntheticNode.columnNumber = 1
        syntheticNode.lastLineNumber = 1
        syntheticNode.lastColumnNumber = 1

        return DefinitionResult.Source(syntheticNode, matchingVar.path.toUri())
    }

    /**
     * Check if we should attempt global lookup.
     * This is needed when local resolution returns the reference itself (common for ClassNode)
     * or when no definition was found.
     */

    /**
     * Attempt to find definition globally using the compilation service.
     */

    /**
     * Validate the definition node has proper position information.
     */
    private fun validateDefinition(definition: ASTNode, uri: URI): ASTNode {
        // Make sure the definition has valid position information
        if (!definition.hasValidPosition()) {
            logger.debug("Definition node has invalid position information")
            handleValidationError("invalidDefinitionPosition", uri, ValidationContext.NodeContext(definition))
        }

        logger.debug(
            "Resolved to definition: ${definition.javaClass.simpleName} " +
                "at ${definition.lineNumber}:${definition.columnNumber}",
        )
        return definition
    }

    /**
     * Create a SymbolNotFoundException with consistent parameters.
     */
    private fun createSymbolNotFoundException(
        symbol: String,
        uri: URI,
        position: com.github.albertocavalcante.groovyparser.ast.types.Position,
    ) = SymbolNotFoundException(symbol, uri, position.line, position.character)

    /**
     * Handle resolution errors by throwing appropriate exceptions.
     * Consolidates all exception throwing logic to reduce throw count.
     */
    @Suppress("ThrowsCount") // This method centralizes all throws to satisfy detekt
    private fun handleResolutionError(
        originalException: Throwable?,
        targetNode: ASTNode,
        uri: URI,
        position: com.github.albertocavalcante.groovyparser.ast.types.Position,
    ): Nothing = when (originalException) {
        is StackOverflowError -> throw CircularReferenceException(
            targetNode.javaClass.simpleName,
            listOf("resolveToDefinition", targetNode.toString()),
        )

        else -> throw createSymbolNotFoundException(targetNode.toString(), uri, position)
    }

    /**
     * Validation context for different error scenarios.
     */
    private sealed class ValidationContext {
        data class PositionContext(val position: com.github.albertocavalcante.groovyparser.ast.types.Position) :
            ValidationContext()

        data class NodeContext(val node: ASTNode) : ValidationContext()
    }

    /**
     * Handle validation errors by throwing appropriate exceptions.
     * Consolidates validation throws to reduce throw count.
     */
    @Suppress("ThrowsCount") // This method centralizes all throws to satisfy detekt
    private fun handleValidationError(errorType: String, uri: URI, context: ValidationContext): Nothing =
        when (context) {
            is ValidationContext.PositionContext -> handlePositionValidationError(errorType, uri, context.position)
            is ValidationContext.NodeContext -> handleNodeValidationError(errorType, uri, context.node)
        }

    private fun handlePositionValidationError(
        errorType: String,
        uri: URI,
        position: com.github.albertocavalcante.groovyparser.ast.types.Position,
    ): Nothing = when (errorType) {
        "invalidPosition" -> throw uri.invalidPosition(position.line, position.character, "Negative coordinates")
        "nodeNotFound" -> throw uri.nodeNotFoundAtPosition(position.line, position.character)
        else -> throw createSymbolNotFoundException("unknown", uri, position)
    }

    private fun handleNodeValidationError(errorType: String, uri: URI, node: ASTNode): Nothing = when (errorType) {
        "invalidDefinitionPosition" -> throw uri.invalidPosition(
            node.lineNumber,
            node.columnNumber,
            "Definition node has invalid position information",
        )

        else -> throw createSymbolNotFoundException(
            node.toString(),
            uri,
            com.github.albertocavalcante.groovyparser.ast.types.Position(node.lineNumber, node.columnNumber),
        )
    }

    /**
     * Find all targets at the given position for the specified target kinds.
     * Based on kotlin-lsp's getTargetsAtPosition pattern.
     */
    fun findTargetsAt(
        uri: URI,
        position: com.github.albertocavalcante.groovyparser.ast.types.Position,
        targetKinds: Set<TargetKind>,
    ): List<ASTNode> {
        logger.debug("Finding targets at $uri:${position.line}:${position.character} for kinds: $targetKinds")

        val targetNode = astVisitor.getNodeAt(uri, position)
            ?: return emptyList()

        val results = mutableListOf<ASTNode>()

        if (TargetKind.REFERENCE in targetKinds) {
            // If looking for references, return the node itself if it's a reference
            if (targetNode.isReference()) {
                results.add(targetNode)
            }
        }

        if (TargetKind.DECLARATION in targetKinds) {
            // If looking for declarations, try to resolve to definition
            val definition = targetNode.resolveToDefinition(astVisitor, symbolTable, strict = false)
            if (definition != null && definition != targetNode && definition.hasValidPosition()) {
                results.add(definition)
            }
        }

        logger.debug("Found ${results.size} targets")
        return results
    }

    /**
     * Check if a node represents a reference (not a declaration)
     */
    private fun ASTNode.isReference(): Boolean = when (this) {
        is org.codehaus.groovy.ast.expr.VariableExpression -> true
        is org.codehaus.groovy.ast.expr.MethodCallExpression -> true
        is org.codehaus.groovy.ast.expr.ConstructorCallExpression -> true
        is org.codehaus.groovy.ast.expr.PropertyExpression -> true
        else -> false
    }

    /**
     * Check if the node has valid position information
     */
    private fun ASTNode.hasValidPosition(): Boolean =
        lineNumber > 0 && columnNumber > 0 && lastLineNumber > 0 && lastColumnNumber > 0

    /**
     * Get statistics about the resolver state
     */
    fun getStatistics(): Map<String, Any> = mapOf(
        "symbolTableStats" to symbolTable.getStatistics(),
        "totalNodes" to astVisitor.getAllNodes().size,
        "totalClassNodes" to astVisitor.getAllClassNodes().size,
    )
}
