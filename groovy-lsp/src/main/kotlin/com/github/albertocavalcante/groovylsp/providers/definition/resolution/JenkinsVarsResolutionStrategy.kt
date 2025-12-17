package com.github.albertocavalcante.groovylsp.providers.definition.resolution

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import com.github.albertocavalcante.groovylsp.providers.definition.DefinitionResolver
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.slf4j.LoggerFactory

/**
 * Resolves method calls to Jenkins vars/ global variables.
 *
 * Jenkins Shared Libraries define global variables as files in the `vars/` directory.
 * When a Jenkinsfile calls `buildPlugin()`, this should navigate to `vars/buildPlugin.groovy`.
 *
 * **Priority: HIGHEST** - runs before any other resolution to catch Jenkins-specific patterns.
 *
 * ## Node Handling
 * Due to AST click resolution, the cursor may be on:
 * - [MethodCallExpression]: Use `methodAsString` directly
 * - [ConstantExpression]: The method name constant (heuristic: valid identifier-like string)
 */
class JenkinsVarsResolutionStrategy(private val compilationService: GroovyCompilationService) :
    SymbolResolutionStrategy {

    private val logger = LoggerFactory.getLogger(JenkinsVarsResolutionStrategy::class.java)

    override suspend fun resolve(context: ResolutionContext): ResolutionResult {
        val methodName = extractMethodName(context.targetNode)
            ?: return SymbolResolutionStrategy.notApplicable(STRATEGY_NAME)

        val globalVars = compilationService.getJenkinsGlobalVariables()
        val matchingVar = globalVars.find { it.name == methodName }
            ?: return SymbolResolutionStrategy.notFound(
                "No vars/$methodName.groovy found (${globalVars.size} vars available)",
                STRATEGY_NAME,
            )

        logger.debug("Found Jenkins global variable '{}' at {}", methodName, matchingVar.path)

        // Create a synthetic ClassNode to represent the definition location
        val syntheticNode = ClassNode(matchingVar.name, 0, null).apply {
            lineNumber = 1
            columnNumber = 1
            lastLineNumber = 1
            lastColumnNumber = 1
        }

        return SymbolResolutionStrategy.found(
            DefinitionResolver.DefinitionResult.Source(syntheticNode, matchingVar.path.toUri()),
        )
    }

    /**
     * Extract a method name from the target node for vars/ lookup.
     *
     * This handles the common case where clicking on a method name returns
     * the inner ConstantExpression instead of the MethodCallExpression.
     */
    private fun extractMethodName(node: org.codehaus.groovy.ast.ASTNode): String? = when (node) {
        is MethodCallExpression -> node.methodAsString
        is ConstantExpression -> {
            val value = node.value as? String ?: return null
            // Heuristic: must be a valid identifier (no spaces, dots, etc.)
            value.takeIf { it.isNotBlank() && it.matches(IDENTIFIER_REGEX) }
        }

        else -> null
    }

    companion object {
        private const val STRATEGY_NAME = "JenkinsVars"
        private val IDENTIFIER_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")
    }
}
