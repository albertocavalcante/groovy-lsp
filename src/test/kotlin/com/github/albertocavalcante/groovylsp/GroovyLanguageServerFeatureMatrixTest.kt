package com.github.albertocavalcante.groovylsp

import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GroovyLanguageServerFeatureMatrixTest {

    private lateinit var server: GroovyLanguageServer
    private lateinit var client: SynchronizingTestLanguageClient
    private lateinit var workspaceRoot: Path
    private lateinit var documentUri: String

    private val documentContent = """
        class Greeter {
            String message = "Hi"
            void greet() {
                println message
            }
        }

        def greeter = new Greeter()
        greeter.message
        greeter.greet()
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        workspaceRoot = Files.createTempDirectory("groovy-lsp-feature-matrix")
        documentUri = workspaceRoot.resolve("feature-matrix.groovy").toUri().toString()

        server = GroovyLanguageServer()
        client = SynchronizingTestLanguageClient()
        server.connect(client)

        val initParams = InitializeParams().apply {
            processId = 99
            workspaceFolders = listOf(WorkspaceFolder(workspaceRoot.toUri().toString(), "feature-matrix"))
        }

        runBlocking {
            server.initialize(initParams).get()
            server.initialized(InitializedParams())
        }

        val textDocument = TextDocumentItem(documentUri, "groovy", 1, documentContent)
        server.textDocumentService.didOpen(org.eclipse.lsp4j.DidOpenTextDocumentParams(textDocument))

        client.awaitSuccessfulCompilation(documentUri)
    }

    @Test
    fun `completion provider returns static suggestions`() = runBlocking {
        val params = CompletionParams().apply {
            textDocument = TextDocumentIdentifier(documentUri)
            position = Position(7, 0)
        }

        val result = server.textDocumentService.completion(params).get()
        assertTrue(result.isLeft)
        val items = result.left
        assertTrue(items.isNotEmpty(), "Expected completion items for empty position.")
    }

    @Test
    fun `definition provider resolves class constructor reference`() = runBlocking {
        val params = DefinitionParams().apply {
            textDocument = TextDocumentIdentifier(documentUri)
            position = Position(7, 20) // Inside "Greeter" on constructor call
        }

        val result = server.textDocumentService.definition(params).get()
        assertTrue(result.isLeft)
        val locations = result.left
        assertTrue(locations.isNotEmpty(), "Definition request should return at least one location.")
        assertTrue(
            locations.any { it.uri == documentUri },
            "Definition should point to symbols within the current document.",
        )
    }

    @Test
    fun `references provider finds field usage`() = runBlocking {
        val params = ReferenceParams().apply {
            textDocument = TextDocumentIdentifier(documentUri)
            position = Position(8, 9) // Over "message" access
            context = ReferenceContext(true)
        }

        val locations = server.textDocumentService.references(params).get()
        assertTrue(
            locations.isEmpty(),
            "References provider is not yet wired through the LSP service; update when implemented.",
        )
    }

    @Test
    fun `hover provider returns markdown`() = runBlocking {
        val params = HoverParams().apply {
            textDocument = TextDocumentIdentifier(documentUri)
            position = Position(8, 2) // Over "greeter"
        }

        val hover = server.textDocumentService.hover(params).get()
        assertNotNull(hover.contents)
        assertTrue(hover.contents.isRight)
    }

    @Test
    fun `type definition currently returns no results`() = runBlocking {
        val params = TypeDefinitionParams().apply {
            textDocument = TextDocumentIdentifier(documentUri)
            position = Position(8, 2)
        }

        val either = server.textDocumentService.typeDefinition(params).get()
        assertTrue(either.isLeft)
        val locations = either.left
        assertTrue(locations.isNotEmpty(), "Type definition should resolve to the symbol declaration.")
    }

    @Test
    fun `document symbols placeholder returns empty list`() = runBlocking {
        val params = DocumentSymbolParams().apply {
            textDocument = TextDocumentIdentifier(documentUri)
        }
        val symbols = server.textDocumentService.documentSymbol(params).get()
        assertTrue(symbols.isEmpty(), "Document symbol provider not implemented; ensure capability tracked.")
    }

    @Test
    fun `workspace symbol placeholder returns empty list`() = runBlocking {
        val result: Either<List<SymbolInformation>, List<WorkspaceSymbol>> =
            server.workspaceService.symbol(WorkspaceSymbolParams("Greeter")).get()
        assertTrue(result.isLeft)
        assertTrue(result.left.isEmpty(), "Workspace symbol provider not implemented; ensure capability tracked.")
    }

    @Test
    fun `rename operation is unsupported`() {
        runBlocking {
            val params = RenameParams().apply {
                textDocument = TextDocumentIdentifier(documentUri)
                position = Position(8, 2)
                newName = "updatedGreeter"
            }

            val error = assertFailsWith<UnsupportedOperationException> {
                server.textDocumentService.rename(params).get()
            }
            assertNotNull(error)
        }
    }
}
