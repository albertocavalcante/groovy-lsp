package com.github.albertocavalcante.groovylsp.documentation

import org.slf4j.LoggerFactory

/**
 * Extracts documentation from groovydoc/javadoc comments.
 */
object DocExtractor {
    private val logger = LoggerFactory.getLogger(DocExtractor::class.java)

    // Regex to extract @param tags
    private val paramRegex = Regex("""@param\s+(\w+)\s+(.+?)(?=@\w+|$)""", RegexOption.DOT_MATCHES_ALL)

    // Regex to extract @return tag
    private val returnRegex = Regex("""@return\s+(.+?)(?=@\w+|$)""", RegexOption.DOT_MATCHES_ALL)

    // Regex to extract @throws/@exception tags
    private val throwsRegex = Regex("""@(?:throws|exception)\s+(\w+)\s+(.+?)(?=@\w+|$)""", RegexOption.DOT_MATCHES_ALL)

    // Regex to extract @since tag
    private val sinceRegex = Regex("""@since\s+(.+?)(?=@\w+|$)""", RegexOption.DOT_MATCHES_ALL)

    // Regex to extract @author tag
    private val authorRegex = Regex("""@author\s+(.+?)(?=@\w+|$)""", RegexOption.DOT_MATCHES_ALL)

    // Regex to extract @deprecated tag
    private val deprecatedRegex = Regex("""@deprecated\s+(.+?)(?=@\w+|$)""", RegexOption.DOT_MATCHES_ALL)

    // Regex to extract @see tag
    private val seeRegex = Regex("""@see\s+(.+?)(?=@\w+|$)""", RegexOption.DOT_MATCHES_ALL)

    // Regex for whitespace normalization
    private val whitespaceRegex = Regex("""\s+""")

    /**
     * Extract documentation from source code at a specific line.
     * Looks for groovydoc/javadoc comment preceding the given line.
     *
     * @param sourceLines The source code split into lines
     * @param targetLine The line number (1-based) where the documented element starts
     * @return Extracted documentation or empty Documentation if none found
     */
    fun extractDocumentation(sourceLines: List<String>, targetLine: Int): Documentation {
        if (targetLine <= 0 || targetLine > sourceLines.size) {
            return Documentation.EMPTY
        }

        // Look backwards from target line to find doc comment
        val docComment = findDocComment(sourceLines, targetLine) ?: return Documentation.EMPTY

        return parseDocComment(docComment)
    }

    /**
     * Extract documentation from a full source text at a specific line.
     *
     * @param sourceText The complete source code
     * @param targetLine The line number (1-based) where the documented element starts
     * @return Extracted documentation or empty Documentation if none found
     */
    fun extractDocumentation(sourceText: String, targetLine: Int): Documentation {
        val lines = sourceText.lines()
        return extractDocumentation(lines, targetLine)
    }

    /**
     * Find the doc comment preceding a target line.
     * Searches backwards from the target line, skipping blank lines and annotations.
     */
    private fun findDocComment(sourceLines: List<String>, targetLine: Int): String? {
        var currentLine = targetLine // Start from target line (1-based)

        // Skip blank lines and annotations backwards
        while (currentLine > 1) {
            currentLine-- // Move to previous line
            val line = sourceLines[currentLine - 1].trim() // Convert to 0-based for array access
            if (line.isEmpty() || line.startsWith("@")) {
                continue // Keep skipping
            }
            // Found a non-empty, non-annotation line
            break
        }

        // Now currentLine should be pointing to the line just before the target (or annotations)
        // Check if this line ends a doc comment
        if (currentLine > 0) {
            val line = sourceLines[currentLine - 1].trim() // 0-based access
            if (line.endsWith("*/")) {
                // Found end of doc comment, now find the start
                val endLine = currentLine // This is 1-based line number
                var startLine = currentLine

                while (startLine > 0) {
                    val commentLine = sourceLines[startLine - 1].trim() // 0-based access
                    if (commentLine.startsWith("/**")) {
                        // Found the start of the doc comment (startLine and endLine are 1-based)
                        // Include endLine in the subList
                        val docCommentLines = sourceLines.subList(startLine - 1, endLine) // 0-based: [start, end)
                        return docCommentLines.joinToString("\n")
                    }
                    startLine--
                }
            }
        }

        return null
    }

    /**
     * Parse a doc comment string into a Documentation object.
     */
    private fun parseDocComment(docComment: String): Documentation {
        // Remove comment delimiters and asterisks
        val cleanedComment = docComment
            .replace(Regex("""/\*\*"""), "")
            .replace(Regex("""\*/"""), "")
            .lines()
            .joinToString("\n") { line ->
                line.trim().removePrefix("*").trim()
            }
            .trim()

        // Extract summary (first sentence or first paragraph)
        val summaryMatch = cleanedComment.split(Regex("""[.?!]\s+|\n\n""")).firstOrNull()?.trim() ?: ""
        val summary = if (summaryMatch.startsWith("@")) "" else summaryMatch

        // Extract description (everything before first @ tag)
        val descParts = cleanedComment.split(Regex("""(?=@\w+)"""))
        val description = descParts.firstOrNull()?.trim()?.let {
            if (it.startsWith("@")) "" else it
        } ?: ""

        // Extract @param tags
        val params = paramRegex.findAll(cleanedComment).associate { match ->
            val paramName = match.groupValues[1]
            val paramDesc = match.groupValues[2].trim().replace(whitespaceRegex, " ")
            paramName to paramDesc
        }

        // Extract @return tag
        val returnDoc = returnRegex.find(cleanedComment)?.groupValues?.get(1)?.trim()?.replace(whitespaceRegex, " ")
            ?: ""

        // Extract @throws tags
        val throws = throwsRegex.findAll(cleanedComment).associate { match ->
            val exceptionType = match.groupValues[1]
            val exceptionDesc = match.groupValues[2].trim().replace(whitespaceRegex, " ")
            exceptionType to exceptionDesc
        }

        // Extract @since tag
        val since = sinceRegex.find(cleanedComment)?.groupValues?.get(1)?.trim()?.replace(whitespaceRegex, " ") ?: ""

        // Extract @author tag
        val author = authorRegex.find(cleanedComment)?.groupValues?.get(1)?.trim()?.replace(whitespaceRegex, " ")
            ?: ""

        // Extract @deprecated tag
        val deprecated =
            deprecatedRegex.find(cleanedComment)?.groupValues?.get(1)?.trim()?.replace(whitespaceRegex, " ") ?: ""

        // Extract @see tags
        val see = seeRegex.findAll(cleanedComment).map { match ->
            match.groupValues[1].trim().replace(whitespaceRegex, " ")
        }.toList()

        return Documentation(
            summary = summary,
            description = description,
            params = params,
            returnDoc = returnDoc,
            throws = throws,
            since = since,
            author = author,
            deprecated = deprecated,
            see = see,
        )
    }
}
