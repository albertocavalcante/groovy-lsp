@file:Suppress(
    "TooGenericExceptionCaught", // Plugin resolution uses catch-all for resilience
    "ReturnCount", // Multiple early returns are clearer in search methods
    "NestedBlockDepth", // Plugin resolution has inherent nesting
)

package com.github.albertocavalcante.groovyjenkins

import com.github.albertocavalcante.groovyjenkins.config.JenkinsPluginConfiguration
import com.github.albertocavalcante.groovyjenkins.metadata.BundledJenkinsMetadata
import com.github.albertocavalcante.groovyjenkins.metadata.BundledJenkinsMetadataLoader
import com.github.albertocavalcante.groovyjenkins.metadata.JenkinsStepMetadata
import com.github.albertocavalcante.groovyjenkins.metadata.MetadataMerger
import com.github.albertocavalcante.groovyjenkins.metadata.StableStepDefinitions
import com.github.albertocavalcante.groovyjenkins.updatecenter.JenkinsUpdateCenterClient
import com.github.albertocavalcante.groovylsp.buildtool.MavenSourceArtifactResolver
import com.github.albertocavalcante.groovylsp.buildtool.SourceArtifactResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Functional interface for a source of Jenkins metadata.
 */
typealias MetadataSource = suspend () -> BundledJenkinsMetadata

/**
 * Central orchestrator for Jenkins plugin metadata resolution.
 *
 * Implements a functional pipeline strategy where sources are folded:
 * 1. **Bundled**: Base fallback (static stubs)
 * 2. **Stable**: Hardcoded core definitions
 * 3. **User Config**: Plugins specified in plugins.txt
 * 4. **Downloaded**: Downloaded JARs
 * 5. **Classpath**: Project dependencies (Highest priority)
 */
