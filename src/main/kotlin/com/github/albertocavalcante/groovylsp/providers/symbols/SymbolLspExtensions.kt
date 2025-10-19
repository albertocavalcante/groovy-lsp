package com.github.albertocavalcante.groovylsp.providers.symbols

import com.github.albertocavalcante.groovylsp.ast.safeRange
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind

/**
 * Converts a [Symbol] into a [SymbolInformation] for use in LSP responses.
 */
fun Symbol.toSymbolInformation(): SymbolInformation? {
    val range = toLspRange() ?: return null
    val info = SymbolInformation(name, toSymbolKind(), Location(uri.toString(), range))
    info.containerName = containerName()
    return info
}

/**
 * Converts a [Symbol] into a [DocumentSymbol]. Currently we emit flat symbols (no children).
 */
fun Symbol.toDocumentSymbol(): DocumentSymbol? {
    val range = toLspRange() ?: return null
    val symbol = DocumentSymbol(name, toSymbolKind(), range, range)
    symbol.detail = detail()
    return symbol
}

private fun Symbol.toSymbolKind(): SymbolKind = when (this) {
    is Symbol.Class -> SymbolKind.Class
    is Symbol.Method -> SymbolKind.Method
    is Symbol.Field -> SymbolKind.Field
    is Symbol.Property -> SymbolKind.Property
    is Symbol.Variable -> SymbolKind.Variable
    is Symbol.Import -> SymbolKind.Module
}

private fun Symbol.containerName(): String? = when (this) {
    is Symbol.Method -> owner?.nameWithoutPackage ?: owner?.name
    is Symbol.Field -> owner?.nameWithoutPackage ?: owner?.name
    is Symbol.Property -> owner?.nameWithoutPackage ?: owner?.name
    is Symbol.Class -> packageName
    is Symbol.Import -> packageName
    else -> null
}

private fun Symbol.detail(): String? = when (this) {
    is Symbol.Method -> signature
    is Symbol.Field -> type?.nameWithoutPackage
    is Symbol.Property -> type?.nameWithoutPackage
    is Symbol.Class -> fullyQualifiedName
    is Symbol.Variable -> type?.nameWithoutPackage
    is Symbol.Import -> importedName
}

private fun Symbol.toLspRange(): Range? {
    val range = node.safeRange().getOrNull()
    if (range != null) {
        return range
    }

    val start = position.getOrNull()?.toLspPosition() ?: return null
    return Range(start, start)
}
