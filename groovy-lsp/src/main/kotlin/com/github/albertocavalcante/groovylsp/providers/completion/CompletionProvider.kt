package com.github.albertocavalcante.groovylsp.providers.completion

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import com.github.albertocavalcante.groovylsp.dsl.completion.CompletionsBuilder
import com.github.albertocavalcante.groovylsp.dsl.completion.GroovyCompletions
import com.github.albertocavalcante.groovylsp.dsl.completion.completions
import com.github.albertocavalcante.groovyparser.ast.ClassSymbol
import com.github.albertocavalcante.groovyparser.ast.FieldSymbol
import com.github.albertocavalcante.groovyparser.ast.ImportSymbol
import com.github.albertocavalcante.groovyparser.ast.MethodSymbol
import com.github.albertocavalcante.groovyparser.ast.SymbolExtractor
import com.github.albertocavalcante.groovyparser.ast.VariableSymbol
import org.codehaus.groovy.control.CompilationFailedException
import org.eclipse.lsp4j.CompletionItem
import org.slf4j.LoggerFactory

/**
 * Provides completion items for Groovy language constructs using clean DSL.
 */
object CompletionProvider {
    private val logger = LoggerFactory.getLogger(CompletionProvider::class.java)

    /**
     * Get basic Groovy language completion items using DSL.
     */
    fun getBasicCompletions(): List<CompletionItem> = GroovyCompletions.basic()

    /**
     * Get contextual completions based on AST analysis.
     */
    fun getContextualCompletions(
        uri: String,
        line: Int,
        character: Int,
        compilationService: GroovyCompilationService,
    ): List<CompletionItem> = try {
        println("### getContextualCompletions called: uri=$uri, line=$line, char=$character")

        val uriObj = java.net.URI.create(uri)
        println("URI object: $uriObj")

        // Get the AST for the current file
        val ast = compilationService.getAst(uriObj)
        val astModel = compilationService.getAstModel(uriObj)

        println("AST: $ast, ASTModel: $astModel")

        if (ast == null || astModel == null) {
            println("AST or AST Model is null!")
            emptyList()
        } else {
            buildCompletionsList(ast, astModel, line, character, compilationService, uriObj)
        }
    } catch (e: CompilationFailedException) {
        // If AST analysis fails, log and return empty list
        logger.debug("AST analysis failed for completion at {}:{}: {}", line, character, e.message)
        emptyList()
    }

    private fun buildCompletionsList(
        ast: org.codehaus.groovy.ast.ASTNode,
        astModel: com.github.albertocavalcante.groovyparser.ast.GroovyAstModel,
        line: Int,
        character: Int,
        compilationService: GroovyCompilationService,
        uri: java.net.URI,
    ): List<CompletionItem> {
        println("=== buildCompletionsList called ===")
        println("URI: $uri, line: $line, character: $character")

        // Extract completion context
        val context = SymbolExtractor.extractCompletionSymbols(ast, line, character)

        // Try to detect member access (e.g., "myList.")
        val nodeAtCursor = astModel.getNodeAt(uri, line, character)
        println("NodeAtCursor result: $nodeAtCursor")
        val completionContext = detectCompletionContext(nodeAtCursor, astModel)
        println("CompletionContext result: $completionContext")

        return completions {
            // Add local symbol completions
            addClasses(context.classes)
            addMethods(context.methods)
            addFields(context.fields)
            addVariables(context.variables)
            addImports(context.imports)
            addKeywords()

            // Handle contextual completions
            when (completionContext) {
                is CompletionContext.MemberAccess -> {
                    println("Adding GDK/Classpath methods for ${completionContext.qualifierType}")
                    addGdkMethods(completionContext.qualifierType, compilationService)
                    addClasspathMethods(completionContext.qualifierType, compilationService)
                }

                is CompletionContext.TypeParameter -> {
                    println("Adding type parameter classes for prefix '${completionContext.prefix}'")
                    addTypeParameterClasses(completionContext.prefix, compilationService)
                }

                null -> {
                    /* No special context */
                }
            }
        }
    }

