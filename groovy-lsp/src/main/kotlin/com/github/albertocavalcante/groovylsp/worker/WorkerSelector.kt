package com.github.albertocavalcante.groovylsp.worker

import com.github.albertocavalcante.groovylsp.version.GroovyVersion
import com.github.albertocavalcante.groovylsp.version.GroovyVersionRange

sealed interface WorkerConnector {
    data object InProcess : WorkerConnector
}

data class WorkerDescriptor(
    val id: String,
    val supportedRange: GroovyVersionRange,
    val capabilities: WorkerCapabilities,
    val connector: WorkerConnector,
)

class WorkerSelector(descriptors: List<WorkerDescriptor>) {
    private val workers: List<WorkerDescriptor>

    init {
        val ids = descriptors.map { it.id }
        require(ids.distinct().size == ids.size) { "Worker ids must be unique" }
        workers = descriptors.toList()
    }

    fun select(requestedVersion: GroovyVersion, requiredFeatures: Set<WorkerFeature> = emptySet()): WorkerDescriptor? {
        val candidates = workers.filter { descriptor ->
            descriptor.supportedRange.contains(requestedVersion) &&
                requiredFeatures.all { feature -> feature in descriptor.capabilities.features }
        }

        return candidates.maxWithOrNull(
            compareBy<WorkerDescriptor> {
                it.supportedRange.minInclusive
            }.thenByDescending {
                it.supportedRange.maxInclusive
            }.thenBy {
                it.id
            },
        )
    }
}
