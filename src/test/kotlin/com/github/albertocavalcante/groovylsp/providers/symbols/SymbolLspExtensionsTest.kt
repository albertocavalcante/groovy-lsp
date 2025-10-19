package com.github.albertocavalcante.groovylsp.providers.symbols

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.eclipse.lsp4j.SymbolKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.net.URI

class SymbolLspExtensionsTest {

    @Test
    fun `class symbol converts to document and symbol information`() {
        val classNode = ClassNode("Greeter", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE).apply {
            setLineNumber(1)
            setColumnNumber(1)
            setLastLineNumber(1)
            setLastColumnNumber(10)
        }
        val uri = URI.create("file:///test.groovy")

        val symbol = Symbol.Class.from(classNode, uri)

        val documentSymbol = symbol.toDocumentSymbol()
        assertNotNull(documentSymbol)
        require(documentSymbol != null)
        assertEquals(SymbolKind.Class, documentSymbol.kind)
        assertEquals("Greeter", documentSymbol.name)
        assertEquals("Greeter", documentSymbol.detail)

        val symbolInformation = symbol.toSymbolInformation()
        assertNotNull(symbolInformation)
        require(symbolInformation != null)
        assertEquals(SymbolKind.Class, symbolInformation.kind)
        assertEquals(uri.toString(), symbolInformation.location.uri)
        assertEquals(0, symbolInformation.location.range.start.line)
    }

    @Test
    fun `method symbol includes signature in detail`() {
        val classNode = ClassNode("Greeter", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        val methodNode = MethodNode(
            "greet",
            Modifier.PUBLIC,
            ClassHelper.STRING_TYPE,
            arrayOf(Parameter(ClassHelper.STRING_TYPE, "name")),
            ClassNode.EMPTY_ARRAY,
            null,
        ).apply {
            declaringClass = classNode
            setLineNumber(2)
            setColumnNumber(1)
            setLastLineNumber(2)
            setLastColumnNumber(20)
        }
        val uri = URI.create("file:///test.groovy")

        val methodSymbol = Symbol.Method.from(methodNode, uri)

        val documentSymbol = methodSymbol.toDocumentSymbol()
        assertNotNull(documentSymbol)
        require(documentSymbol != null)
        assertEquals(SymbolKind.Method, documentSymbol.kind)
        assertEquals("greet", documentSymbol.name)
        assertEquals("public String greet(String name)", documentSymbol.detail)

        val symbolInformation = methodSymbol.toSymbolInformation()
        assertNotNull(symbolInformation)
        require(symbolInformation != null)
        assertEquals(SymbolKind.Method, symbolInformation.kind)
        assertEquals(uri.toString(), symbolInformation.location.uri)
        assertEquals("public String greet(String name)", methodSymbol.signature)
    }
}
