package com.github.albertocavalcante.groovyparser.tokens

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroovyTokenIndexTest {

    @Test
    fun `should detect line comments`() {
        val source = """
            def x = 1 // this is a comment
        """.trimIndent()
        val index = GroovyTokenIndex.build(source)

        val commentStart = source.indexOf("//")
        assertTrue(index.isInComment(commentStart), "At //")
        assertTrue(index.isInComment(commentStart + 5), "Middle of comment")
        assertFalse(index.isInComment(0), "Before comment")
    }

    @Test
    fun `should detect block comments`() {
        val source = """
            /* 
               multiline
               comment
            */
            def x = 1
        """.trimIndent()
        val index = GroovyTokenIndex.build(source)

        val commentStart = source.indexOf("/*")
        assertTrue(index.isInComment(commentStart), "At /*")
        assertTrue(index.isInComment(commentStart + 10), "Inside block comment")
        assertFalse(index.isInComment(source.indexOf("def")), "After block comment")
    }

    @Test
    fun `should detect single quoted strings`() {
        val source = "def s = 'hello world'"
        val index = GroovyTokenIndex.build(source)

        val stringStart = source.indexOf("'")
        assertTrue(index.isInString(stringStart), "At '")
        assertTrue(index.isInString(stringStart + 5), "Middle of string")
        assertFalse(index.isInString(0), "Before string")
    }

    @Test
    fun `should detect double quoted strings`() {
        val source = "def s = \"hello world\""
        val index = GroovyTokenIndex.build(source)

        val stringStart = source.indexOf("\"")
        assertTrue(index.isInString(stringStart), "At \"")
        assertTrue(index.isInString(stringStart + 5), "Middle of string")
    }

    @Test
    fun `should detect triple quoted strings`() {
        val source = "def s = '''hello\nworld'''"
        val index = GroovyTokenIndex.build(source)

        val stringStart = source.indexOf("'''")
        assertTrue(index.isInString(stringStart), "At '''")
        assertTrue(index.isInString(stringStart + 10), "Inside multiline string")
    }
}
