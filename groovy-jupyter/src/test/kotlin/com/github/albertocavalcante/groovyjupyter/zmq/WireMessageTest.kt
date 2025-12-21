package com.github.albertocavalcante.groovyjupyter.zmq

import com.github.albertocavalcante.groovyjupyter.security.HmacSigner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * TDD tests for WireMessage - Jupyter Wire Protocol parsing and serialization.
 *
 * Jupyter messages are multipart ZMQ messages with structure:
 * [identities...] | <IDS|MSG> | signature | header | parent_header | metadata | content | [buffers...]
 *
 * @see <a href="https://jupyter-client.readthedocs.io/en/stable/messaging.html#the-wire-protocol">
 *     Jupyter Wire Protocol</a>
 */
class WireMessageTest {

    private val delimiter = "<IDS|MSG>".toByteArray()

    @Test
    fun `should parse multipart message with single identity`() {
        // Given: A valid wire message with one identity frame
        val identity = "client-uuid".toByteArray()
        val signature = "abc123"
        val header = """{"msg_id":"1","msg_type":"execute_request"}"""
        val parentHeader = "{}"
        val metadata = "{}"
        val content = """{"code":"x=1"}"""

        val frames = listOf(
            identity,
            delimiter,
            signature.toByteArray(),
            header.toByteArray(),
            parentHeader.toByteArray(),
            metadata.toByteArray(),
            content.toByteArray(),
        )

        // When: Parsing the frames
        val wireMsg = WireMessage.fromFrames(frames)

        // Then: Should extract all parts correctly
        assertThat(wireMsg.identities).hasSize(1)
        assertThat(String(wireMsg.identities[0])).isEqualTo("client-uuid")
        assertThat(wireMsg.signature).isEqualTo(signature)
        assertThat(wireMsg.header).isEqualTo(header)
        assertThat(wireMsg.parentHeader).isEqualTo(parentHeader)
        assertThat(wireMsg.metadata).isEqualTo(metadata)
        assertThat(wireMsg.content).isEqualTo(content)
        assertThat(wireMsg.buffers).isEmpty()
    }

    @Test
    fun `should parse multipart message with multiple identities`() {
        // Given: A message with multiple identity frames (before delimiter)
        val identity1 = "router-id-1".toByteArray()
        val identity2 = "router-id-2".toByteArray()
        val signature = "sig456"
        val header = """{"msg_id":"2"}"""
        val parentHeader = "{}"
        val metadata = "{}"
        val content = "{}"

        val frames = listOf(
            identity1,
            identity2,
            delimiter,
            signature.toByteArray(),
            header.toByteArray(),
            parentHeader.toByteArray(),
            metadata.toByteArray(),
            content.toByteArray(),
        )

        // When: Parsing
        val wireMsg = WireMessage.fromFrames(frames)

        // Then: Should capture both identities
        assertThat(wireMsg.identities).hasSize(2)
        assertThat(String(wireMsg.identities[0])).isEqualTo("router-id-1")
        assertThat(String(wireMsg.identities[1])).isEqualTo("router-id-2")
    }

    @Test
    fun `should parse message with no identity frames`() {
        // Given: A message with delimiter as first frame (no identities)
        val signature = "nosig"
        val header = """{"msg_id":"3"}"""
        val parentHeader = "{}"
        val metadata = "{}"
        val content = "{}"

        val frames = listOf(
            delimiter,
            signature.toByteArray(),
            header.toByteArray(),
            parentHeader.toByteArray(),
            metadata.toByteArray(),
            content.toByteArray(),
        )

        // When: Parsing
        val wireMsg = WireMessage.fromFrames(frames)

        // Then: Should have empty identities
        assertThat(wireMsg.identities).isEmpty()
        assertThat(wireMsg.header).isEqualTo(header)
    }

    @Test
    fun `should parse message with extra buffers`() {
        // Given: A message with binary buffers after content
        val identity = "id".toByteArray()
        val buffer1 = byteArrayOf(0x01, 0x02, 0x03)
        val buffer2 = byteArrayOf(0x04, 0x05)

        val frames = listOf(
            identity,
            delimiter,
            "sig".toByteArray(),
            "{}".toByteArray(), // header
            "{}".toByteArray(), // parent
            "{}".toByteArray(), // metadata
            "{}".toByteArray(), // content
            buffer1,
            buffer2,
        )

        // When: Parsing
        val wireMsg = WireMessage.fromFrames(frames)

        // Then: Should capture buffers
        assertThat(wireMsg.buffers).hasSize(2)
        assertThat(wireMsg.buffers[0]).isEqualTo(buffer1)
        assertThat(wireMsg.buffers[1]).isEqualTo(buffer2)
    }

