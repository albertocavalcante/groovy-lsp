package com.github.albertocavalcante.groovyjenkins.plugins

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Discovers installed Jenkins plugins using layered resolution.
 *
 * Resolution order (later sources can override versions):
 * 1. Built-in defaults (workflow-basic-steps, etc.)
 * 2. LSP client configuration (jenkins.plugins)
 * 3. plugins.txt file (auto-discovered or explicit path)
 *
 * HEURISTIC: Default plugins are always included (unless disabled) to provide
 * good out-of-the-box experience. Users can customize via configuration.
 */
class PluginDiscoveryService(
    private val workspaceRoot: Path,
    private val config: PluginConfiguration = PluginConfiguration(),
) {
    private val logger = LoggerFactory.getLogger(PluginDiscoveryService::class.java)

    data class InstalledPlugin(val shortName: String, val version: String? = null, val jarPath: Path? = null)

    /**
     * Discover all installed plugins using layered resolution.
     *
     * @return Merged list of plugins from all sources
     */
    fun discoverPlugins(): List<InstalledPlugin> {
        val pluginMap = mutableMapOf<String, InstalledPlugin>()

        // Layer 1: Default plugins (lowest priority)
        if (config.includeDefaultPlugins) {
            PluginConfiguration.DEFAULT_PLUGINS.forEach { name ->
                pluginMap[name] = InstalledPlugin(shortName = name)
            }
            logger.debug("Added {} default plugins", PluginConfiguration.DEFAULT_PLUGINS.size)
        }

        // Layer 2: Config plugins (can override defaults)
        config.plugins.forEach { spec ->
            parsePluginSpec(spec)?.let { plugin ->
                pluginMap[plugin.shortName] = plugin
            }
        }
        if (config.plugins.isNotEmpty()) {
            logger.debug("Added {} config plugins", config.plugins.size)
        }

        // Layer 3: plugins.txt (highest priority, can override all)
        val pluginsTxt = findPluginsTxt()
        if (pluginsTxt != null) {
            parsePluginsTxt(pluginsTxt).forEach { plugin ->
                pluginMap[plugin.shortName] = plugin
            }
        }

        return pluginMap.values.toList()
    }

    /**
     * Check if any explicit plugin configuration exists.
     */
    fun hasPluginConfiguration(): Boolean = config.plugins.isNotEmpty() || findPluginsTxt() != null

    /**
     * Get the set of plugin short names for filtering.
     */
    fun getInstalledPluginNames(): Set<String> = discoverPlugins().map { it.shortName }.toSet()

    /**
     * Find plugins.txt using configured path or auto-discovery.
     */
    private fun findPluginsTxt(): Path? {
        // Priority 1: Explicit configuration
        config.pluginsTxtPath?.let { configuredPath ->
            val path = if (Paths.get(configuredPath).isAbsolute) {
                Paths.get(configuredPath)
            } else {
                workspaceRoot.resolve(configuredPath)
            }
            if (Files.exists(path) && Files.isRegularFile(path)) {
                logger.debug("Using configured plugins.txt path: {}", path)
                return path
            }
            logger.warn("Configured plugins.txt not found: {}", path)
            return null
        }

        // Priority 2: Auto-discovery in standard locations
        return PLUGINS_TXT_CANDIDATES
            .asSequence()
            .map { workspaceRoot.resolve(it) }
            .firstOrNull { Files.exists(it) && Files.isRegularFile(it) }
            ?.also { logger.debug("Auto-discovered plugins.txt: {}", it) }
    }

    /**
     * Parse plugins.txt file.
     */
    internal fun parsePluginsTxt(path: Path): List<InstalledPlugin> = try {
        Files.readAllLines(path)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { parsePluginSpec(it) }
            .toList()
            .also { logger.info("Parsed {} plugins from {}", it.size, path) }
    } catch (e: Exception) {
        logger.warn("Failed to parse plugins.txt at {}: {}", path, e.message)
        emptyList()
    }

    /**
     * Parse a plugin specification string.
     *
     * Supported formats:
     * - "plugin-name" (version unknown)
     * - "plugin-name:version"
     * - "plugin-name:version:url" (extra fields ignored)
     */
    private fun parsePluginSpec(spec: String): InstalledPlugin? {
        val parts = spec.split(":")
        val shortName = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val version = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

        return InstalledPlugin(shortName = shortName, version = version)
    }

    companion object {
        private val PLUGINS_TXT_CANDIDATES = listOf(
            "plugins.txt",
            "jenkins/plugins.txt",
            ".jenkins/plugins.txt",
            "docker/plugins.txt",
            "jenkins-config/plugins.txt",
            "config/plugins.txt",
        )
    }
}
