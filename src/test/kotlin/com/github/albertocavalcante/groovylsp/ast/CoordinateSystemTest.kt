package com.github.albertocavalcante.groovylsp.ast

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import kotlinx.coroutines.test.runTest
import org.codehaus.groovy.ast.ModuleNode
import org.eclipse.lsp4j.Position
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the CoordinateSystem singleton.
 * These tests verify coordinate conversion and position containment logic.
 */
class CoordinateSystemTest {

    private lateinit var compilationService: GroovyCompilationService

    @BeforeEach
    fun setup() {
        compilationService = GroovyCompilationService()
    }

    @Test
    fun `lspToGroovy converts coordinates correctly`() {
        // LSP coordinates are 0-based, Groovy are 1-based
        val groovyPos = CoordinateSystem.lspToGroovy(0, 0)
        assertEquals(1, groovyPos.line)
        assertEquals(1, groovyPos.column)

        val groovyPos2 = CoordinateSystem.lspToGroovy(5, 10)
        assertEquals(6, groovyPos2.line)
        assertEquals(11, groovyPos2.column)
    }

    @Test
    fun `groovyToLsp converts coordinates correctly`() {
        // Groovy coordinates are 1-based, LSP are 0-based
        val lspPos = CoordinateSystem.groovyToLsp(1, 1)
        assertEquals(0, lspPos.line)
        assertEquals(0, lspPos.character)

        val lspPos2 = CoordinateSystem.groovyToLsp(6, 11)
        assertEquals(5, lspPos2.line)
        assertEquals(10, lspPos2.character)
    }

    @Test
    fun `LspPosition converts to Groovy correctly`() {
        val lspPos = CoordinateSystem.LspPosition(3, 7)
        val groovyPos = lspPos.toGroovy()
        assertEquals(4, groovyPos.line)
        assertEquals(8, groovyPos.column)
    }

    @Test
    fun `GroovyPosition converts to LSP correctly`() {
        val groovyPos = CoordinateSystem.GroovyPosition(4, 8)
        val lspPos = groovyPos.toLsp()
        assertEquals(3, lspPos.line)
        assertEquals(7, lspPos.character)
    }

    @Test
    fun `Position object conversion works`() {
        val lsp4jPos = Position(2, 5)
        val groovyPos = CoordinateSystem.lspToGroovy(lsp4jPos)
        assertEquals(3, groovyPos.line)
        assertEquals(6, groovyPos.column)
    }

    @Test
    fun `isValidNodePosition works with valid nodes`() = runTest {
        val groovyCode = "class TestClass {}"
        val uri = URI.create("file:///test.groovy")
        val ast = compilationService.compile(uri, groovyCode).ast as ModuleNode
        val classNode = ast.classes.first()

        // Valid node should have valid positions
        assertTrue(CoordinateSystem.isValidNodePosition(classNode))
    }

    @Test
    fun `getNodeLspRange returns correct range for valid node`() = runTest {
        val groovyCode = """
            class TestClass {
                def method() {}
            }
        """.trimIndent()

        val uri = URI.create("file:///test.groovy")
        val ast = compilationService.compile(uri, groovyCode).ast as ModuleNode
        val classNode = ast.classes.first()

        val range = CoordinateSystem.getNodeLspRange(classNode)
        assertNotNull(range)

        // Verify the range contains the class definition
        assertTrue(range.start.line >= 0)
        assertTrue(range.start.character >= 0)
        assertTrue(range.end.line >= range.start.line)
    }

    @Test
    fun `getNodeLspRange works with valid nodes`() = runTest {
        val groovyCode = "class TestClass {}"
        val uri = URI.create("file:///test.groovy")
        val ast = compilationService.compile(uri, groovyCode).ast as ModuleNode
        val classNode = ast.classes.first()

        val range = CoordinateSystem.getNodeLspRange(classNode)
        assertNotNull(range)
    }

