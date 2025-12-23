package com.github.albertocavalcante.groovylsp.config

import com.github.albertocavalcante.groovylsp.Version
import org.eclipse.lsp4j.CodeLensOptions
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.SemanticTokenModifiers
import org.eclipse.lsp4j.SemanticTokenTypes
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * Factory for creating LSP ServerCapabilities.
 * Encapsulates all capability registration logic to keep the main server class clean.
 */
object ServerCapabilitiesFactory {

    fun createInitializeResult(): InitializeResult {
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

            // Document formatting
            documentFormattingProvider = Either.forLeft(true)

            // References
            referencesProvider = Either.forLeft(true)

            // Type definition support
            typeDefinitionProvider = Either.forLeft(true)

            // Signature help support
            signatureHelpProvider = SignatureHelpOptions().apply {
                triggerCharacters = listOf("(", ",")
            }

            // Rename support
            renameProvider = Either.forLeft(true)

            // Code actions
            codeActionProvider = Either.forLeft(true)

            // Semantic tokens support
            semanticTokensProvider = createSemanticTokensOptions()

            // CodeLens support for test run/debug buttons
            codeLensProvider = CodeLensOptions().apply {
                resolveProvider = false
            }
        }

        val serverInfo = ServerInfo().apply {
            name = "Groovy Language Server"
            version = Version.current
        }

        return InitializeResult(capabilities, serverInfo)
    }

    private fun createSemanticTokensOptions(): SemanticTokensWithRegistrationOptions =
        SemanticTokensWithRegistrationOptions().apply {
            legend = SemanticTokensLegend().apply {
                // Token types - MUST match indices in JenkinsSemanticTokenProvider.TokenTypes
                tokenTypes = listOf(
                    SemanticTokenTypes.Namespace, // 0
                    SemanticTokenTypes.Type, // 1
                    SemanticTokenTypes.Class, // 2
                    SemanticTokenTypes.Enum, // 3
                    SemanticTokenTypes.Interface, // 4
                    SemanticTokenTypes.Struct, // 5
                    SemanticTokenTypes.TypeParameter, // 6
                    SemanticTokenTypes.Parameter, // 7
                    SemanticTokenTypes.Variable, // 8
                    SemanticTokenTypes.Property, // 9
                    SemanticTokenTypes.EnumMember, // 10
                    SemanticTokenTypes.Event, // 11
                    SemanticTokenTypes.Function, // 12
                    SemanticTokenTypes.Method, // 13
                    SemanticTokenTypes.Macro, // 14 <- Used for pipeline blocks
                    SemanticTokenTypes.Keyword, // 15
                    SemanticTokenTypes.Modifier, // 16
                    SemanticTokenTypes.Comment, // 17
                    SemanticTokenTypes.String, // 18
                    SemanticTokenTypes.Number, // 19
                    SemanticTokenTypes.Regexp, // 20
                    SemanticTokenTypes.Operator, // 21
                    SemanticTokenTypes.Decorator, // 22 <- Used for wrapper blocks
                )

                // Token modifiers (bitfield)
                tokenModifiers = listOf(
                    SemanticTokenModifiers.Declaration,
                    SemanticTokenModifiers.Definition,
                    SemanticTokenModifiers.Readonly,
                    SemanticTokenModifiers.Static,
                    SemanticTokenModifiers.Deprecated,
                    SemanticTokenModifiers.Abstract,
                    SemanticTokenModifiers.Async,
                    SemanticTokenModifiers.Modification,
                    SemanticTokenModifiers.Documentation,
                    SemanticTokenModifiers.DefaultLibrary,
                )
            }

            // Support full document semantic tokens (no delta updates yet)
            full = Either.forLeft(true)
        }
}
