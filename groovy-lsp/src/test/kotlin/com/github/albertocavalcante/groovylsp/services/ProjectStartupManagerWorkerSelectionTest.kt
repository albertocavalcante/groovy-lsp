package com.github.albertocavalcante.groovylsp.services

import com.github.albertocavalcante.groovylsp.compilation.GroovyCompilationService
import com.github.albertocavalcante.groovylsp.version.GroovyVersion
import com.github.albertocavalcante.groovylsp.version.GroovyVersionInfo
import com.github.albertocavalcante.groovylsp.version.GroovyVersionRange
import com.github.albertocavalcante.groovylsp.version.GroovyVersionSource
import com.github.albertocavalcante.groovylsp.worker.WorkerCapabilities
import com.github.albertocavalcante.groovylsp.worker.WorkerConnector
import com.github.albertocavalcante.groovylsp.worker.WorkerDescriptor
import com.github.albertocavalcante.groovylsp.worker.WorkerRouter
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test

class ProjectStartupManagerWorkerSelectionTest {

    @Test
    fun `select worker stores selected worker in compilation service`() {
        val compilationService = mockk<GroovyCompilationService>(relaxed = true)
        val worker = descriptor(
            id = "in-process",
            range = GroovyVersionRange(parseVersion("2.0.0"), parseVersion("4.0.0")),
        )
        val manager = ProjectStartupManager(
            compilationService = compilationService,
            availableBuildTools = emptyList(),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            workerRouter = WorkerRouter(listOf(worker)),
        )

        manager.selectWorker(groovyInfo("3.0.0"))

        verify(exactly = 1) { compilationService.updateSelectedWorker(worker) }
    }

    @Test
    fun `select worker clears selection when no compatible worker`() {
        val compilationService = mockk<GroovyCompilationService>(relaxed = true)
        val worker = descriptor(
            id = "legacy",
            range = GroovyVersionRange(parseVersion("2.0.0"), parseVersion("2.4.0")),
        )
        val manager = ProjectStartupManager(
            compilationService = compilationService,
            availableBuildTools = emptyList(),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            workerRouter = WorkerRouter(listOf(worker)),
        )

        manager.selectWorker(groovyInfo("4.0.0"))

        verify(exactly = 1) { compilationService.updateSelectedWorker(null) }
    }

    private fun groovyInfo(raw: String) = GroovyVersionInfo(parseVersion(raw), GroovyVersionSource.RUNTIME)

    private fun parseVersion(raw: String): GroovyVersion =
        requireNotNull(GroovyVersion.parse(raw)) { "Failed to parse version: $raw" }

    private fun descriptor(id: String, range: GroovyVersionRange): WorkerDescriptor = WorkerDescriptor(
        id = id,
        supportedRange = range,
        capabilities = WorkerCapabilities(),
        connector = WorkerConnector.InProcess,
    )
}
