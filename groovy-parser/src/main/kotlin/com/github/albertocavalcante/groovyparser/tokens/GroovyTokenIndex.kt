package com.github.albertocavalcante.groovyparser.tokens

import arrow.core.Option
import arrow.core.none
import arrow.core.some

/**
 * Token classification at a given offset.
 */
sealed interface TokenContext {
    data object Code : TokenContext
    data object LineComment : TokenContext
    data object BlockComment : TokenContext
    data object StringLiteral : TokenContext
    data object GString : TokenContext
}

/**
 * Efficient token index for cursor context queries.
 */
class GroovyTokenIndex private constructor(private val spans: List<TokenSpan>) {
    data class TokenSpan(val start: Int, val end: Int, val context: TokenContext)

    /** Query token context at offset. */
    fun contextAt(offset: Int): Option<TokenContext> {
        if (spans.isEmpty()) return none()

        val idx = spans.binarySearchBy(offset) { it.start }
        val insertionPoint = if (idx >= 0) idx else -(idx + 1) - 1

        if (insertionPoint < 0 || insertionPoint >= spans.size) return none()

        val span = spans[insertionPoint]
        return if (offset in span.start until span.end) span.context.some() else none()
    }

    fun isInComment(offset: Int): Boolean =
        contextAt(offset).fold({ false }) { it is TokenContext.LineComment || it is TokenContext.BlockComment }

    fun isInString(offset: Int): Boolean =
        contextAt(offset).fold({ false }) { it is TokenContext.StringLiteral || it is TokenContext.GString }

    fun isInCommentOrString(offset: Int): Boolean = isInComment(offset) || isInString(offset)

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(GroovyTokenIndex::class.java)

        /** Build index from source using Groovy lexer */
        fun build(source: String): GroovyTokenIndex {
            val lexer = org.apache.groovy.parser.antlr4.GroovyLangLexer(
                groovyjarjarantlr4.v4.runtime.CharStreams.fromString(source),
            )

            val spans = mutableListOf<TokenSpan>()

            // Handle shebang manually if lexer skips it
            if (source.startsWith("#!")) {
                val firstNewLine = source.indexOf('\n')
                val end = if (firstNewLine != -1) firstNewLine else source.length
                spans.add(TokenSpan(0, end, TokenContext.LineComment))
            }

            try {
                while (true) {
                    val token = try {
                        lexer.nextToken()
                    } catch (e: Throwable) {
                        logger.debug("Lexer error at offset {}: {}", spans.lastOrNull()?.end ?: 0, e.message)
                        break // Stop if lexer is truly broken or throws Error
                    }

                    if (token.type == groovyjarjarantlr4.v4.runtime.Token.EOF) break

                    val type = token.type
                    val symbolicName = lexer.vocabulary.getSymbolicName(type) ?: ""
                    val text = token.text ?: ""

                    val context = when {
                        symbolicName in setOf("SINGLE_LINE_COMMENT", "SH_COMMENT") ||
                            (symbolicName == "NL" && (text.startsWith("//") || text.startsWith("#"))) ->
                            TokenContext.LineComment

                        symbolicName == "DELIMITED_COMMENT" ||
                            (symbolicName == "NL" && text.startsWith("/*")) ->
                            TokenContext.BlockComment

                        symbolicName in setOf("StringLiteral", "SQ_STRING", "DQ_STRING", "TQS", "StringLiteralPart") ->
                            TokenContext.StringLiteral

                        symbolicName.startsWith("GString") ||
                            symbolicName.contains("GSTRING_MODE") ->
                            TokenContext.GString

                        else -> TokenContext.Code
                    }

                    if (context != TokenContext.Code) {
                        // Avoid duplicates if we manually handled shebang
                        if (token.startIndex == 0 && spans.isNotEmpty() && spans[0].start == 0) {
                            // Already handled shebang
                        } else {
                            spans.add(TokenSpan(token.startIndex, token.stopIndex + 1, context))
                        }
                    }
                }
            } catch (e: Throwable) {
                // Ignore lexer errors during indexing to keep LSP resilient but log
                logger.debug("Ignoring throwable during token indexing for resiliency", e)
            }

            return GroovyTokenIndex(spans.sortedBy { it.start })
        }
    }
}
