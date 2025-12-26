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
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Worker ids must be unique; duplicates: ${duplicates.sorted()}" }
        workers = descriptors.toList()
    }

    fun select(requestedVersion: GroovyVersion, requiredFeatures: Set<WorkerFeature> = emptySet()): WorkerDescriptor? {
        val candidates = workers.filter { descriptor ->
            descriptor.supportedRange.contains(requestedVersion) &&
                descriptor.capabilities.features.containsAll(requiredFeatures)
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
