package com.github.albertocavalcante.groovylsp.buildtool.gradle

import com.github.albertocavalcante.groovylsp.buildtool.BuildTool
import com.github.albertocavalcante.groovylsp.buildtool.WorkspaceResolution
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Gradle build tool that uses the Gradle Tooling API to extract
 * binary JAR dependencies from a project.
 *
 * This is Phase 1 implementation - focuses on getting dependencies
 * on the classpath for compilation. Future phases will add source
 * JAR support and on-demand downloading.
 */
class GradleBuildTool(private val connectionFactory: GradleConnectionFactory = GradleConnectionPool) : BuildTool {

    private val logger = LoggerFactory.getLogger(GradleBuildTool::class.java)
    private val dependencyResolver = GradleDependencyResolver(connectionFactory)

    override val name: String = "Gradle"

    /**
     * Checks if the given directory is a Gradle project.
     */
    override fun canHandle(workspaceRoot: Path): Boolean {
        val gradleFiles = listOf(
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts",
        )

        return gradleFiles.any { fileName ->
            val candidate = workspaceRoot.resolve(fileName)
            val present = candidate.exists()
            logger.debug("Gradle probe: {} present={}", candidate, present)
            present
        }
    }

    /**
     * Resolves all binary JAR dependencies from a Gradle project.
     *
     * @param workspaceRoot The root directory of the Gradle project
     * @param onProgress Optional callback for progress updates (e.g., Gradle distribution download)
     * @return List of paths to dependency JAR files and source directories
     */
    override fun resolve(workspaceRoot: Path, onProgress: ((String) -> Unit)?): WorkspaceResolution {
        if (!canHandle(workspaceRoot)) {
            logger.info("Not a Gradle project: $workspaceRoot")
            return WorkspaceResolution(emptyList(), emptyList())
        }

        logger.info("Resolving Gradle dependencies for: $workspaceRoot")

        // Delegate dependency resolution to the resolver
        val dependencies = dependencyResolver.resolveDependencies(workspaceRoot)

        // Extract source directories from the project
        val sourceDirectories = extractSourceDirectories(workspaceRoot)

        logger.info("Resolved ${dependencies.size} dependencies and ${sourceDirectories.size} source directories")
        return WorkspaceResolution(dependencies, sourceDirectories)
    }

    private fun extractSourceDirectories(workspaceRoot: Path): List<Path> {
        // Standard Gradle layout assumption
        return listOf(
            workspaceRoot.resolve("src/main/java"),
            workspaceRoot.resolve("src/main/groovy"),
            workspaceRoot.resolve("src/main/kotlin"),
            workspaceRoot.resolve("src/test/java"),
            workspaceRoot.resolve("src/test/groovy"),
            workspaceRoot.resolve("src/test/kotlin"),
        ).filter { it.exists() }
    }

    override fun createWatcher(
        coroutineScope: kotlinx.coroutines.CoroutineScope,
        onChange: (java.nio.file.Path) -> Unit,
    ): com.github.albertocavalcante.groovylsp.buildtool.BuildToolFileWatcher =
        GradleBuildFileWatcher(coroutineScope, onChange)
}
