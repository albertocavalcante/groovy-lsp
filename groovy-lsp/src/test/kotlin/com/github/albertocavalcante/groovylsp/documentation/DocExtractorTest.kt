package com.github.albertocavalcante.groovylsp.documentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocExtractorTest {

    @Test
    fun `extracts simple summary from groovydoc`() {
        val source = """
            package com.example
            
            /**
             * This is a simple class.
             */
            class SimpleClass {
            }
        """.trimIndent()

        val doc = DocExtractor.extractDocumentation(source, 6) // Line with "class SimpleClass"

        assertEquals("This is a simple class.", doc.summary)
        assertTrue(doc.isNotEmpty())
    }

    @Test
    fun `extracts method documentation with params and return`() {
        val source = """
            /**
             * Calculates the sum of two numbers.
             * This method adds a and b together.
             *
             * @param a the first number
             * @param b the second number
             * @return the sum of a and b
             */
            def add(int a, int b) {
                return a + b
            }
        """.trimIndent()

        val doc = DocExtractor.extractDocumentation(source, 9) // Line with "def add"

        assertEquals("Calculates the sum of two numbers", doc.summary)
        assertTrue(doc.description.contains("This method adds a and b together"))
        assertEquals(2, doc.params.size)
        assertEquals("the first number", doc.params["a"])
        assertEquals("the second number", doc.params["b"])
        assertEquals("the sum of a and b", doc.returnDoc)
    }

    @Test
    fun `extracts documentation with throws and deprecated`() {
        val source = """
            /**
             * Old method that throws exceptions.
             *
             * @param input the input string
             * @throws IllegalArgumentException if input is null
             * @throws IOException if I/O error occurs
             * @deprecated Use newMethod instead
             */
            void oldMethod(String input) {
            }
        """.trimIndent()

        val doc = DocExtractor.extractDocumentation(source, 9) // Line with "void oldMethod"

        assertEquals("Old method that throws exceptions", doc.summary)
        assertEquals(2, doc.throws.size)
        assertTrue(doc.throws.containsKey("IllegalArgumentException"))
        assertTrue(doc.throws.containsKey("IOException"))
        assertTrue(doc.deprecated.contains("Use newMethod instead"))
    }

    @Test
    fun `returns empty documentation when no doc comment exists`() {
        val source = """
            package com.example
            
            class NoDocClass {
                def method() {
                    return 42
                }
            }
        """.trimIndent()

        val doc = DocExtractor.extractDocumentation(source, 3) // Line with "class NoDocClass"

        assertTrue(doc.isEmpty())
        assertEquals("", doc.summary)
    }

    @Test
    fun `handles documentation with annotations before method`() {
        val source = """
            /**
             * Annotated method with documentation.
             *
             * @param name the name parameter
             * @return a greeting string
             */
            @Override
            @Deprecated
            String greet(String name) {
                return "Hello"
            }
        """.trimIndent()

        val doc = DocExtractor.extractDocumentation(source, 9) // Line with "String greet"

        assertEquals("Annotated method with documentation", doc.summary)
        assertEquals("the name parameter", doc.params["name"])
        assertEquals("a greeting string", doc.returnDoc)
    }

    @Test
    fun `extracts documentation from line number outside bounds returns empty`() {
        val source = """
            /**
             * Test class.
             */
            class Test {
            }
        """.trimIndent()

        val docBefore = DocExtractor.extractDocumentation(source, 0) // Invalid line
        val docAfter = DocExtractor.extractDocumentation(source, 100) // Beyond end

        assertTrue(docBefore.isEmpty())
        assertTrue(docAfter.isEmpty())
    }
}

@Test
fun `debug sublist extraction`() {
    val lines = listOf("line1", "line2", "/**", " * Doc", " */", "class Foo")
    // Line 6 is "class Foo" (1-based)
    // Line 5 is " */" (1-based)
    // Line 4 is " * Doc" (1-based)
    // Line 3 is "/**" (1-based)

    // subList(2, 5) should give indices 2, 3, 4 = "/**", " * Doc", " */"
    val result = lines.subList(2, 5)
    println("Result: $result")
    assertEquals(3, result.size)
    assertTrue(result[0].contains("/**"))
    assertTrue(result[2].contains("*/"))
}
