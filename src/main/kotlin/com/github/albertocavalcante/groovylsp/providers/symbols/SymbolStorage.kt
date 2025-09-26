package com.github.albertocavalcante.groovylsp.providers.symbols

import com.github.albertocavalcante.groovylsp.ast.AstVisitor
import com.github.albertocavalcante.groovylsp.errors.LspResult
import com.github.albertocavalcante.groovylsp.errors.symbolNotFoundError
import com.github.albertocavalcante.groovylsp.errors.toLspResult
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.ImportNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.Variable
import org.eclipse.lsp4j.Position
import java.net.URI
import kotlin.reflect.KClass

/**
 * Immutable, type-safe symbol storage using persistent data structures
 */
data class SymbolStorage(
    val symbols: PersistentMap<URI, PersistentList<Symbol>> = persistentMapOf(),
    val symbolsByName: PersistentMap<Pair<URI, SymbolName>, PersistentList<Symbol>> = persistentMapOf(),
    val symbolsByCategory: PersistentMap<Pair<URI, SymbolCategory>, PersistentList<Symbol>> = persistentMapOf(),
) {

    /**
     * Adds a symbol and returns a new SymbolStorage instance
     */
    fun add(symbol: Symbol): SymbolStorage {
        val uri = symbol.uri
        val name = symbol.name
        val category = symbol.category()

        return copy(
            symbols = symbols.put(uri, (symbols[uri] ?: persistentListOf()).add(symbol)),
            symbolsByName = symbolsByName.put(
                uri to name,
                (symbolsByName[uri to name] ?: persistentListOf()).add(symbol),
            ),
            symbolsByCategory = symbolsByCategory.put(
                uri to category,
                (symbolsByCategory[uri to category] ?: persistentListOf()).add(symbol),
            ),
        )
    }

    /**
     * Adds multiple symbols at once
     */
    fun addAll(symbols: List<Symbol>): SymbolStorage = symbols.fold(this) { storage, symbol -> storage.add(symbol) }

    /**
     * Type-safe symbol lookup by name and type
     */
    inline fun <reified T : Symbol> find(uri: URI, name: SymbolName): LspResult<T> = symbolsByName[uri to name]
        ?.filterIsInstance<T>()
        ?.firstOrNull()
        ?.toLspResult()
        ?: uri.symbolNotFoundError(name, Position(0, 0), T::class.java.simpleName).toLspResult()

    /**
     * Finds all symbols of a specific type
     */
    inline fun <reified T : Symbol> findAll(uri: URI): kotlin.collections.List<T> = symbols[uri]
        ?.filterIsInstance<T>()
        ?: emptyList()

    /**
     * Finds all symbols by category
     */
    fun findByCategory(uri: URI, category: SymbolCategory): List<Symbol> =
        symbolsByCategory[uri to category] ?: emptyList()

    /**
     * Finds symbols matching a pattern
     */
    fun findMatching(uri: URI, pattern: String): List<Symbol> = symbols[uri]
        ?.filter { it.matches(pattern) }
        ?: emptyList()

    /**
     * Finds symbols matching a regex
     */
    fun findMatching(uri: URI, regex: Regex): List<Symbol> = symbols[uri]
        ?.filter { it.matches(regex) }
        ?: emptyList()

    /**
     * Gets all symbols for a URI
     */
    fun getSymbols(uri: URI): List<Symbol> = symbols[uri] ?: emptyList()

    /**
     * Gets all URIs that have symbols
     */
    fun getUris(): Set<URI> = symbols.keys.toSet()

    /**
     * Checks if storage is empty
     */
    fun isEmpty(): Boolean = symbols.isEmpty()

    /**
     * Clears all symbols and returns empty storage
     */
    fun clear(): SymbolStorage = SymbolStorage()

    /**
     * Clears symbols for a specific URI
     */
    fun clearUri(uri: URI): SymbolStorage {
        if (symbols[uri] == null) return this

        return copy(
            symbols = symbols.remove(uri),
            symbolsByName = symbolsByName.filterKeys { it.first != uri }.toPersistentMap(),
            symbolsByCategory = symbolsByCategory.filterKeys { it.first != uri }.toPersistentMap(),
        )
    }

    /**
     * Gets statistics about the symbol storage
     */
    fun getStatistics(): SymbolStatistics {
        val allSymbols = symbols.values.flatten()

        return SymbolStatistics(
            totalSymbols = allSymbols.size,
            symbolsByCategory = SymbolCategory.values().associateWith { category ->
                allSymbols.count { it.category() == category }
            },
            symbolsByUri = symbols.mapValues { it.value.size },
            uniqueNames = allSymbols.map { it.name }.distinct().size,
        )
    }
}

/**
 * Statistics about symbol storage
 */
data class SymbolStatistics(
    val totalSymbols: Int,
    val symbolsByCategory: Map<SymbolCategory, Int>,
    val symbolsByUri: Map<URI, Int>,
    val uniqueNames: Int,
)

/**
 * DSL for querying symbols
 */
class SymbolQuery {
    var uri: URI? = null
    var name: String? = null
    var namePattern: Regex? = null
    var category: SymbolCategory? = null
    var visibility: Visibility? = null
    var isStatic: Boolean? = null

    inline fun <reified T : Symbol> type(): KClass<T> = T::class

    fun execute(storage: SymbolStorage): List<Symbol> {
        val baseSymbols = uri?.let { storage.getSymbols(it) } ?: storage.symbols.values.flatten()

        return baseSymbols
            .filter(createNameFilter())
            .filter(createPatternFilter())
            .filter(createCategoryFilter())
            .filter(createVisibilityFilter())
            .filter(createStaticFilter())
    }

