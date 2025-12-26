package com.github.albertocavalcante.groovylsp.worker

import com.github.albertocavalcante.groovylsp.version.GroovyVersion
import com.github.albertocavalcante.groovylsp.version.GroovyVersionRange
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkerHandshakeTest {

    @Test
    fun `handshake compatible when protocol matches and version in range`() {
        val request = WorkerHandshakeRequest(
            protocolVersion = WorkerProtocol.VERSION,
            requestedGroovyVersion = GroovyVersion.parse("3.0.9")!!,
        )
        val response = WorkerHandshakeResponse(
            protocolVersion = WorkerProtocol.VERSION,
            workerId = "worker-1",
            groovyRange = GroovyVersionRange(
                GroovyVersion.parse("2.5.0")!!,
                GroovyVersion.parse("4.0.0")!!,
            ),
            capabilities = WorkerCapabilities(features = setOf(WorkerFeature.AST)),
        )

        assertTrue(response.isCompatible(request))
    }

    @Test
    fun `handshake rejects protocol mismatch`() {
        val request = WorkerHandshakeRequest(
            protocolVersion = WorkerProtocol.VERSION + 1,
            requestedGroovyVersion = GroovyVersion.parse("3.0.9")!!,
        )
        val response = WorkerHandshakeResponse(
            protocolVersion = WorkerProtocol.VERSION,
            workerId = "worker-1",
            groovyRange = GroovyVersionRange(
                GroovyVersion.parse("2.5.0")!!,
                GroovyVersion.parse("4.0.0")!!,
            ),
            capabilities = WorkerCapabilities(features = emptySet()),
        )

        assertFalse(response.isCompatible(request))
    }

    @Test
    fun `handshake rejects version outside range`() {
        val request = WorkerHandshakeRequest(
            protocolVersion = WorkerProtocol.VERSION,
            requestedGroovyVersion = GroovyVersion.parse("4.2.0")!!,
        )
        val response = WorkerHandshakeResponse(
            protocolVersion = WorkerProtocol.VERSION,
            workerId = "worker-1",
            groovyRange = GroovyVersionRange(
                GroovyVersion.parse("2.5.0")!!,
                GroovyVersion.parse("4.0.0")!!,
            ),
            capabilities = WorkerCapabilities(features = emptySet()),
        )

        assertFalse(response.isCompatible(request))
    }
}
