package com.github.albertocavalcante.groovylsp

import kotlinx.coroutines.*
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class GroovyLanguageServer : LanguageServer, LanguageClientAware, TextDocumentService, WorkspaceService {

    private val logger = LoggerFactory.getLogger(GroovyLanguageServer::class.java)
    private var client: LanguageClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Server lifecycle

    override fun connect(client: LanguageClient) {
        logger.info("Connected to language client")
        this.client = client
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        return coroutineScope.future {
            logger.info("Initializing Groovy Language Server...")
            logger.info("Client: ${params.clientInfo?.name ?: "Unknown"}")
            logger.info("Root URI: ${params.rootUri}")
            logger.info("Workspace folders: ${params.workspaceFolders?.map { it.uri }}")

            val capabilities = ServerCapabilities().apply {
                // Text synchronization
                textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)

                // Completion support
                completionProvider = CompletionOptions().apply {
                    resolveProvider = false
                    triggerCharacters = listOf(".", ":", "=", "*")
                }

                // Hover support
                hoverProvider = Either.forLeft(true)

                // Definition support
                definitionProvider = Either.forLeft(true)

                // Document symbols
                documentSymbolProvider = Either.forLeft(true)

                // Workspace symbols
                workspaceSymbolProvider = Either.forLeft(true)

                // References
                referencesProvider = Either.forLeft(true)

                // Diagnostics will be pushed
            }

            val serverInfo = ServerInfo().apply {
                name = "Groovy Language Server"
                version = "0.1.0-SNAPSHOT"
            }

            InitializeResult(capabilities, serverInfo)
        }
    }

    override fun initialized(params: InitializedParams) {
        logger.info("Server initialized")

        // Send a test message to the client
        client?.showMessage(MessageParams().apply {
            type = MessageType.Info
            message = "Groovy Language Server is ready!"
        })
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.supplyAsync {
            logger.info("Shutting down Groovy Language Server...")
            try {
                coroutineScope.cancel()
            } catch (e: Exception) {
                logger.error("Error during shutdown", e)
            }
            Any()
        }
    }

    override fun exit() {
        logger.info("Exiting Groovy Language Server")
        // LSP spec says exit should just terminate - the client handles process termination
        // We don't call System.exit() here as it should be handled by the caller
    }

    // Document service

    override fun getTextDocumentService(): TextDocumentService = this

    override fun didOpen(params: DidOpenTextDocumentParams) {
        logger.info("Opened: ${params.textDocument.uri}")

        // Send a simple diagnostic for testing
        val diagnostics = mutableListOf<Diagnostic>()

        // Add a hello world diagnostic
        if (params.textDocument.text.contains("TODO")) {
            diagnostics.add(Diagnostic().apply {
                range = Range(Position(0, 0), Position(0, 4))
                severity = DiagnosticSeverity.Information
                source = "groovy-lsp"
                message = "TODO found - Hello from Groovy LSP!"
            })
        }

        client?.publishDiagnostics(PublishDiagnosticsParams().apply {
            uri = params.textDocument.uri
            this.diagnostics = diagnostics
        })
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        logger.debug("Changed: ${params.textDocument.uri}")
        // Re-analyze on change (simplified for now)
        val text = params.contentChanges.firstOrNull()?.text ?: return

        // Clear diagnostics for now
        client?.publishDiagnostics(PublishDiagnosticsParams().apply {
            uri = params.textDocument.uri
            diagnostics = emptyList()
        })
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        logger.info("Closed: ${params.textDocument.uri}")

        // Clear diagnostics
        client?.publishDiagnostics(PublishDiagnosticsParams().apply {
            uri = params.textDocument.uri
            diagnostics = emptyList()
        })
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.debug("Saved: ${params.textDocument.uri}")
    }

    // Completion

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return coroutineScope.future {
            logger.debug("Completion requested at ${params.position}")

            // Simple hello world completions
            val items = listOf(
                CompletionItem().apply {
                    label = "println"
                    kind = CompletionItemKind.Function
                    detail = "println(value)"
                    documentation = Either.forLeft("Prints a value to the console")
                    insertText = "println(\$1)"
                    insertTextFormat = InsertTextFormat.Snippet
                },
                CompletionItem().apply {
                    label = "def"
                    kind = CompletionItemKind.Keyword
                    detail = "def name = value"
                    documentation = Either.forLeft("Define a variable")
                    insertText = "def \${1:name} = \${2:value}"
                    insertTextFormat = InsertTextFormat.Snippet
                },
                CompletionItem().apply {
                    label = "class"
                    kind = CompletionItemKind.Keyword
                    detail = "class Name { ... }"
                    documentation = Either.forLeft("Define a class")
                    insertText = "class \${1:Name} {\n    \$0\n}"
                    insertTextFormat = InsertTextFormat.Snippet
                }
            )

            Either.forLeft(items)
        }
    }

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        return CompletableFuture.completedFuture(unresolved)
    }

    // Hover

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        return coroutineScope.future {
            logger.debug("Hover requested at ${params.position}")

            val hover = Hover().apply {
                contents = Either.forRight(MarkupContent().apply {
                    kind = MarkupKind.MARKDOWN
                    value = """
                        ## Hello from Groovy LSP!

                        This is a test hover message.
                        Position: Line ${params.position.line + 1}, Column ${params.position.character + 1}
                    """.trimIndent()
                })
            }

            hover
        }
    }

    // Definition

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        return coroutineScope.future {
            logger.debug("Definition requested at ${params.position}")
            Either.forLeft(emptyList<Location>())
        }
    }

    // Document symbols

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        return coroutineScope.future {
            logger.debug("Document symbols requested for ${params.textDocument.uri}")
            emptyList()
        }
    }

    // Workspace service

    override fun getWorkspaceService(): WorkspaceService = this

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        logger.info("Configuration changed")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        params.changes.forEach { change ->
            logger.debug("File changed: ${change.uri} (${change.type})")
        }
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> {
        return coroutineScope.future {
            logger.debug("Workspace symbols requested: ${params.query}")
            Either.forLeft(emptyList<SymbolInformation>())
        }
    }

    // Helper extension for coroutines
    private fun <T> CoroutineScope.future(block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        launch {
            try {
                future.complete(block())
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }
}