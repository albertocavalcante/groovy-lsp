package com.github.albertocavalcante.groovylsp

import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GroovyLanguageServerTest {

    private lateinit var server: GroovyLanguageServer
    private lateinit var mockClient: TestLanguageClient

    @BeforeEach
    fun setup() {
        server = GroovyLanguageServer()
        mockClient = TestLanguageClient()
        server.connect(mockClient)
    }

    @Test
    fun `test server initialization`() = runBlocking {
        val params = InitializeParams().apply {
            processId = 1234
            rootUri = "file:///test/project"
            capabilities = ClientCapabilities()
            clientInfo = ClientInfo("Test Client", "1.0.0")
        }

        val result = server.initialize(params).get()

        assertNotNull(result)
        assertNotNull(result.capabilities)
        assertEquals("Groovy Language Server", result.serverInfo?.name)
        assertEquals("0.1.0-SNAPSHOT", result.serverInfo?.version)

        // Check capabilities
        val capabilities = result.capabilities
        assertNotNull(capabilities.completionProvider)
        assertTrue(capabilities.completionProvider.triggerCharacters?.contains(".") == true)
        assertNotNull(capabilities.hoverProvider)
        assertNotNull(capabilities.definitionProvider)
    }

    @Test
    fun `test completion returns items`() = runBlocking {
        val params = CompletionParams().apply {
            textDocument = TextDocumentIdentifier("file:///test.groovy")
            position = Position(0, 0)
        }

        val result = server.completion(params).get()

        assertNotNull(result)
        assertTrue(result.isLeft)

        val items = result.left
        assertTrue(items.isNotEmpty())
        assertTrue(items.any { it.label == "println" })
        assertTrue(items.any { it.label == "def" })
        assertTrue(items.any { it.label == "class" })
    }

    @Test
    fun `test hover returns content`() = runBlocking {
        val params = HoverParams().apply {
            textDocument = TextDocumentIdentifier("file:///test.groovy")
            position = Position(5, 10)
        }

        val result = server.hover(params).get()

        assertNotNull(result)
        assertNotNull(result.contents)
        assertTrue(result.contents.isRight)

        val content = result.contents.right
        assertEquals(MarkupKind.MARKDOWN, content.kind)
        assertTrue(content.value.contains("Hello from Groovy LSP"))
        assertTrue(content.value.contains("Line 6, Column 11")) // 0-indexed to 1-indexed
    }

    @Test
    fun `test document open with TODO triggers diagnostic`() {
        val params = DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = "file:///test.groovy"
                languageId = "groovy"
                version = 1
                text = "// TODO: Implement this\nprintln 'Hello World'"
            }
        }

        server.didOpen(params)

        // Check that diagnostic was published
        val publishedDiagnostics = mockClient.diagnostics
        assertNotNull(publishedDiagnostics)
        assertEquals("file:///test.groovy", publishedDiagnostics.uri)
        assertTrue(publishedDiagnostics.diagnostics.isNotEmpty())

        val diagnostic = publishedDiagnostics.diagnostics.first()
        assertEquals(DiagnosticSeverity.Information, diagnostic.severity)
        assertTrue(diagnostic.message.contains("TODO found"))
    }

    @Test
    fun `test shutdown and exit`() = runBlocking {
        val result = server.shutdown().get()
        assertNotNull(result)
        // Note: We don't actually call exit() in tests as it would terminate the test JVM
    }

    // Mock client for testing
    private class TestLanguageClient : LanguageClient {
        var diagnostics: PublishDiagnosticsParams? = null
        var messages = mutableListOf<MessageParams>()

        override fun telemetryEvent(obj: Any?) {}

        override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
            this.diagnostics = diagnostics
        }

        override fun showMessage(messageParams: MessageParams) {
            messages.add(messageParams)
        }

        override fun showMessageRequest(requestParams: ShowMessageRequestParams) =
            CompletableFuture.completedFuture(MessageActionItem())

        override fun logMessage(message: MessageParams) {}

        override fun workspaceFolders() = CompletableFuture.completedFuture(emptyList<WorkspaceFolder>())

        override fun configuration(params: ConfigurationParams) =
            CompletableFuture.completedFuture(emptyList<Any>())

        override fun createProgress(params: WorkDoneProgressCreateParams): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)

        override fun notifyProgress(params: ProgressParams) {}

        override fun registerCapability(params: RegistrationParams): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)

        override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)

        override fun applyEdit(params: ApplyWorkspaceEditParams) =
            CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(false))

        override fun refreshSemanticTokens(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

        override fun refreshCodeLenses(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

        override fun showDocument(params: ShowDocumentParams) =
            CompletableFuture.completedFuture(ShowDocumentResult(true))

        override fun refreshDiagnostics(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

        override fun refreshInlayHints(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

        override fun refreshInlineValues(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
    }
}