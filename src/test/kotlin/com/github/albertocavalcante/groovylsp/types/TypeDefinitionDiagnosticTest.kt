package com.github.albertocavalcante.groovylsp.types

import com.github.albertocavalcante.groovylsp.ast.AstVisitor
import com.github.albertocavalcante.groovylsp.compilation.CompilationContext
import groovy.lang.GroovyClassLoader
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.io.StringReaderSource
import org.eclipse.lsp4j.Position
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * Diagnostic test to understand what's happening with type resolution.
 */
class TypeDefinitionDiagnosticTest {

    @Test
    fun `debug type resolution process`() {
        val code = """
            class Person {
                String name
            }
            def person = new Person()
            person.name = "test"
        """.trimIndent()

        println("=== DEBUGGING TYPE RESOLUTION ===")
        println("Input code:")
        println(code)
        println()

        // 1. Compile the code
        val context = compileGroovy(code)
        println("✓ Compilation completed")
        println("ModuleNode: ${context.moduleNode}")
        println("AstVisitor: ${context.astVisitor}")
        println()

        // 2. Try to find all nodes
        val allNodes = context.astVisitor.getAllNodes()
        println("Found ${allNodes.size} AST nodes:")
        allNodes.forEachIndexed { index, node ->
            println("  $index: ${node.javaClass.simpleName} - $node")
        }
        println()

        // 3. Try position-based lookup
        val testPosition = Position(4, 10) // Should be around "person.name"
        println("Looking for node at position $testPosition...")
        val nodeAtPosition = context.astVisitor.getNodeAt(context.uri, testPosition)
        println("Node at position: $nodeAtPosition")
        println("Node type: ${nodeAtPosition?.javaClass?.simpleName}")
        println()

        // 4. Try type resolution (simplified - just check if we can create a TypeResolver)
        if (nodeAtPosition != null) {
            val typeResolver = GroovyTypeResolver()
            println("✓ TypeResolver created successfully")
            println("Node available for resolution: ${nodeAtPosition.javaClass.simpleName}")
        } else {
            println("❌ No node found at position - cannot test type resolution")
        }

        println("=== END DEBUG ===")
    }

    private fun compileGroovy(code: String): CompilationContext {
        val config = CompilerConfiguration()
        val classLoader = GroovyClassLoader()
        val compilationUnit = CompilationUnit(config, null, classLoader)

        val source = StringReaderSource(code, config)
        val sourceUnit = SourceUnit("test.groovy", source, config, classLoader, compilationUnit.errorCollector)
        compilationUnit.addSource(sourceUnit)

        val astVisitor = AstVisitor()
        val uri = URI.create("file:///test.groovy")

        try {
            // Compile to get AST
            compilationUnit.compile(Phases.CANONICALIZATION)

            // Get the module and visit with our AST visitor
            val module = sourceUnit.ast
            astVisitor.visitModule(module, sourceUnit, uri)

            return CompilationContext(
                uri = uri,
                moduleNode = module,
                compilationUnit = compilationUnit,
                astVisitor = astVisitor,
                workspaceRoot = null,
            )
        } catch (e: Exception) {
            println("Compilation error: ${e.message}")
            // Even with compilation errors, we might have partial AST
            val module = sourceUnit.ast ?: ModuleNode(sourceUnit)
            astVisitor.visitModule(module, sourceUnit, uri)

            return CompilationContext(
                uri = uri,
                moduleNode = module,
                compilationUnit = compilationUnit,
                astVisitor = astVisitor,
                workspaceRoot = null,
            )
        }
    }
}
