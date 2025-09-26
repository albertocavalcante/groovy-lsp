package com.github.albertocavalcante.groovylsp.ast.registry

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ImportNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Variable
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal storage for symbol registry.
 * Contains all the underlying data structures for storing symbols.
 */
class SymbolStorage {

    val variableDeclarations = ConcurrentHashMap<URI, MutableMap<String, Variable>>()
    val methodDeclarations = ConcurrentHashMap<URI, MutableMap<String, MutableList<MethodNode>>>()
    val classDeclarations = ConcurrentHashMap<URI, MutableMap<String, ClassNode>>()
    val importDeclarations = ConcurrentHashMap<URI, MutableMap<String, ImportNode>>()
    val fieldDeclarations = ConcurrentHashMap<ClassNode, MutableMap<String, ASTNode>>()

    fun clear() {
        variableDeclarations.clear()
        methodDeclarations.clear()
        classDeclarations.clear()
        importDeclarations.clear()
        fieldDeclarations.clear()
    }

    fun isEmpty(): Boolean = variableDeclarations.isEmpty() &&
        methodDeclarations.isEmpty() &&
        classDeclarations.isEmpty() &&
        importDeclarations.isEmpty() &&
        fieldDeclarations.isEmpty()

    fun getStatistics(): Map<String, Int> = mapOf(
        "variables" to variableDeclarations.values.sumOf { it.size },
        "methods" to methodDeclarations.values.sumOf { it.values.sumOf { methods -> methods.size } },
        "classes" to classDeclarations.values.sumOf { it.size },
        "imports" to importDeclarations.values.sumOf { it.size },
        "fields" to fieldDeclarations.values.sumOf { it.size },
    )
}
