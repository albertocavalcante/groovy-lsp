package com.github.albertocavalcante.groovylsp

import kotlinx.coroutines.test.runTest
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests using the non-blocking test server.
 */
class NonBlockingServerTest {

    private var serverHandle: TestLanguageServerHandle? = null

    @AfterEach
    fun cleanup() {
        serverHandle?.stop()
    }

    @Test
    fun `test server lifecycle with non-blocking harness`() = runTest {
        val runner = TestLanguageServerRunner()
        serverHandle = runner.startInMemoryServer()

        val server = serverHandle!!.server
        val client = serverHandle!!.client

        // Test initialization
        val initParams = InitializeParams().apply {
            workspaceFolders = listOf(WorkspaceFolder("file:///tmp/test", "test"))
            initializationOptions = mapOf("groovy.codenarc.enabled" to false)
        }

        val initResult = server.initialize(initParams).get()
        assertNotNull(initResult)
        assertNotNull(initResult.capabilities)

        // Test document operations
        val textDoc = TextDocumentItem().apply {
            uri = "file:///test.groovy"
            languageId = "groovy"
            version = 1
            text = """
                class TestClass {
                    void method() {
                        println "Hello from test"
                    }
                }
            """.trimIndent()
        }

        val didOpenParams = DidOpenTextDocumentParams().apply {
            textDocument = textDoc
        }

        server.textDocumentService.didOpen(didOpenParams)

        // Wait for compilation to complete
        client.awaitSuccessfulCompilation("file:///test.groovy")

        // Test completion
        val completionParams = CompletionParams().apply {
            textDocument = TextDocumentIdentifier("file:///test.groovy")
            position = Position(3, 12) // Inside the method
        }

        val completions = server.textDocumentService.completion(completionParams).get()
        assertNotNull(completions)
        assertTrue(completions.isLeft) // Should return list, not CompletionList

        val items = completions.left
        assertTrue(items.isNotEmpty())
        assertTrue(items.any { it.label == "println" })

        // Test shutdown
        val shutdownResult = server.shutdown().get()
        assertNotNull(shutdownResult)
    }
}