@Suppress("UnusedPrivateProperty") // TODO: updateCenterClient and cacheDir reserved for future use
class JenkinsPluginManager(
    private val sourceResolver: SourceArtifactResolver = MavenSourceArtifactResolver(),
    private val metadataExtractor: JenkinsPluginMetadataExtractor = JenkinsPluginMetadataExtractor(),
    private val updateCenterClient: JenkinsUpdateCenterClient = JenkinsUpdateCenterClient(),
    private val pluginConfiguration: JenkinsPluginConfiguration = JenkinsPluginConfiguration(),
    private val cacheDir: Path = MavenSourceArtifactResolver.getDefaultCacheDir().parent.resolve("jenkins-plugins"),
) {

    private val logger = LoggerFactory.getLogger(JenkinsPluginManager::class.java)

    // Thread-safe lazy loading
    private val bundledMetadataLoader = BundledJenkinsMetadataLoader()
    private val bundledMetadataMutex = Mutex()
    private var bundledMetadataCache: BundledJenkinsMetadata? = null

    // Cache for extracted metadata from JARs
    private val extractedMetadataCache = mutableMapOf<String, List<JenkinsStepMetadata>>()
    private val cacheMutex = Mutex()

    // Cache for user-configured plugins (by workspace)
    private val userConfigCache = mutableMapOf<Path, List<JenkinsPluginConfiguration.PluginEntry>>()
    private val userConfigMutex = Mutex()

    // Cache for downloaded plugin JARs
    private val downloadedPluginCache = mutableMapOf<String, Path>()
    private val downloadedPluginMutex = Mutex()

    /**
     * Resolve metadata for a Jenkins step using functional pipeline.
     */
    suspend fun resolveStepMetadata(
        stepName: String,
        classpathJars: List<Path> = emptyList(),
        workspaceRoot: Path? = null,
    ): JenkinsStepMetadata? {
        // Define sources from lowest to highest priority
        val sources: List<MetadataSource> = listOf(
            // 1. Bundled (Lowest)
            { getBundledMetadata() },

            // 2. Stable Definitions
            {
                BundledJenkinsMetadata(
                    steps = StableStepDefinitions.all(),
                    globalVariables = emptyMap(),
                    postConditions = emptyMap(),
                    declarativeOptions = emptyMap(),
                    agentTypes = emptyMap(),
                )
            },

            // 3. User Config & Downloaded (Medium)
            {
                val userSteps = if (workspaceRoot != null) {
                    collectUserConfiguredSteps(workspaceRoot)
                } else {
                    emptyMap()
                }

                // Also check previously downloaded/resolved plugins not explicitly in config
                // This is a bit of a loose heuristic but matches previous logic
                val downloadedSteps = collectDownloadedSteps()

                BundledJenkinsMetadata(
                    steps = downloadedSteps + userSteps, // User config overrides random downloads
                    globalVariables = emptyMap(),
                    postConditions = emptyMap(),
                    declarativeOptions = emptyMap(),
                    agentTypes = emptyMap(),
                )
            },

            // 4. Classpath (Highest)
            {
                val classpathSteps = collectClasspathSteps(classpathJars)
                BundledJenkinsMetadata(
                    steps = classpathSteps,
                    globalVariables = emptyMap(),
                    postConditions = emptyMap(),
                    declarativeOptions = emptyMap(),
                    agentTypes = emptyMap(),
                )
            },
        )

        // Fold sources to create the authoritative view
        // Note: usage of 'fold' here implies we build the COMPLETE metadata view
        // For distinct step resolution, this might be expensive if done on every request.
        // However, for correctness, this is the functional way.
        // Optimization: We could lazy-fetch only the requested step, but MetadataMerger works on full objects.
        // Given the scale, constructing the Maps is relatively cheap compared to I/O which is cached.

        val mergedMetadata = sources.fold(
            BundledJenkinsMetadata(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap()),
        ) { acc, source ->
            try {
                val next = source()
                // Use our Semigroup-based merger
                // Using 'merge' which simplifies to Semigroup combine in underlying logic
                MetadataMerger.merge(acc, next.steps)
                    .copy(
                        // Preserve other fields logic from MetadataMerger if needed,
                        // but MetadataMerger.merge only merges steps into acc.
                        // Let's use the explicit combine if we want full merge.
                        // Actually MetadataMerger.merge (as refactored) calls bundledMetadataSemigroup.combine
                        // So it merges EVERYTHING.
                    )
            } catch (e: Exception) {
                logger.warn("Failed to load metadata source", e)
                acc
            }
        }

        return mergedMetadata.getStep(stepName)
    }

    // --- Helper collection methods (Refactored to return Maps) ---

    private suspend fun collectClasspathSteps(classpathJars: List<Path>): Map<String, JenkinsStepMetadata> {
        val steps = mutableMapOf<String, JenkinsStepMetadata>()
        classpathJars.forEach { jar ->
            extractMetadataFromJar(jar).forEach { step ->
                steps[step.name] = step
            }
        }
        return steps
    }

    private suspend fun collectUserConfiguredSteps(workspaceRoot: Path): Map<String, JenkinsStepMetadata> {
        val steps = mutableMapOf<String, JenkinsStepMetadata>()
        val plugins = loadUserPlugins(workspaceRoot)

        for (plugin in plugins) {
            val coords = plugin.toMavenCoordinates() ?: continue
            val jarPath = resolvePluginJar(coords) ?: continue
            extractMetadataFromJar(jarPath).forEach { step ->
                steps[step.name] = step
            }
        }
        return steps
    }

    private suspend fun collectDownloadedSteps(): Map<String, JenkinsStepMetadata> {
        val steps = mutableMapOf<String, JenkinsStepMetadata>()
        val downloadedJars = downloadedPluginMutex.withLock {
            downloadedPluginCache.values.toList()
        }

        for (jarPath in downloadedJars) {
            if (Files.exists(jarPath)) {
                extractMetadataFromJar(jarPath).forEach { step ->
                    steps[step.name] = step
                }
            }
        }
        return steps
    }

    /**
     * Load user-configured plugins from workspace, with caching.
     */
    private suspend fun loadUserPlugins(workspaceRoot: Path): List<JenkinsPluginConfiguration.PluginEntry> =
        userConfigMutex.withLock {
            userConfigCache.getOrPut(workspaceRoot) {
                pluginConfiguration.loadPluginsFromWorkspace(workspaceRoot)
            }
        }

    /**
     * Resolve a plugin JAR from Maven coordinates.
     */
    private suspend fun resolvePluginJar(coords: JenkinsPluginConfiguration.MavenCoordinates): Path? {
        val key = "${coords.groupId}:${coords.artifactId}:${coords.version}"

        // Check cache first
        val cached = downloadedPluginMutex.withLock { downloadedPluginCache[key] }
        if (cached != null && Files.exists(cached)) {
            return cached
        }

        // Try to resolve via source resolver
        return try {
            val jarPath = sourceResolver.resolveSourceJar(coords.groupId, coords.artifactId, coords.version)
            if (jarPath != null) {
                downloadedPluginMutex.withLock {
                    downloadedPluginCache[key] = jarPath
                }
            }
            jarPath
        } catch (e: Exception) {
            logger.debug("Failed to resolve plugin JAR: {}", key, e)
            null
        }
    }

    /**
     * Get all available step names from all tiers.
     */
    suspend fun getAllKnownSteps(classpathJars: List<Path> = emptyList()): Set<String> {
        // We can reuse resolveStepMetadata logic but optimized for key retrieval?
        // For now, let's keep it simple and explicit as the caller might just want names.

        // 1. Bundled
        val bundled = getBundledMetadata().steps.keys

        // 2. Classpath
        val classpath = collectClasspathSteps(classpathJars).keys

        // 3. Stable
        val stable = StableStepDefinitions.all().keys

        return bundled + classpath + stable
    }

    /**
     * Extract metadata from a JAR, with caching.
     */
    private suspend fun extractMetadataFromJar(jarPath: Path): List<JenkinsStepMetadata> {
        val key = jarPath.toString()

        val cached = cacheMutex.withLock {
            extractedMetadataCache[key]
        }
        if (cached != null) return cached

        // Check if this looks like a Jenkins plugin
        val fileName = jarPath.fileName.toString()
        if (!isLikelyJenkinsPlugin(fileName)) {
            return emptyList()
        }

        val extracted = metadataExtractor.extractFromJar(jarPath, fileName.removeSuffix(".jar"))

        cacheMutex.withLock {
            extractedMetadataCache[key] = extracted
        }

        return extracted
    }

    /**
     * Heuristic to identify likely Jenkins plugins.
     */
    private fun isLikelyJenkinsPlugin(fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        return lowerName.contains("workflow") ||
            lowerName.contains("jenkins") ||
            lowerName.contains("pipeline") ||
            lowerName.contains("plugin") ||
            lowerName.contains("credentials") ||
            lowerName.contains("scm")
    }

    /**
     * Get bundled metadata, loading lazily if needed.
     */
    private suspend fun getBundledMetadata(): BundledJenkinsMetadata = bundledMetadataMutex.withLock {
        if (bundledMetadataCache == null) {
            bundledMetadataCache = try {
                bundledMetadataLoader.load()
            } catch (e: Exception) {
                logger.error("Failed to load bundled Jenkins metadata", e)
                // Return empty metadata on failure
                BundledJenkinsMetadata(emptyMap(), emptyMap())
            }
        }
        bundledMetadataCache!!
    }

    /**
     * Force refresh of cached metadata.
     */
    suspend fun invalidateCache() {
        cacheMutex.withLock {
            extractedMetadataCache.clear()
        }
        bundledMetadataMutex.withLock {
            bundledMetadataCache = null
        }
        logger.info("Invalidated Jenkins plugin manager cache")
    }
}
