package com.github.albertocavalcante.groovylsp.buildtool.gradle

import com.github.albertocavalcante.groovylsp.buildtool.DependencyResolver
import com.github.albertocavalcante.groovylsp.buildtool.WorkspaceResolution
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * Programmatic Gradle dependency resolver using the Gradle Tooling API.
 *
 * This implements:
 * - In-process dependency resolution (no subprocess)
 * - Automatic retry with isolated Gradle user home on init script failures
 * - Exponential backoff retry for transient failures (e.g., lock timeouts)
 * - Source directory extraction from IdeaProject model
 */
class GradleDependencyResolver(private val connectionFactory: GradleConnectionFactory = GradleConnectionPool) :
    DependencyResolver {

    /**
     * Configuration for retry behavior on transient failures.
     */
    private object RetryConfig {
        const val MAX_ATTEMPTS = 3
        const val INITIAL_DELAY_MS = 2000L
        const val BACKOFF_MULTIPLIER = 2.0
    }

    private val logger = LoggerFactory.getLogger(GradleDependencyResolver::class.java)

    override val name: String = "Gradle Tooling API"

    /**
     * Resolves all binary JAR dependencies from a Gradle project.
     *
     * @param projectFile Path to the project directory (build.gradle location)
     * @return List of paths to dependency JAR files
     */
    override fun resolveDependencies(projectFile: Path): List<Path> =
        resolveWithSourceDirectories(projectFile).dependencies

    /**
     * Resolves both dependencies and source directories from a Gradle project.
     * This extracts source directories from the IdeaProject model, supporting
     * custom source directory layouts.
     *
     * Implements resilient resolution with:
     * - Exponential backoff retry for transient failures (e.g., lock timeouts)
     * - Isolated Gradle user home fallback for init script errors
     *
     * @param projectFile Path to the project directory (build.gradle location)
     * @return WorkspaceResolution containing both dependencies and source directories
     */
    fun resolveWithSourceDirectories(projectFile: Path): WorkspaceResolution {
        val projectDir = if (Files.isDirectory(projectFile)) projectFile else projectFile.parent

        logger.info("Resolving dependencies using Gradle Tooling API for: $projectDir")

        var lastException: Throwable? = null
        var currentDelay = RetryConfig.INITIAL_DELAY_MS

        // Attempt resolution with exponential backoff for transient failures
        for (attempt in 1..RetryConfig.MAX_ATTEMPTS) {
            val result = runCatching {
                resolveWithGradleUserHome(projectDir, gradleUserHomeDir = null)
            }

            if (result.isSuccess) {
                if (attempt > 1) {
                    logger.info("Gradle dependency resolution succeeded on attempt $attempt")
                }
                return result.getOrThrow()
            }

            lastException = result.exceptionOrNull()

            // Check if this is a transient failure worth retrying
            if (lastException != null && isTransientFailure(lastException)) {
                if (attempt < RetryConfig.MAX_ATTEMPTS) {
                    logger.warn(
                        "Gradle dependency resolution failed (attempt $attempt/${RetryConfig.MAX_ATTEMPTS}): " +
                            "${lastException.message}. Retrying in ${currentDelay}ms...",
                    )
                    Thread.sleep(currentDelay)
                    currentDelay = (currentDelay * RetryConfig.BACKOFF_MULTIPLIER).toLong()
                    continue
                }
            }

            // Not a transient failure or we've exhausted retries - try isolated user home for init script errors
            if (lastException != null && shouldRetryWithIsolatedGradleUserHome(lastException)) {
                logger.warn(
                    "Gradle dependency resolution failed; retrying with isolated Gradle user home " +
                        "to avoid incompatible user init scripts",
                )

                val isolatedUserHome = isolatedGradleUserHomeDir()
                val retryAttempt = runCatching {
                    resolveWithGradleUserHome(projectDir, isolatedUserHome.toFile())
                }

                if (retryAttempt.isSuccess) {
                    return retryAttempt.getOrThrow()
                }

                lastException = retryAttempt.exceptionOrNull() ?: lastException
            }

            break
        }

        logger.error("Gradle dependency resolution failed: ${lastException?.message}", lastException)
        return WorkspaceResolution(emptyList(), emptyList())
    }

    /**
     * Returns the Gradle user home directory path.
     */
    override fun resolveLocalRepository(): Path? {
        val gradleUserHome = System.getenv("GRADLE_USER_HOME")
        if (!gradleUserHome.isNullOrBlank()) {
            val path = Paths.get(gradleUserHome)
            if (Files.exists(path)) {
                return path
            }
        }

        val userHome = System.getProperty("user.home")
        return Paths.get(userHome, ".gradle")
    }

    private fun resolveWithGradleUserHome(projectDir: Path, gradleUserHomeDir: File?): WorkspaceResolution {
        val connection = connectionFactory.getConnection(projectDir, gradleUserHomeDir)

        val modelBuilder = connection.model(IdeaProject::class.java)
            .withArguments(
                "-Dorg.gradle.daemon=true",
                "-Dorg.gradle.parallel=true",
                "-Dorg.gradle.configureondemand=true",
                "-Dorg.gradle.vfs.watch=true",
            )
            .setJvmArguments("-Xmx1g", "-XX:+UseG1GC")

        val ideaProject = modelBuilder.get()

        val dependencies = mutableSetOf<Path>()
        val sourceDirectories = mutableSetOf<Path>()

        ideaProject.modules.forEach { module ->
            processModule(module, dependencies, sourceDirectories)
        }

        logger.info(
            "Resolved ${dependencies.size} dependencies and ${sourceDirectories.size} source directories via Gradle Tooling API",
        )
        return WorkspaceResolution(dependencies.toList(), sourceDirectories.toList())
    }

    private fun shouldRetryWithIsolatedGradleUserHome(error: Throwable): Boolean {
        val allMessages = error.causeChain()
            .mapNotNull { it.message }
            .joinToString("\n")

        return allMessages.contains("init.d") ||
            allMessages.contains("init script") ||
            allMessages.contains("cp_init") ||
            allMessages.contains("Unsupported class file major version")
    }

    /**
     * Detects transient failures that are worth retrying with exponential backoff.
     * These are typically caused by external processes holding locks temporarily.
     */
    private fun isTransientFailure(error: Throwable): Boolean {
        val allMessages = error.causeChain()
            .mapNotNull { it.message }
            .joinToString("\n")

        val allClassNames = error.causeChain()
            .map { it.javaClass.simpleName }
            .joinToString("\n")

        // Lock timeout - another Gradle process holds a lock
        if (allClassNames.contains("LockTimeoutException") ||
            allMessages.contains("Timeout waiting to lock") ||
            allMessages.contains("currently in use by another process")
        ) {
            return true
        }

        // Connection refused - Gradle daemon not ready yet
        if (allMessages.contains("Connection refused") ||
            allMessages.contains("Could not connect to the Gradle daemon")
        ) {
            return true
        }

        return false
    }

    private fun isolatedGradleUserHomeDir(): Path {
        val userHome = System.getProperty("user.home")
        val base = if (!userHome.isNullOrBlank()) {
            Paths.get(userHome)
        } else {
            Paths.get(System.getProperty("java.io.tmpdir"))
        }

        val dir = base.resolve(".groovy-lsp").resolve("gradle-user-home")
        return runCatching { Files.createDirectories(dir) }
            .getOrElse { e ->
                logger.error("Failed to create isolated Gradle user home dir at $dir; falling back to temp dir", e)
                val tempDir = Paths.get(System.getProperty("java.io.tmpdir"))
                    .resolve("groovy-lsp-gradle-user-home")
                Files.createDirectories(tempDir)
                tempDir
            }
    }

    private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

    private fun processModule(
        module: IdeaModule,
        dependencies: MutableSet<Path>,
        sourceDirectories: MutableSet<Path>,
    ) {
        logger.debug("Processing module: ${module.name}")

        // Extract dependencies
        module.dependencies
            .filterIsInstance<IdeaSingleEntryLibraryDependency>()
            .forEach { dependency ->
                val jarPath = dependency.file.toPath()
                if (jarPath.exists()) {
                    logger.debug("Found dependency: ${dependency.file.name}")
                    dependencies.add(jarPath)
                } else {
                    logger.warn("Dependency JAR not found: $jarPath")
                }
            }

        // Extract source directories from IdeaProject model
        module.contentRoots?.forEach { root ->
            root.sourceDirectories?.forEach { dir ->
                dir.directory?.toPath()?.takeIf { it.exists() }?.let(sourceDirectories::add)
            }
            root.testDirectories?.forEach { dir ->
                dir.directory?.toPath()?.takeIf { it.exists() }?.let(sourceDirectories::add)
            }
        }
    }
}
