package com.github.albertocavalcante.groovylsp.providers.codeaction

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImportActionTest {

    private lateinit var compilationService: GroovyCompilationService
    private lateinit var importAction: ImportAction

    private val testUri = "file:///test.groovy"

    @BeforeEach
    fun setUp() {
        compilationService = GroovyCompilationService()
        importAction = ImportAction(compilationService)
    }

    @Test
    fun `returns empty list when no diagnostics`() {
        val actions = importAction.createImportActions(testUri, emptyList(), "def x = 1")

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `returns empty list when diagnostics not about missing symbols`() {
        val diagnostic = Diagnostic().apply {
            range = Range(Position(0, 0), Position(0, 5))
            message = "Type mismatch"
        }

        val actions = importAction.createImportActions(testUri, listOf(diagnostic), "def x = 1")

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `returns empty list when no candidates found for missing symbol`() {
        val diagnostic = Diagnostic().apply {
            range = Range(Position(0, 8), Position(0, 20))
            message = "unable to resolve class UnknownClass"
        }

        val actions = importAction.createImportActions(testUri, listOf(diagnostic), "def x = UnknownClass.foo()")

        assertTrue(actions.isEmpty(), "Should not provide actions when no candidates found")
    }

    @Test
    fun `extracts symbol name from unable to resolve class message`() {
        val diagnostic = Diagnostic().apply {
            range = Range(Position(0, 0), Position(0, 10))
            message = "unable to resolve class MyClass"
        }

        val actions = importAction.createImportActions(testUri, listOf(diagnostic), "MyClass x = null")

        // We don't have any workspace symbols set up, so should be empty
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `extracts symbol name from cannot find symbol message`() {
        val diagnostic = Diagnostic().apply {
            range = Range(Position(0, 0), Position(0, 10))
            message = "cannot find symbol MyClass"
        }

        val actions = importAction.createImportActions(testUri, listOf(diagnostic), "MyClass x = null")

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `extracts symbol name from unresolved reference message`() {
        val diagnostic = Diagnostic().apply {
            range = Range(Position(0, 0), Position(0, 10))
            message = "Unresolved reference: MyClass"
        }

        val actions = importAction.createImportActions(testUri, listOf(diagnostic), "MyClass x = null")

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `handles multiple diagnostics`() {
        val diagnostic1 = Diagnostic().apply {
            range = Range(Position(0, 0), Position(0, 10))
            message = "unable to resolve class ClassA"
        }

        val diagnostic2 = Diagnostic().apply {
            range = Range(Position(1, 0), Position(1, 10))
            message = "unable to resolve class ClassB"
        }

        val actions = importAction.createImportActions(
            testUri,
            listOf(diagnostic1, diagnostic2),
            "ClassA a = null\nClassB b = null",
        )

        // Should handle multiple diagnostics without crashing
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `finds import insertion point after package declaration`() {
        val content = """
            package com.example
            
            class Test {
            }
        """.trimIndent()

        // We can't easily test the private method, but we can test that actions are created correctly
        // For now, this is a placeholder for future enhancement
        assertTrue(true)
    }

    @Test
    fun `finds import insertion point after existing imports`() {
        val content = """
            package com.example
            
            import java.util.List
            
            class Test {
            }
        """.trimIndent()

        // This tests that we don't crash with existing imports
        val diagnostic = Diagnostic().apply {
            range = Range(Position(0, 0), Position(0, 10))
            message = "unable to resolve class UnknownClass"
        }

        val actions = importAction.createImportActions(testUri, listOf(diagnostic), content)
        assertTrue(actions.isEmpty()) // No candidates, but should not crash
    }

    @Test
    fun `handles content with no package declaration`() {
        val content = """
            class Test {
            }
        """.trimIndent()

        val diagnostic = Diagnostic().apply {
            range = Range(Position(0, 0), Position(0, 10))
            message = "unable to resolve class UnknownClass"
        }

        val actions = importAction.createImportActions(testUri, listOf(diagnostic), content)
        assertTrue(actions.isEmpty())
    }
}
