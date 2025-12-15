package com.github.albertocavalcante.groovylsp.buildtool

import java.nio.file.Path

/**
 * interface representing a build tool (e.g. Gradle, Maven) capable of resolving dependencies.
 */
interface BuildTool {
    val name: String

    /**
     * Checks if this build tool can handle the given workspace.
     * @param workspaceRoot The root directory of the workspace.
     * @return true if this build tool detects a valid project it can manage.
     */
    fun canHandle(workspaceRoot: Path): Boolean

    /**
     * Resolves dependencies for the workspace.
     * @param workspaceRoot The root directory of the workspace.
     * @param onProgress Optional callback for progress updates.
     * @return The resolved workspace dependencies and source directories.
     */
    fun resolve(workspaceRoot: Path, onProgress: ((String) -> Unit)? = null): WorkspaceResolution
}
