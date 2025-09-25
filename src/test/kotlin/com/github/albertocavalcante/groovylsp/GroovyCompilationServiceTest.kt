package com.github.albertocavalcante.groovylsp

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.DiagnosticSeverity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class GroovyCompilationServiceTest {

    private lateinit var compilationService: GroovyCompilationService

    @BeforeEach
    fun setup() {
        compilationService = GroovyCompilationService()
    }

    @Test
    fun `test compile valid groovy file returns success with no errors`() = runBlocking {
        val simpleGroovyContent = """
            package test

            class TestClass {
                String name

                void greet() {
                    println "Hello, " + name
                }
            }
        """.trimIndent()

        val uri = URI.create("file:///test/TestClass.groovy")
        val result = compilationService.compile(uri, simpleGroovyContent)

        assertTrue(result.isSuccess)
        assertTrue(result.diagnostics.isEmpty())
        assertNotNull(result.ast)
    }

    @Test
    fun `test compile invalid groovy file returns errors`() = runBlocking {
        val invalidGroovyContent = """
            package test

            class TestClass {
                // Missing opening brace
                void badMethod( {
                    println "This has syntax errors"
                }
            }
        """.trimIndent()

        val uri = URI.create("file:///test/BadClass.groovy")
        val result = compilationService.compile(uri, invalidGroovyContent)

        assertFalse(result.isSuccess)
        assertTrue(result.diagnostics.isNotEmpty())

        // Should have at least one syntax error diagnostic
        val syntaxErrors = result.diagnostics.filter {
            it.severity == DiagnosticSeverity.Error
        }
        assertTrue(syntaxErrors.isNotEmpty())
    }

    @Test
    fun `test compile caches AST for repeated requests`() = runBlocking {
        val groovyContent = """
            class SimpleClass {
                String name = "test"
            }
        """.trimIndent()

        val uri = URI.create("file:///test/SimpleClass.groovy")

        // First compilation
        val result1 = compilationService.compile(uri, groovyContent)
        val ast1 = compilationService.getAst(uri)

        // Second request for same content
        val result2 = compilationService.compile(uri, groovyContent)
        val ast2 = compilationService.getAst(uri)

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        assertNotNull(ast1)
        assertNotNull(ast2)
        // AST should be cached (same instance)
        assertSame(ast1, ast2)
    }

    @Test
    fun `test compile updates cache when content changes`() = runBlocking {
        val originalContent = """
            class OriginalClass {
                String originalField
            }
        """.trimIndent()

        val updatedContent = """
            class UpdatedClass {
                String updatedField
            }
        """.trimIndent()

        val uri = URI.create("file:///test/ChangingClass.groovy")

        // Compile original
        compilationService.compile(uri, originalContent)
        val originalAst = compilationService.getAst(uri)

        // Compile updated content
        compilationService.compile(uri, updatedContent)
        val updatedAst = compilationService.getAst(uri)

        assertNotNull(originalAst)
        assertNotNull(updatedAst)
        // Should be different AST instances after content change
        assertNotSame(originalAst, updatedAst)
    }

    @Test
    fun `test getDiagnostics returns empty for valid file`() = runBlocking {
        val validContent = """
            class ValidClass {
                void validMethod() {
                    println "Valid code"
                }
            }
        """.trimIndent()

        val uri = URI.create("file:///test/ValidClass.groovy")
        compilationService.compile(uri, validContent)

        val diagnostics = compilationService.getDiagnostics(uri)
        assertTrue(diagnostics.isEmpty())
    }

    @Test
    fun `test getDiagnostics returns errors for invalid file`() = runBlocking {
        val invalidContent = """
            class InvalidClass {
                // Invalid syntax - missing closing parenthesis
                void invalidMethod(String param {
                    println param
                }
            }
        """.trimIndent()

        val uri = URI.create("file:///test/InvalidClass.groovy")
        compilationService.compile(uri, invalidContent)

        val diagnostics = compilationService.getDiagnostics(uri)
        assertTrue(diagnostics.isNotEmpty())

        // Should contain error-level diagnostics
        val errors = diagnostics.filter { it.severity == DiagnosticSeverity.Error }
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `test compile with real test resource files`() = runBlocking {
        // Load the Simple.groovy test resource
        val simpleGroovyContent = this::class.java.classLoader
            .getResource("Simple.groovy")
            ?.readText()
            ?: fail("Could not load Simple.groovy test resource")

        val uri = URI.create("file:///test/Simple.groovy")
        val result = compilationService.compile(uri, simpleGroovyContent)

        assertTrue(result.isSuccess, "Simple.groovy should compile without errors")
        assertTrue(result.diagnostics.isEmpty(), "Simple.groovy should have no diagnostics")
        assertNotNull(result.ast, "Simple.groovy should produce an AST")
    }

    @Test
    fun `test compile with syntax error test resource files`() = runBlocking {
        // Load the SyntaxError.groovy test resource
        val syntaxErrorContent = this::class.java.classLoader
            .getResource("SyntaxError.groovy")
            ?.readText()
            ?: fail("Could not load SyntaxError.groovy test resource")

        val uri = URI.create("file:///test/SyntaxError.groovy")
        val result = compilationService.compile(uri, syntaxErrorContent)

        assertFalse(result.isSuccess, "SyntaxError.groovy should fail to compile")
        assertTrue(result.diagnostics.isNotEmpty(), "SyntaxError.groovy should have diagnostics")

        // Should contain at least one syntax error (compiler stops at first fatal error)
        val errors = result.diagnostics.filter { it.severity == DiagnosticSeverity.Error }
        assertTrue(errors.isNotEmpty(), "SyntaxError.groovy should have at least one syntax error")
    }

    @Test
    fun `test getAst returns null for uncompiled file`() {
        val uri = URI.create("file:///test/NeverCompiled.groovy")
        val ast = compilationService.getAst(uri)
        assertNull(ast, "AST should be null for files that were never compiled")
    }

    @Test
    fun `test diagnostics contain proper position information`() = runBlocking {
        val contentWithError = """
            class TestClass {
                void method( {
                    println "error on line 3"
                }
            }
        """.trimIndent()

        val uri = URI.create("file:///test/PositionTest.groovy")
        val result = compilationService.compile(uri, contentWithError)

        assertTrue(result.diagnostics.isNotEmpty())

        val diagnostic = result.diagnostics.first()
        assertNotNull(diagnostic.range)
        assertNotNull(diagnostic.range.start)
        assertNotNull(diagnostic.range.end)

        // The syntax error should be on line 1 (0-indexed), where the method declaration is
        assertEquals(1, diagnostic.range.start.line)
    }
}
