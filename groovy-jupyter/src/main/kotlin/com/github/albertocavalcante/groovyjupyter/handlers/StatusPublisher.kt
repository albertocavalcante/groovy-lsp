package com.github.albertocavalcante.groovyjupyter.handlers

import com.github.albertocavalcante.groovyjupyter.protocol.Header
import com.github.albertocavalcante.groovyjupyter.protocol.JupyterMessage
import com.github.albertocavalcante.groovyjupyter.protocol.MessageType
import com.github.albertocavalcante.groovyjupyter.security.HmacSigner
import com.github.albertocavalcante.groovyjupyter.zmq.WireMessage
import org.slf4j.LoggerFactory
import org.zeromq.ZMQ

/**
 * Publishes kernel execution status on the IOPub socket.
 *
 * The kernel must publish busy/idle status to inform frontends
 * about its execution state. This helps UI show activity indicators.
 */
class StatusPublisher(private val iopubSocket: ZMQ.Socket, private val signer: HmacSigner) {
    private val logger = LoggerFactory.getLogger(StatusPublisher::class.java)

    /**
     * Publish that the kernel is busy processing a request.
     */
    fun publishBusy(parent: JupyterMessage) {
        publishStatus("busy", parent)
    }

    /**
     * Publish that the kernel is idle and ready for new requests.
     */
    fun publishIdle(parent: JupyterMessage) {
        publishStatus("idle", parent)
    }

    private fun publishStatus(executionState: String, parent: JupyterMessage) {
        logger.debug("Publishing status: {}", executionState)

        val header = Header(
            session = parent.header.session,
            username = parent.header.username,
            msgType = MessageType.STATUS.value,
        )

        val content = """{"execution_state":"$executionState"}"""

        val wireMessage = WireMessage(
            identities = emptyList(),
            signature = "", // Will be computed
            header = header.toJson(),
            parentHeader = parent.header.toJson(),
            metadata = "{}",
            content = content,
        )

        val frames = wireMessage.toSignedFrames(signer)
        sendMultipart(frames)

        logger.trace("Status {} published", executionState)
    }

    private fun sendMultipart(frames: List<ByteArray>) {
        frames.forEachIndexed { index, frame ->
            val sendMore = index < frames.size - 1
            iopubSocket.send(frame, if (sendMore) ZMQ.SNDMORE else 0)
        }
    }
}