    /**
     * Detect completion context (member access or type parameter).
     */
    private fun detectCompletionContext(
        nodeAtCursor: org.codehaus.groovy.ast.ASTNode?,
        astModel: com.github.albertocavalcante.groovyparser.ast.GroovyAstModel,
    ): CompletionContext? {
        println("=== COMPLETION CONTEXT DETECTION ===")
        println("Node at cursor: ${nodeAtCursor?.javaClass?.simpleName}")

        if (nodeAtCursor == null) {
            println("No node at cursor position")
            return null
        }

        // Log node details
        println("Node type: ${nodeAtCursor.javaClass.name}")
        println("Node text: ${nodeAtCursor.text}")
        println("Node line: ${nodeAtCursor.lineNumber}, column: ${nodeAtCursor.columnNumber}")

        // Check parent as well
        val parent = astModel.getParent(nodeAtCursor)
        println("Parent node: ${parent?.javaClass?.simpleName}")
        println("Parent text: ${parent?.text}")

        return when (nodeAtCursor) {
            // Case 1: Direct PropertyExpression (e.g., "myList.ea|")
            is org.codehaus.groovy.ast.expr.PropertyExpression -> {
                println("Found PropertyExpression!")
                println("  Object expression: ${nodeAtCursor.objectExpression.javaClass.simpleName}")
                println("  Object type: ${nodeAtCursor.objectExpression.type?.name}")
                println("  Property: ${nodeAtCursor.property}")

                val qualifierType = nodeAtCursor.objectExpression.type?.name
                println("  Resolved qualifier type: $qualifierType")
                qualifierType?.let { CompletionContext.MemberAccess(it) }
            }

            // Case 2: VariableExpression that's part of a PropertyExpression
            // (e.g., cursor is on "myList" in "myList.")
            is org.codehaus.groovy.ast.expr.VariableExpression -> {
                println("Found VariableExpression: ${nodeAtCursor.name}")
                println("  Variable type: ${nodeAtCursor.type?.name}")

                if (parent is org.codehaus.groovy.ast.expr.PropertyExpression) {
                    println("  Parent is PropertyExpression - member access detected!")
                    val qualifierType = nodeAtCursor.type?.name
                    println("  Resolved qualifier type: $qualifierType")
                    qualifierType?.let { CompletionContext.MemberAccess(it) }
                } else {
                    println("  Parent is NOT PropertyExpression")
                    null
                }
            }

            // Case 3: ConstantExpression that's the property in a PropertyExpression
            // (e.g., cursor lands on the dummy identifier "IntelliJIdeaRulezzz" in "myList.IntelliJIde aRulezzz")
            is org.codehaus.groovy.ast.expr.ConstantExpression -> {
                println("Found ConstantExpression: ${nodeAtCursor.text}")

                if (parent is org.codehaus.groovy.ast.expr.PropertyExpression) {
                    println("  Parent is PropertyExpression - extracting object type!")
                    println("  Object expression: ${parent.objectExpression.javaClass.simpleName}")
                    val qualifierType = parent.objectExpression.type?.name
                    println("  Resolved qualifier type: $qualifierType")
                    qualifierType?.let { CompletionContext.MemberAccess(it) }
                } else {
                    println("  Parent is NOT PropertyExpression")
                    null
                }
            }

            // Case 4: ClassExpression (e.g. "List<String>")
            is org.codehaus.groovy.ast.expr.ClassExpression -> {
                println("Found ClassExpression: ${nodeAtCursor.type.name}")
                val generics = nodeAtCursor.type.genericsTypes
                if (generics != null && generics.isNotEmpty()) {
                    println("  Has generics: ${generics.map { it.name }}")
                    // Check if any generic type matches our dummy identifier
                    val dummyGeneric = generics.find { it.name.contains("IntelliJIdeaRulezzz") }
                    if (dummyGeneric != null) {
                        println("  Found dummy generic: ${dummyGeneric.name}")
                        // Extract prefix (everything before the dummy identifier)
                        val prefix = dummyGeneric.name.substringBefore("IntelliJIdeaRulezzz")
                        println("  Extracted prefix: '$prefix'")
                        CompletionContext.TypeParameter(prefix)
                    } else {
                        null
                    }
                } else {
                    println("  No generics found")
                    null
                }
            }

            // Case 5: Method call expression (might be incomplete)
            is org.codehaus.groovy.ast.expr.MethodCallExpression -> {
                println("Found MethodCallExpression")
                println("  Object expression: ${nodeAtCursor.objectExpression?.javaClass?.simpleName}")
                println("  Method: ${nodeAtCursor.methodAsString}")
                null // For now, don't handle method calls
            }

            else -> {
                println("Unhandled node type for member access: ${nodeAtCursor.javaClass.simpleName}")
                null
            }
        }
    }

    private sealed interface CompletionContext {
        data class MemberAccess(val qualifierType: String) : CompletionContext
        data class TypeParameter(val prefix: String) : CompletionContext
    }

