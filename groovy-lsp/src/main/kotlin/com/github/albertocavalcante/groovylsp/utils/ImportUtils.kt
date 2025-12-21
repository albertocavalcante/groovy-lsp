package com.github.albertocavalcante.groovylsp.utils

import org.eclipse.lsp4j.Position

/**
 * Utilities for working with import statements in Groovy files.
 */
object ImportUtils {

    /**
     * Extracts existing import statements from file content.
     *
     * @param content The file content
     * @return Set of fully qualified names that are already imported
     */
    fun extractExistingImports(content: String): Set<String> {
        val imports = mutableSetOf<String>()
        val lines = content.lines()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("import ") -> {
                    val importStatement = trimmed.removePrefix("import ").removeSuffix(";")
                    // Handle static imports and star imports
                    if (!importStatement.startsWith("static ") && !importStatement.endsWith(".*")) {
                        imports.add(importStatement.trim())
                    }
                }
                trimmed.startsWith("package ") -> {
                    // Package declarations appear before imports, continue scanning
                }
                trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*") -> {
                    // Stop at first non-comment, non-empty line that's not import/package
                    return imports
                }
            }
        }

        return imports
    }

    /**
     * Finds the position where a new import statement should be inserted.
     * Returns the position after the last import statement, or after the package statement.
     *
     * @param content The file content
     * @return Position for inserting a new import
     */
    fun findImportInsertPosition(content: String): Position {
        val lines = content.lines()
        var lastImportLine = -1
        var packageLine = -1

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            when {
                trimmed.startsWith("package ") -> {
                    packageLine = i
                }
                trimmed.startsWith("import ") -> {
                    lastImportLine = i
                }
                trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*") -> {
                    // Stop at first non-comment, non-empty line that's not import/package
                    break
                }
            }
        }

        // Insert after last import, or after package, or at line 0
        val insertLine = when {
            lastImportLine >= 0 -> lastImportLine + 1
            packageLine >= 0 -> packageLine + 1
            else -> 0
        }

        return Position(insertLine, 0)
    }
}