    @Test
    fun `nodeContainsPosition works with LSP coordinates`() = runTest {
        val groovyCode = """
            class TestClass {
                def method() {
                    println "hello"
                }
            }
        """.trimIndent()

        val uri = URI.create("file:///test.groovy")
        val ast = compilationService.compile(uri, groovyCode).ast as ModuleNode
        val classNode = ast.classes.first()

        // Test with LSP coordinates (0-based)
        assertTrue(CoordinateSystem.nodeContainsPosition(classNode, 0, 5))
        assertTrue(CoordinateSystem.nodeContainsPosition(classNode, 1, 4))

        // Test with Position object
        val lspPosition = Position(0, 5)
        assertTrue(CoordinateSystem.nodeContainsPosition(classNode, lspPosition))
    }

    @Test
    fun `nodeContainsPosition correctly handles single-line nodes`() = runTest {
        val groovyCode = "def x = 42"
        val uri = URI.create("file:///test.groovy")
        val ast = compilationService.compile(uri, groovyCode).ast as ModuleNode

        // Find a field or variable declaration
        val scriptClass = ast.scriptClassDummy
        if (scriptClass.fields.isNotEmpty()) {
            val fieldNode = scriptClass.fields.first()

            // Test position within the field declaration
            val range = CoordinateSystem.getNodeLspRange(fieldNode)
            if (range != null) {
                // Test position within range
                assertTrue(CoordinateSystem.nodeContainsPosition(fieldNode, range.start.line, range.start.character))

                // Test position outside range
                assertFalse(CoordinateSystem.nodeContainsPosition(fieldNode, range.end.line + 1, 0))
            }
        }
    }

    @Test
    fun `nodeContainsPosition correctly handles multi-line nodes`() = runTest {
        val groovyCode = """
            class MultilineClass {
                def method() {
                    return "test"
                }
            }
        """.trimIndent()

        val uri = URI.create("file:///test.groovy")
        val ast = compilationService.compile(uri, groovyCode).ast as ModuleNode
        val classNode = ast.classes.first()

        // Test position on first line
        assertTrue(CoordinateSystem.nodeContainsPosition(classNode, 0, 6)) // Within "class"

        // Test position on middle line
        assertTrue(CoordinateSystem.nodeContainsPosition(classNode, 1, 8)) // Within method

        // Test position on last line (if we can determine it)
        val range = CoordinateSystem.getNodeLspRange(classNode)
        if (range != null) {
            assertTrue(CoordinateSystem.nodeContainsPosition(classNode, range.end.line, range.end.character))
        }
    }

    @Test
    fun `getNodePositionDebugString provides useful debug info`() = runTest {
        val groovyCode = "class TestClass {}"
        val uri = URI.create("file:///test.groovy")
        val ast = compilationService.compile(uri, groovyCode).ast as ModuleNode
        val classNode = ast.classes.first()

        val debugString = CoordinateSystem.getNodePositionDebugString(classNode)
        assertNotNull(debugString)
        assertTrue(debugString.contains("Groovy"))
        assertTrue(debugString.contains("LSP"))
    }

    @Test
    fun `type-safe position wrappers work correctly`() {
        val lspPos = CoordinateSystem.LspPosition(5, 10)
        val groovyPos = lspPos.toGroovy()
        val convertedBack = groovyPos.toLsp()

        assertEquals(lspPos.line, convertedBack.line)
        assertEquals(lspPos.character, convertedBack.character)
    }

    @Test
    fun `range conversions work correctly`() {
        val lspStart = CoordinateSystem.LspPosition(1, 2)
        val lspEnd = CoordinateSystem.LspPosition(3, 4)
        val lspRange = CoordinateSystem.LspRange(lspStart, lspEnd)

        val groovyStart = CoordinateSystem.GroovyPosition(2, 3)
        val groovyEnd = CoordinateSystem.GroovyPosition(4, 5)
        val groovyRange = CoordinateSystem.GroovyRange(groovyStart, groovyEnd)

        val convertedLspRange = groovyRange.toLsp()
        assertEquals(lspRange.start.line, convertedLspRange.start.line)
        assertEquals(lspRange.start.character, convertedLspRange.start.character)
        assertEquals(lspRange.end.line, convertedLspRange.end.line)
        assertEquals(lspRange.end.character, convertedLspRange.end.character)
    }
}