    private fun CompletionsBuilder.addClasses(classes: List<ClassSymbol>) {
        classes.forEach { classSymbol ->
            clazz(
                name = classSymbol.name,
                packageName = classSymbol.packageName,
                doc = "Class: ${classSymbol.name}",
            )
        }
    }

    private fun CompletionsBuilder.addMethods(methods: List<MethodSymbol>) {
        methods.forEach { methodSymbol ->
            val paramSignatures = methodSymbol.parameters.map { "${it.type} ${it.name}" }
            method(
                name = methodSymbol.name,
                returnType = methodSymbol.returnType,
                parameters = paramSignatures,
                doc = "Method: ${methodSymbol.name}",
            )
        }
    }

    private fun CompletionsBuilder.addFields(fields: List<FieldSymbol>) {
        fields.forEach { fieldSymbol ->
            field(
                name = fieldSymbol.name,
                type = fieldSymbol.type,
                doc = "Field: ${fieldSymbol.type} ${fieldSymbol.name}",
            )
        }
    }

    private fun CompletionsBuilder.addVariables(variables: List<VariableSymbol>) {
        variables.forEach { varSymbol ->
            val kind = varSymbol.kind.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
            val docString = "$kind: ${varSymbol.type} ${varSymbol.name}"
            variable(
                name = varSymbol.name,
                type = varSymbol.type,
                doc = docString,
            )
        }
    }

    private fun CompletionsBuilder.addImports(imports: List<ImportSymbol>) {
        imports.forEach { importSymbol ->
            if (!importSymbol.isStarImport) {
                val name = importSymbol.className
                    ?: importSymbol.packageName.substringAfterLast('.')

                clazz(
                    name = name,
                    packageName = importSymbol.packageName,
                    doc = "Imported: ${importSymbol.packageName}.$name",
                )
            }
        }
    }

    private fun CompletionsBuilder.addKeywords() {
        val keywords = listOf(
            // Types
            "def", "void", "int", "boolean", "char", "byte",
            "short", "long", "float", "double", "String", "Object",
            // Control flow
            "if", "else", "for", "while", "do", "switch", "case", "default",
            "break", "continue", "return", "try", "catch", "finally", "throw",
            // Structure
            "class", "interface", "trait", "enum", "package", "import",
            // Modifiers
            "public", "protected", "private", "static", "final", "abstract",
            "synchronized", "transient", "volatile", "native",
            // Values/Other
            "true", "false", "null", "this", "super", "new", "in", "as", "assert",
        )
        keywords.forEach { k ->
            keyword(
                keyword = k,
                doc = "Keyword/Type: $k",
            )
        }
    }

    /**
     * Add GDK (GroovyDevelopment Kit) method completions for a given type.
     */
    private fun CompletionsBuilder.addGdkMethods(className: String, compilationService: GroovyCompilationService) {
        val gdkMethods = compilationService.gdkProvider.getMethodsForType(className)
        logger.debug("Found {} GDK methods for type {}", gdkMethods.size, className)

        gdkMethods.forEach { gdkMethod ->
            method(
                name = gdkMethod.name,
                returnType = gdkMethod.returnType,
                parameters = gdkMethod.parameters,
                doc = gdkMethod.doc,
            )
        }
    }

    /**
     * Add JDK/classpath method completions for a given type.
     */
    private fun CompletionsBuilder.addClasspathMethods(
        className: String,
        compilationService: GroovyCompilationService,
    ) {
        val classpathMethods = compilationService.classpathService.getMethods(className)
        logger.debug("Found {} classpath methods for type {}", classpathMethods.size, className)

        classpathMethods.forEach { method ->
            // Only add public instance methods
            if (method.isPublic && !method.isStatic) {
                method(
                    name = method.name,
                    returnType = method.returnType,
                    parameters = method.parameters,
                    doc = method.doc,
                )
            }
        }
    }

    /**
     * Add class completions for type parameters (e.g., List<I...> → Integer).
     */
    private fun CompletionsBuilder.addTypeParameterClasses(
        prefix: String,
        compilationService: GroovyCompilationService,
    ) {
        val classes = compilationService.classpathService.findClassesByPrefix(prefix, maxResults = 20)
        logger.debug("Found {} classes for prefix {}", classes.size, prefix)

        classes.forEach { classInfo ->
            clazz(
                name = classInfo.simpleName,
                packageName = classInfo.packageName,
                doc = "Class: ${classInfo.fullName}",
            )
        }
    }
}
