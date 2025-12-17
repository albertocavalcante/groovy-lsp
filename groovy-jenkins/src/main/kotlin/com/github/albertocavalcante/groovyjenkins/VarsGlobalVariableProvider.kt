package com.github.albertocavalcante.groovyjenkins

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

data class GlobalVariable(val name: String, val path: Path, val documentation: String = "")

class VarsGlobalVariableProvider(private val workspaceRoot: Path) {
    private val logger = LoggerFactory.getLogger(VarsGlobalVariableProvider::class.java)

    fun getGlobalVariables(): List<GlobalVariable> {
        val varsDir = workspaceRoot.resolve("vars")
        if (!Files.exists(varsDir) || !Files.isDirectory(varsDir)) {
            return emptyList()
        }

        return try {
            Files.list(varsDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".groovy") }
                    .map { path ->
                        val name = path.nameWithoutExtension
                        val documentation = readDocumentation(varsDir, name)
                        GlobalVariable(name, path, documentation)
                    }
                    .toList()
            }
        } catch (e: Exception) {
            logger.error("Failed to scan vars directory", e)
            emptyList()
        }
    }

    /**
     * Read documentation from companion .txt file if it exists.
     */
    private fun readDocumentation(varsDir: Path, varName: String): String {
        val txtFile = varsDir.resolve("$varName.txt")
        if (!Files.exists(txtFile)) {
            return ""
        }

        return try {
            val htmlContent = Files.readString(txtFile)
            convertHtmlToMarkdown(htmlContent)
        } catch (e: Exception) {
            logger.warn("Failed to read documentation for $varName: ${e.message}")
            ""
        }
    }

    /**
     * Convert HTML documentation to markdown.
     *
     * Handles common Jenkins documentation patterns:
     * - <p>...</p> paragraphs
     * - <b>...</b> bold text
     * - <code>...</code> code spans
     * - <!-- ... --> HTML comments (stripped)
     */
    internal fun convertHtmlToMarkdown(html: String): String {
        var result = html

        // Strip HTML comments
        result = result.replace(Regex("<!--[\\s\\S]*?-->"), "")

        // Convert <b> and <strong> to markdown bold
        result = result.replace(Regex("<b>([^<]*)</b>"), "**$1**")
        result = result.replace(Regex("<strong>([^<]*)</strong>"), "**$1**")

        // Convert <code> to markdown code
        result = result.replace(Regex("<code>([^<]*)</code>"), "`$1`")

        // Convert <pre> blocks
        result = result.replace(Regex("<pre>([^<]*)</pre>")) { match ->
            "\n```\n${match.groupValues[1]}\n```\n"
        }

        // Convert <a href="...">text</a> to markdown links
        result = result.replace(Regex("<a\\s+href=\"([^\"]+)\">([^<]*)</a>"), "[$2]($1)")

        // Strip paragraph tags but preserve line breaks
        result = result.replace(Regex("<p>\\s*"), "\n")
        result = result.replace(Regex("\\s*</p>"), "\n")

        // Strip remaining HTML tags
        result = result.replace(Regex("<[^>]+>"), "")

        // Clean up whitespace
        result = result.replace(Regex("\n\\s*\n\\s*\n+"), "\n\n")
        result = result.trim()

        return result
    }
}
