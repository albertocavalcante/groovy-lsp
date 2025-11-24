package com.github.albertocavalcante.groovylsp.jenkins

import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Path

/**
 * Manages Jenkins workspace context separately from regular Groovy sources.
 * Provides Jenkins-specific classpath and prevents symbol leakage.
 */
class JenkinsWorkspaceManager(private val configuration: JenkinsConfiguration, private val workspaceRoot: Path) {
    private val logger = LoggerFactory.getLogger(JenkinsWorkspaceManager::class.java)
    private val jenkinsContext = JenkinsContext(configuration, workspaceRoot)

    /**
     * Checks if the given URI represents a Jenkins pipeline file.
     */
    fun isJenkinsFile(uri: URI): Boolean = jenkinsContext.isJenkinsFile(uri)

    /**
     * Gets the classpath for a specific file.
     * Returns Jenkins classpath if it's a Jenkinsfile, empty otherwise.
     */
    fun getClasspathForFile(uri: URI, content: String): List<Path> {
        if (!isJenkinsFile(uri)) {
            return emptyList()
        }

        // Parse library references from the Jenkinsfile
        val libraries = jenkinsContext.parseLibraries(content)

        // Build classpath from referenced libraries
        val classpath = jenkinsContext.buildClasspath(libraries)

        logger.debug("Built Jenkins classpath for $uri: ${classpath.size} entries")
        return classpath
    }

    /**
     * Loads GDSL metadata for Jenkins context.
     */
    fun loadGdslMetadata(): GdslLoadResults = jenkinsContext.loadGdslMetadata()

    /**
     * Updates configuration and rebuilds Jenkins context.
     */
    fun updateConfiguration(newConfig: JenkinsConfiguration): JenkinsWorkspaceManager {
        logger.info("Updating Jenkins configuration")
        return JenkinsWorkspaceManager(newConfig, workspaceRoot)
    }
}