    @Test
    fun `should reject malformed message without delimiter`() {
        // Given: Frames without the delimiter
        val frames = listOf(
            "identity".toByteArray(),
            "not-a-delimiter".toByteArray(),
            "{}".toByteArray(),
        )

        // When/Then: Should throw
        assertThrows<IllegalArgumentException> {
            WireMessage.fromFrames(frames)
        }
    }

    @Test
    fun `should reject message with insufficient frames after delimiter`() {
        // Given: Delimiter but not enough frames after
        val frames = listOf(
            delimiter,
            "sig".toByteArray(),
            "{}".toByteArray(), // header only - need 4 more
        )

        // When/Then: Should throw
        assertThrows<IllegalArgumentException> {
            WireMessage.fromFrames(frames)
        }
    }

    @Test
    fun `should serialize message to frames`() {
        // Given: A WireMessage
        val wireMsg = WireMessage(
            identities = listOf("client-1".toByteArray()),
            signature = "testsig",
            header = """{"msg_id":"1"}""",
            parentHeader = "{}",
            metadata = "{}",
            content = """{"status":"ok"}""",
            buffers = emptyList(),
        )

        // When: Converting to frames
        val frames = wireMsg.toFrames()

        // Then: Should produce correct frame sequence
        assertThat(frames).hasSize(7) // 1 identity + delimiter + 5 message parts
        assertThat(String(frames[0])).isEqualTo("client-1")
        assertThat(String(frames[1])).isEqualTo("<IDS|MSG>")
        assertThat(String(frames[2])).isEqualTo("testsig")
        assertThat(String(frames[3])).isEqualTo("""{"msg_id":"1"}""")
        assertThat(String(frames[6])).isEqualTo("""{"status":"ok"}""")
    }

    @Test
    fun `should serialize message with buffers`() {
        // Given: A message with binary buffers
        val buffer = byteArrayOf(0x00, 0x01, 0x02)
        val wireMsg = WireMessage(
            identities = emptyList(),
            signature = "",
            header = "{}",
            parentHeader = "{}",
            metadata = "{}",
            content = "{}",
            buffers = listOf(buffer),
        )

        // When: Converting to frames
        val frames = wireMsg.toFrames()

        // Then: Buffer should be at the end
        assertThat(frames).hasSize(7) // delimiter + 5 parts + 1 buffer
        assertThat(frames.last()).isEqualTo(buffer)
    }

    @Test
    fun `should compute signature using HmacSigner`() {
        // Given: A signer and message parts
        val signer = HmacSigner("test-key")
        val wireMsg = WireMessage(
            identities = listOf("id".toByteArray()),
            signature = "", // Will be computed
            header = """{"msg_id":"1"}""",
            parentHeader = "{}",
            metadata = "{}",
            content = """{"code":"1+1"}""",
        )

        // When: Converting to frames with signing
        val frames = wireMsg.toSignedFrames(signer)

        // Then: Signature frame should be valid HMAC
        val signatureFrame = String(frames[2])
        assertThat(signatureFrame).isNotEmpty()
        assertThat(signatureFrame).matches("[0-9a-f]+")

        // And: Should verify correctly
        val parts = listOf(
            wireMsg.header,
            wireMsg.parentHeader,
            wireMsg.metadata,
            wireMsg.content,
        )
        assertThat(signer.verify(signatureFrame, parts)).isTrue()
    }

    @Test
    fun `should handle empty signature when key is empty`() {
        // Given: A signer with empty key
        val signer = HmacSigner("")
        val wireMsg = WireMessage(
            identities = emptyList(),
            signature = "",
            header = "{}",
            parentHeader = "{}",
            metadata = "{}",
            content = "{}",
        )

        // When: Converting to signed frames
        val frames = wireMsg.toSignedFrames(signer)

        // Then: Signature should be empty
        val signatureFrame = String(frames[1]) // After delimiter
        assertThat(signatureFrame).isEmpty()
    }

    @Test
    fun `should roundtrip parse and serialize`() {
        // Given: Original frames
        val original = listOf(
            "identity".toByteArray(),
            delimiter,
            "signature".toByteArray(),
            """{"msg_id":"roundtrip"}""".toByteArray(),
            "{}".toByteArray(),
            """{"key":"value"}""".toByteArray(),
            """{"data":123}""".toByteArray(),
        )

        // When: Parse then serialize
        val wireMsg = WireMessage.fromFrames(original)
        val roundtripped = wireMsg.toFrames()

        // Then: Should match
        assertThat(roundtripped).hasSize(original.size)
        for (i in original.indices) {
            assertThat(String(roundtripped[i])).isEqualTo(String(original[i]))
        }
    }
}
