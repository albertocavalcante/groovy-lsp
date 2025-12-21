package com.github.albertocavalcante.groovylsp.utils

import org.eclipse.lsp4j.Position

/**
 * Utilities for working with import statements in Groovy files.
 */
object ImportUtils {

    /**
     * Extracts existing import statements from file content.
     * Properly handles multi-line comments between package and import statements.
     *
     * @param content The file content
     * @return Set of fully qualified names that are already imported
     */
    fun extractExistingImports(content: String): Set<String> {
        val imports = mutableSetOf<String>()
        val lines = content.lines()
        var inMultiLineComment = false

        for (line in lines) {
            val trimmed = line.trim()

            // Track multi-line comment state
            val (newInComment, processedLine) = processCommentState(trimmed, inMultiLineComment)
            inMultiLineComment = newInComment

            // Skip if we're inside a comment or line was consumed by comment
            if (inMultiLineComment || processedLine.isBlank()) continue

            when {
                processedLine.startsWith("import ") -> {
                    parseImport(processedLine)?.let { imports.add(it) }
                }
                processedLine.startsWith("package ") -> {
                    // Package declarations appear before imports, continue scanning
                }
                processedLine.isNotEmpty() && !isCommentLine(processedLine) -> {
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
     * Properly handles multi-line comments.
     *
     * @param content The file content
     * @return Position for inserting a new import
     */
    fun findImportInsertPosition(content: String): Position {
        val lines = content.lines()
        var lastImportLine = -1
        var packageLine = -1
        var inMultiLineComment = false

        for (i in lines.indices) {
            val trimmed = lines[i].trim()

            // Track multi-line comment state
            val (newInComment, processedLine) = processCommentState(trimmed, inMultiLineComment)
            inMultiLineComment = newInComment

            // Skip if we're inside a comment or line was consumed by comment
            if (inMultiLineComment || processedLine.isBlank()) continue

            when {
                processedLine.startsWith("package ") -> {
                    packageLine = i
                }
                processedLine.startsWith("import ") -> {
                    lastImportLine = i
                }
                processedLine.isNotEmpty() && !isCommentLine(processedLine) -> {
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

    /**
     * Processes comment state for a line.
     * Returns (isStillInComment, processedLineContent).
     */
    private fun processCommentState(line: String, inMultiLineComment: Boolean): Pair<Boolean, String> {
        var stillInComment = inMultiLineComment
        var remaining = line

        // If we're in a multi-line comment, look for the end
        if (stillInComment) {
            val endIndex = remaining.indexOf("*/")
            if (endIndex >= 0) {
                remaining = remaining.substring(endIndex + 2).trim()
                stillInComment = false
            } else {
                return true to "" // Still in comment, nothing to process
            }
        }

        // Look for start of multi-line comment
        val startIndex = remaining.indexOf("/*")
        if (startIndex >= 0) {
            val beforeComment = remaining.substring(0, startIndex).trim()
            val afterStart = remaining.substring(startIndex + 2)
            val endIndex = afterStart.indexOf("*/")

            if (endIndex >= 0) {
                // Comment ends on same line
                val afterEnd = afterStart.substring(endIndex + 2).trim()
                remaining = beforeComment + " " + afterEnd
            } else {
                // Multi-line comment continues
                stillInComment = true
                remaining = beforeComment
            }
        }

        return stillInComment to remaining.trim()
    }

    /**
     * Parses an import line and extracts the fully qualified name.
     * Returns null for static imports and star imports.
     */
    private fun parseImport(line: String): String? {
        val importStatement = line.removePrefix("import ").removeSuffix(";").trim()
        return when {
            importStatement.startsWith("static ") -> null
            importStatement.endsWith(".*") -> null
            else -> importStatement
        }
    }

    /**
     * Checks if a line is a single-line comment.
     */
    private fun isCommentLine(line: String): Boolean =
        line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")
}
