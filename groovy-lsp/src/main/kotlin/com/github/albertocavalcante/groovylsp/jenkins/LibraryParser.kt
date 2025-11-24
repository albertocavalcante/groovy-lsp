package com.github.albertocavalcante.groovylsp.jenkins

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.slf4j.LoggerFactory

/**
 * Represents a reference to a Jenkins shared library in a Jenkinsfile.
 */
data class LibraryReference(val name: String, val version: String? = null)

/**
 * Parses @Library annotations and library() calls from Jenkinsfile source.
 */
class LibraryParser {
    private val logger = LoggerFactory.getLogger(LibraryParser::class.java)

    /**
     * Parses library references from Jenkinsfile source code.
     */
    @Suppress("TooGenericExceptionCaught")
    fun parseLibraries(source: String): List<LibraryReference> = try {
        val ast = parseToAst(source)
        extractLibraries(ast)
    } catch (e: Exception) {
        logger.warn("Failed to parse libraries from Jenkinsfile", e)
        emptyList()
    }

    private fun parseToAst(source: String): ModuleNode {
        val config = org.codehaus.groovy.control.CompilerConfiguration()
        val unit = CompilationUnit(config)
        val sourceUnit = SourceUnit("Jenkinsfile", source, config, null, unit.errorCollector)
        unit.addSource(sourceUnit)
        unit.compile(Phases.CONVERSION)
        return sourceUnit.ast
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun extractLibraries(ast: ModuleNode): List<LibraryReference> {
        val libraries = mutableListOf<LibraryReference>()

        // Extract from @Library annotations on classes (including script class)
        ast.classes?.forEach { classNode ->
            // Check class-level annotations
            classNode.annotations?.forEach { annotation ->
                if (isLibraryAnnotation(annotation)) {
                    libraries.addAll(extractFromAnnotation(annotation))
                }
            }

            // Check field-level annotations (for @Library('utils') _ syntax)
            classNode.fields?.forEach { field ->
                field.annotations?.forEach { annotation ->
                    if (isLibraryAnnotation(annotation)) {
                        libraries.addAll(extractFromAnnotation(annotation))
                    }
                }
            }

            // Check method-level statements for variable declarations with annotations
            classNode.methods?.forEach { method ->
                method.code?.visit(object : org.codehaus.groovy.ast.CodeVisitorSupport() {
                    override fun visitDeclarationExpression(
                        expression: org.codehaus.groovy.ast.expr.DeclarationExpression,
                    ) {
                        expression.annotations?.forEach { annotation ->
                            if (isLibraryAnnotation(annotation)) {
                                libraries.addAll(extractFromAnnotation(annotation))
                            }
                        }
                        super.visitDeclarationExpression(expression)
                    }
                })
            }
        }

        // Extract from @Library annotations on import statements
        ast.imports?.forEach { importNode ->
            importNode.annotations?.forEach { annotation ->
                if (isLibraryAnnotation(annotation)) {
                    libraries.addAll(extractFromAnnotation(annotation))
                }
            }
        }

        // Also check star imports
        ast.starImports?.forEach { importNode ->
            importNode.annotations?.forEach { annotation ->
                if (isLibraryAnnotation(annotation)) {
                    libraries.addAll(extractFromAnnotation(annotation))
                }
            }
        }

        // Extract from library() method calls in script
        ast.statementBlock?.statements?.forEach { statement ->
            if (statement is ExpressionStatement) {
                val expr = statement.expression
                if (expr is MethodCallExpression && expr.methodAsString == "library") {
                    extractFromMethodCall(expr)?.let { libraries.add(it) }
                }
            }
        }

        return libraries
    }

    private fun isLibraryAnnotation(annotation: AnnotationNode): Boolean = annotation.classNode.name == "Library" ||
        annotation.classNode.name.endsWith(".Library")

    @Suppress("NestedBlockDepth")
    private fun extractFromAnnotation(annotation: AnnotationNode): List<LibraryReference> {
        val libraries = mutableListOf<LibraryReference>()

        // Check for value member (single string or list)
        val valueMember = annotation.getMember("value")
        when (valueMember) {
            is ConstantExpression -> {
                parseLibraryString(valueMember.text)?.let { libraries.add(it) }
            }
            is ListExpression -> {
                valueMember.expressions.forEach { expr ->
                    if (expr is ConstantExpression) {
                        parseLibraryString(expr.text)?.let { libraries.add(it) }
                    }
                }
            }
        }

        return libraries
    }

    private fun extractFromMethodCall(call: MethodCallExpression): LibraryReference? {
        val args = call.arguments
        if (args is ArgumentListExpression && args.expressions.isNotEmpty()) {
            val firstArg = args.expressions[0]
            if (firstArg is ConstantExpression) {
                return parseLibraryString(firstArg.text)
            }
        }
        return null
    }

    /**
     * Parses a library string like "name@version" into a LibraryReference.
     */
    private fun parseLibraryString(libraryString: String): LibraryReference? {
        if (libraryString.isBlank()) return null

        val parts = libraryString.split("@", limit = 2)
        return when (parts.size) {
            1 -> LibraryReference(parts[0].trim())
            2 -> LibraryReference(parts[0].trim(), parts[1].trim())
            else -> null
        }
    }
}