    private fun createNameFilter(): (Symbol) -> Boolean = { symbol ->
        name?.let { symbol.name == it } ?: true
    }

    private fun createPatternFilter(): (Symbol) -> Boolean = { symbol ->
        namePattern?.let { symbol.matches(it) } ?: true
    }

    private fun createCategoryFilter(): (Symbol) -> Boolean = { symbol ->
        category?.let { symbol.category() == it } ?: true
    }

    private fun createVisibilityFilter(): (Symbol) -> Boolean = { symbol ->
        visibility?.let { vis -> matchesVisibility(symbol, vis) } ?: true
    }

    private fun createStaticFilter(): (Symbol) -> Boolean = { symbol ->
        isStatic?.let { static -> matchesStatic(symbol, static) } ?: true
    }

    private fun matchesVisibility(symbol: Symbol, vis: Visibility): Boolean = when (symbol) {
        is Symbol.Method -> symbol.visibility == vis
        is Symbol.Field -> symbol.visibility == vis
        is Symbol.Property -> symbol.visibility == vis
        is Symbol.Class -> symbol.visibility == vis
        else -> true
    }

    private fun matchesStatic(symbol: Symbol, static: Boolean): Boolean = when (symbol) {
        is Symbol.Method -> symbol.isStatic == static
        is Symbol.Field -> symbol.isStatic == static
        is Symbol.Property -> symbol.isStatic == static
        else -> true
    }
}

/**
 * DSL function for querying symbols
 */
fun SymbolStorage.query(block: SymbolQuery.() -> Unit): List<Symbol> = SymbolQuery().apply(block).execute(this)

/**
 * Builder for creating SymbolStorage from AST nodes
 */
class SymbolBuilder(private val uri: URI) {
    private val symbols = mutableListOf<Symbol>()

    fun variable(variable: Variable): SymbolBuilder = apply {
        symbols += Symbol.Variable.from(variable, uri)
    }

    fun method(method: MethodNode): SymbolBuilder = apply {
        symbols += Symbol.Method.from(method, uri)
    }

    fun field(field: FieldNode): SymbolBuilder = apply {
        symbols += Symbol.Field.from(field, uri)
    }

    fun property(property: PropertyNode): SymbolBuilder = apply {
        symbols += Symbol.Property.from(property, uri)
    }

    fun classNode(classNode: ClassNode): SymbolBuilder = apply {
        symbols += Symbol.Class.from(classNode, uri)
    }

    fun import(import: ImportNode): SymbolBuilder = apply {
        symbols += Symbol.Import.from(import, uri)
    }

    fun staticImport(import: ImportNode): SymbolBuilder = apply {
        symbols += Symbol.Import.fromStatic(import, uri)
    }

    fun build(): List<Symbol> = symbols.toList()
}

/**
 * DSL function for building symbols
 */
fun buildSymbols(uri: URI, block: SymbolBuilder.() -> Unit): List<Symbol> = SymbolBuilder(uri).apply(block).build()

/**
 * Extension function to build SymbolStorage from AstVisitor
 */
fun SymbolStorage.buildFromVisitor(visitor: AstVisitor): SymbolStorage {
    var storage = this

    visitor.getAllNodes().forEach { node ->
        val uri = visitor.getUri(node) ?: return@forEach

        val symbol = when (node) {
            is Variable -> Symbol.Variable.from(node, uri)
            is MethodNode -> Symbol.Method.from(node, uri)
            is FieldNode -> Symbol.Field.from(node, uri)
            is PropertyNode -> Symbol.Property.from(node, uri)
            is ClassNode -> Symbol.Class.from(node, uri)
            is ImportNode -> Symbol.Import.from(node, uri)
            else -> null
        }

        symbol?.let { storage = storage.add(it) }
    }

    return storage
}

/**
 * Compatibility layer for the old SymbolTable interface
 */
@Deprecated("Use SymbolStorage directly", ReplaceWith("SymbolStorage"))
class TypeSafeSymbolTable(private var storage: SymbolStorage = SymbolStorage()) {

    fun addVariableDeclaration(uri: URI, variable: Variable) {
        storage = storage.add(Symbol.Variable.from(variable, uri))
    }

    fun addMethodDeclaration(uri: URI, method: MethodNode) {
        storage = storage.add(Symbol.Method.from(method, uri))
    }

    fun addClassDeclaration(uri: URI, classNode: ClassNode) {
        storage = storage.add(Symbol.Class.from(classNode, uri))
    }

    fun findVariableDeclaration(uri: URI, name: String): Variable? = storage.find<Symbol.Variable>(uri, name)
        .getOrNull()
        ?.node as? Variable

    fun buildFromVisitor(visitor: AstVisitor) {
        storage = storage.buildFromVisitor(visitor)
    }

    fun clear() {
        storage = storage.clear()
    }

    fun getStatistics(): Map<String, Any> = with(storage.getStatistics()) {
        mapOf(
            "totalSymbols" to totalSymbols,
            "variables" to (symbolsByCategory[SymbolCategory.VARIABLE] ?: 0),
            "methods" to (symbolsByCategory[SymbolCategory.METHOD] ?: 0),
            "classes" to (symbolsByCategory[SymbolCategory.CLASS] ?: 0),
            "fields" to (symbolsByCategory[SymbolCategory.FIELD] ?: 0),
            "properties" to (symbolsByCategory[SymbolCategory.PROPERTY] ?: 0),
            "imports" to (symbolsByCategory[SymbolCategory.IMPORT] ?: 0),
            "uniqueNames" to uniqueNames,
        )
    }
}
