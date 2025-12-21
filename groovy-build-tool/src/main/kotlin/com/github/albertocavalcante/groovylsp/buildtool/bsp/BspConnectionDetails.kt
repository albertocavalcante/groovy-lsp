package com.github.albertocavalcante.groovylsp.buildtool.bsp

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.toList

/**
 * BSP connection details parsed from .bsp directory JSON files.
 * BSP servers advertise themselves via JSON files in the .bsp directory.
 */
data class BspConnectionDetails(
    val name: String,
    val version: String,
    val bspVersion: String,
    val languages: List<String>,
    val argv: List<String>,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(BspConnectionDetails::class.java)

        fun findConnectionFiles(workspaceRoot: Path): List<Path> {
            val bspDir = workspaceRoot.resolve(".bsp")
            if (!bspDir.exists()) return emptyList()

            return runCatching {
                Files.list(bspDir).use { stream ->
                    stream.filter { it.isRegularFile() && it.extension == "json" }.toList()
                }
            }.onFailure {
                logger.warn("Failed to list .bsp directory: ${it.message}")
            }.getOrDefault(emptyList())
        }

        fun findFirst(workspaceRoot: Path): BspConnectionDetails? =
            findConnectionFiles(workspaceRoot).firstNotNullOfOrNull { file ->
                parse(file)?.also {
                    logger.info("Found BSP server '${it.name}' v${it.version} from ${file.name}")
                }
            }

        fun parse(file: Path): BspConnectionDetails? {
            if (!file.exists()) return null
            return runCatching {
                parseJson(file.readText())
            }.onFailure {
                logger.warn("Failed to parse BSP connection file ${file.name}: ${it.message}")
            }.getOrNull()
        }

        internal fun parseJson(json: String): BspConnectionDetails? {
            val name = extractString(json, "name") ?: return null
            val version = extractString(json, "version") ?: return null
            val bspVersion = extractString(json, "bspVersion") ?: return null
            val languages = extractArray(json, "languages") ?: emptyList()
            val argv = extractArray(json, "argv") ?: return null

            if (argv.isEmpty()) {
                logger.warn("BSP connection file has empty argv")
                return null
            }

            return BspConnectionDetails(name, version, bspVersion, languages, argv)
        }

        private fun extractString(json: String, field: String): String? {
            val pattern = "\"$field\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            return pattern.find(json)?.groupValues?.get(1)
        }

        private fun extractArray(json: String, field: String): List<String>? {
            val pattern = "\"$field\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
            val match = pattern.find(json) ?: return null
            val content = match.groupValues[1]
            if (content.isBlank()) return emptyList()
            return "\"([^\"]+)\"".toRegex().findAll(content).map { it.groupValues[1] }.toList()
        }
    }
}
