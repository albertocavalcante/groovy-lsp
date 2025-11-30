package com.github.albertocavalcante.groovylsp.providers.completion

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * Integration test for GDK method completion.
 * Verifies that GDK methods are suggested when accessing properties on Groovy objects.
 */
class GdkCompletionIntegrationTest {

    private lateinit var compilationService: GroovyCompilationService

    @BeforeEach
    fun setup() {
        compilationService = GroovyCompilationService()
        // Initialize GDK provider
        compilationService.gdkProvider.initialize()
    }

    @Test
    fun `should provide GDK method completions for List`() {
        runBlocking {
            val groovyCode = """
                def myList = [1, 2, 3]
                myList.
            """.trimIndent()

            val uri = URI.create("file:///test/Test.groovy")

            // Calculate cursor position (line 1, character 7 means after the dot on second line)
            val line = 1
            val character = 7

            // Insert dummy identifier at cursor position to make code parseable
            // This is what IntelliJ and Kotlin LSP do
            val lines = groovyCode.lines().toMutableList()
            val targetLine = lines[line]
            val modifiedLine =
                targetLine.substring(0, character) + "IntelliJIdeaRulezzz" + targetLine.substring(character)
            lines[line] = modifiedLine
            val codeWithDummy = lines.joinToString("\n")

            println("Original code:\n$groovyCode")
            println("\nCode with dummy:\n$codeWithDummy")

            // Compile the modified code
            val result = compilationService.compile(uri, codeWithDummy)
            println("Compilation result: isSuccess=${result.isSuccess}, diagnostics=${result.diagnostics.size}")
            result.diagnostics.forEach { println("  Diagnostic: ${it.message}") }

            // Check if we have an AST
            val ast = compilationService.getAst(uri)
            val astModel = compilationService.getAstModel(uri)
            println("After compile - AST: ${ast != null}, ASTModel: ${astModel != null}")

            // Request completions at the original cursor position (before dummy was inserted)
            val completions = CompletionProvider.getContextualCompletions(
                uri = uri.toString(),
                line = line,
                character = character,
                compilationService = compilationService,
            )

            // Should include GDK methods for List
            val completionLabels = completions.map { it.label }
            println("Found ${completions.size} completions: $completionLabels")

            // Check for common GDK methods
            assertThat(completionLabels).contains("each", "find", "collect")
        }
    }

    @Test
    fun `should provide GDK method completions for String`() {
        runBlocking {
            val groovyCode = """
                def myString = "hello"
                myString.
            """.trimIndent()

            val uri = URI.create("file:///test/Test.groovy")
            val line = 1
            val character = 9

            // Insert dummy identifier
            val lines = groovyCode.lines().toMutableList()
            lines[line] = lines[line].substring(0, character) + "IntelliJIdeaRulezzz"
            val codeWithDummy = lines.joinToString("\n")

            // Compile the modified code
            compilationService.compile(uri, codeWithDummy)

            // Request completions
            val completions = CompletionProvider.getContextualCompletions(
                uri = uri.toString(),
                line = line,
                character = character,
                compilationService = compilationService,
            )

            // Should include GDK methods for String
            val completionLabels = completions.map { it.label }
            println("Found ${completions.size} completions: $completionLabels")

            // Check for common GDK methods (these work on Object, so will work on String too)
            assertThat(completionLabels).contains("each", "find")
        }
    }

    @Test
    fun `should provide JDK method completions for String`() {
        runBlocking {
            val groovyCode = """
                def myString = "hello"
                myString.
            """.trimIndent()

            val uri = URI.create("file:///test/Test.groovy")
            val line = 1
            val character = 9

            // Insert dummy identifier
            val lines = groovyCode.lines().toMutableList()
            lines[line] = lines[line].substring(0, character) + "IntelliJIdeaRulezzz"
            val codeWithDummy = lines.joinToString("\n")

            // Compile the modified code
            compilationService.compile(uri, codeWithDummy)

            // Request completions
            val completions = CompletionProvider.getContextualCompletions(
                uri = uri.toString(),
                line = line,
                character = character,
                compilationService = compilationService,
            )

            // Should include JDK methods for String (or at least Object methods)
            val completionLabels = completions.map { it.label }
            println("Found ${completions.size} completions: $completionLabels")

            // Check for common JDK methods (these exist on Object, so will be there)
            assertThat(completionLabels).contains("toString", "equals", "hashCode")
        }
    }

    @Test
    fun `should provide type completions for generics`() {
        runBlocking {
            val groovyCode = """
                List<Str
            """.trimIndent()

            val uri = URI.create("file:///test/Test.groovy")
            val line = 0
            val character = 8

            // Insert dummy identifier
            val lines = groovyCode.lines().toMutableList()
            lines[line] = lines[line].substring(0, character) + "IntelliJIdeaRulezzz" + lines[line].substring(character)
            // Close the angle bracket to make it valid syntax? Or maybe the dummy is enough?
            // Let's try adding a closing > just in case, though the user might not have typed it yet.
            // Actually, "List<IntelliJIdeaRulezzz" might be a valid variable declaration start?
            // Let's try "List<IntelliJIdeaRulezzz>" to be safe for parsing as a type.
            lines[line] += ">"

            val codeWithDummy = lines.joinToString("\n")
            println("Code with dummy: $codeWithDummy")

            // Compile the modified code
            val result = compilationService.compile(uri, codeWithDummy)
            println("Compilation result: isSuccess=${result.isSuccess}, diagnostics=${result.diagnostics.size}")
            result.diagnostics.forEach { println("  Diagnostic: ${it.message}") }

            // Request completions
            val completions = CompletionProvider.getContextualCompletions(
                uri = uri.toString(),
                line = line,
                character = character,
                compilationService = compilationService,
            )

            // We expect to find "String"
            val completionLabels = completions.map { it.label }
            println("Found ${completions.size} completions: $completionLabels")

            assertThat(completionLabels).contains("String")
        }
    }
}
