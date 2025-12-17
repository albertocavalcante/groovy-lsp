package com.github.albertocavalcante.diagnostics.codenarc

import org.codenarc.CodeNarcRunner
import org.codenarc.analyzer.FilesSourceAnalyzer
import org.codenarc.results.Results
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * Interface for executing CodeNarc analysis on Groovy source files.
 * This provides a clean abstraction over the CodeNarc execution engine.
 */
interface CodeAnalyzer {
    /**
     * Analyzes the given source code with the specified ruleset.
     *
     * @param sourceCode The Groovy source code to analyze
     * @param fileName The name of the file being analyzed (for reporting)
     * @param rulesetContent The CodeNarc ruleset content (Groovy DSL)
     * @param propertiesFile Optional path to CodeNarc properties file
     * @return The analysis results
     */
    fun analyze(sourceCode: String, fileName: String, rulesetContent: String, propertiesFile: String? = null): Results
}

/**
 * Default implementation of CodeAnalyzer using CodeNarc's runner.
 */
@Suppress("TooGenericExceptionCaught") // CodeNarc interop layer handles all analysis errors
class DefaultCodeAnalyzer : CodeAnalyzer {

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultCodeAnalyzer::class.java)

        // Base temp directory
        private val baseTempDir: Path = Files.createTempDirectory("codenarc-lsp-")
            .also { it.toFile().deleteOnExit() }

        private val rulesetCacheDir: Path = Files.createDirectory(baseTempDir.resolve("rulesets"))
            .also { it.toFile().deleteOnExit() }

        private val cachedRulesetFilesByHash = ConcurrentHashMap<String, Path>()
        private val rulesetFileCache = RulesetFileCache(
            cacheDir = rulesetCacheDir,
            cachedFilesByHash = cachedRulesetFilesByHash,
        )
    }

    override fun analyze(
        sourceCode: String,
        fileName: String,
        rulesetContent: String,
        propertiesFile: String?,
    ): Results {
        logger.debug("Starting CodeNarc analysis for file: $fileName")

        // Create a unique temp directory for this analysis to avoid collisions
        // and allow using the real filename.
        val analysisId = UUID.randomUUID().toString()
        val analysisDir = Files.createDirectory(baseTempDir.resolve(analysisId))

        // Use the actual filename if possible, or a safe default.
        val safeFileName = try {
            Paths.get(fileName).fileName?.toString() ?: "script.groovy"
        } catch (_: Exception) {
            "script.groovy"
        }
        val tempSourceFile = analysisDir.resolve(safeFileName)

        val rulesetFile = rulesetFileCache.getOrCreate(rulesetContent)

        return try {
            // Efficient write with NIO - direct UTF-8 bytes
            Files.write(tempSourceFile, sourceCode.toByteArray(StandardCharsets.UTF_8))

            val runner = CodeNarcRunner().apply {
                // FilesSourceAnalyzer produces FileResults (supports getChildren())
                sourceAnalyzer = FilesSourceAnalyzer().also { analyzer ->
                    // Use reflection to set properties since Kotlin can't access Groovy property setters directly
                    analyzer.javaClass.getDeclaredField("baseDirectory").apply {
                        isAccessible = true
                        set(analyzer, analysisDir.toString())
                    }
                    analyzer.javaClass.getDeclaredField("sourceFiles").apply {
                        isAccessible = true
                        set(analyzer, arrayOf(tempSourceFile.fileName.toString()))
                    }
                }

                // Set the ruleset file
                ruleSetFiles = rulesetFile.toUri().toString()

                // Set properties file if provided
                propertiesFile?.let { propFile ->
                    propertiesFilename = propFile
                    logger.debug("Using CodeNarc properties file: $propFile")
                }
            }

            // Execute analysis on the source code
            val results: Results
            val executeMs = measureTimeMillis {
                results = runner.execute()
            }

            logger.debug(
                "CodeNarc analysis completed for $fileName: ${results.getTotalNumberOfFiles(true)} files, " +
                    "${results.getNumberOfViolationsWithPriority(1, true)} p1 violations, " +
                    "execute=${executeMs}ms",
            )

            results
        } catch (e: Exception) {
            logger.error("Failed to execute CodeNarc analysis for $fileName", e)
            throw CodeAnalysisException("CodeNarc analysis failed for $fileName", e)
        } finally {
            // Synchronous cleanup to prevent resource leaks
            try {
                analysisDir.toFile().deleteRecursively()
            } catch (e: Exception) {
                logger.debug("Temp file cleanup failed for $fileName: ${e.message}")
            }
        }
    }
}

internal class RulesetFileCache(
    private val cacheDir: Path,
    private val cachedFilesByHash: ConcurrentHashMap<String, Path> = ConcurrentHashMap(),
) {
    fun getOrCreate(rulesetContent: String): Path {
        // NOTE: This cache trades a small amount of disk space for significantly faster repeated analysis
        // by avoiding rewriting identical ruleset content for every CodeNarc run.
        // TODO: Replace file-based ruleset loading with a direct in-memory rule set configuration, if supported
        // by CodeNarc, to eliminate temp file IO entirely.
        val hash = sha256Hex(rulesetContent)
        return cachedFilesByHash.computeIfAbsent(hash) {
            val cachedRulesetPath = cacheDir.resolve("$hash.groovy")
            if (Files.exists(cachedRulesetPath)) {
                return@computeIfAbsent cachedRulesetPath
            }

            val tmpPath = cacheDir.resolve("$hash.${UUID.randomUUID()}.tmp")
            Files.write(tmpPath, rulesetContent.toByteArray(StandardCharsets.UTF_8))

            try {
                Files.move(tmpPath, cachedRulesetPath, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                // NOTE: Some filesystems don't support ATOMIC_MOVE; fall back to a best-effort replace.
                // TODO: Use a filesystem capability check (or single-writer lock) to avoid relying on exceptions.
                try {
                    Files.move(tmpPath, cachedRulesetPath, StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    Files.deleteIfExists(tmpPath)
                }
            }

            cachedRulesetPath
        }
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                append(((byte.toInt() shr 4) and 0xF).toString(16))
                append((byte.toInt() and 0xF).toString(16))
            }
        }
    }
}

/**
 * Exception thrown when CodeNarc analysis fails.
 */
class CodeAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)
