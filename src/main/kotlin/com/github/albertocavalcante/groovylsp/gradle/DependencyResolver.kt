package com.github.albertocavalcante.groovylsp.gradle

import java.nio.file.Path

/**
 * Provides classpath dependencies for a workspace.
 * Different build systems (Gradle, Maven, Bazel) can supply their own implementations.
 */
fun interface DependencyResolver {
    /**
     * Resolves binary dependencies for the given project directory.
     *
     * @param projectDir root directory of the project
     * @return list of resolved dependency jar paths
     */
    fun resolveDependencies(projectDir: Path): List<Path>
}
