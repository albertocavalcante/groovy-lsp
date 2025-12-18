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
        /** Build index from source using Groovy lexer */
        fun build(source: String): GroovyTokenIndex {
            val lexer = org.apache.groovy.parser.antlr4.GroovyLangLexer(
                groovyjarjarantlr4.v4.runtime.CharStreams.fromString(source),
            )

            val spans = mutableListOf<TokenSpan>()

            try {
                while (true) {
                    val token = try {
                        lexer.nextToken()
                    } catch (e: Throwable) {
                        break // Stop if lexer is truly broken or throws Error
                    }

                    if (token.type == groovyjarjarantlr4.v4.runtime.Token.EOF) break

                    val type = token.type
                    val symbolicName = lexer.vocabulary.getSymbolicName(type) ?: ""

                    val context = when {
                        symbolicName == "SINGLE_LINE_COMMENT" ||
                            (symbolicName == "NL" && token.text.startsWith("//")) -> TokenContext.LineComment

                        symbolicName == "DELIMITED_COMMENT" ||
                            (symbolicName == "NL" && token.text.startsWith("/*")) -> TokenContext.BlockComment

                        symbolicName == "SH_COMMENT" ||
                            (symbolicName == "NL" && token.text.startsWith("#")) -> TokenContext.LineComment

                        symbolicName == "StringLiteral" ||
                            symbolicName == "SQ_STRING" ||
                            symbolicName == "DQ_STRING" ||
                            symbolicName == "TQS" ||
                            symbolicName == "StringLiteralPart" -> TokenContext.StringLiteral

                        symbolicName.startsWith("GString") ||
                            symbolicName.contains("GSTRING_MODE") -> TokenContext.GString

                        else -> TokenContext.Code
                    }

                    if (context != TokenContext.Code) {
                        spans.add(TokenSpan(token.startIndex, token.stopIndex + 1, context))
                    }
                }
            } catch (e: Throwable) {
                // Ignore lexer errors during indexing to keep LSP resilient
            }

            return GroovyTokenIndex(spans.sortedBy { it.start })
        }
    }
}
