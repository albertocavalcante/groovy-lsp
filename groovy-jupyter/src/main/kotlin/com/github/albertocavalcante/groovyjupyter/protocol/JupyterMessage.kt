package com.github.albertocavalcante.groovyjupyter.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Message types defined by the Jupyter messaging protocol.
 *
 * @see <a href="https://jupyter-client.readthedocs.io/en/stable/messaging.html">
 *     Jupyter Messaging Protocol</a>
 */
enum class MessageType(val value: String) {
    // Shell messages
    KERNEL_INFO_REQUEST("kernel_info_request"),
    KERNEL_INFO_REPLY("kernel_info_reply"),
    EXECUTE_REQUEST("execute_request"),
    EXECUTE_REPLY("execute_reply"),
    EXECUTE_INPUT("execute_input"),
    EXECUTE_RESULT("execute_result"),
    COMPLETE_REQUEST("complete_request"),
    COMPLETE_REPLY("complete_reply"),
    INSPECT_REQUEST("inspect_request"),
    INSPECT_REPLY("inspect_reply"),
    IS_COMPLETE_REQUEST("is_complete_request"),
    IS_COMPLETE_REPLY("is_complete_reply"),
    HISTORY_REQUEST("history_request"),
    HISTORY_REPLY("history_reply"),

    // IOPub messages
    STATUS("status"),
    STREAM("stream"),
    DISPLAY_DATA("display_data"),
    ERROR("error"),

    // Stdin messages
    INPUT_REQUEST("input_request"),
    INPUT_REPLY("input_reply"),

    // Control messages
    SHUTDOWN_REQUEST("shutdown_request"),
    SHUTDOWN_REPLY("shutdown_reply"),
    INTERRUPT_REQUEST("interrupt_request"),
    INTERRUPT_REPLY("interrupt_reply"),
    ;

    companion object {
        fun fromValue(value: String): MessageType? =
            entries.find { it.value == value }
    }
}

/**
 * Header for Jupyter messages.
 */
@Serializable
data class Header(
    @SerialName("msg_id") val msgId: String = UUID.randomUUID().toString(),
    val session: String = "",
    val username: String = "kernel",
    val date: String = timestamp(),
    @SerialName("msg_type") val msgType: String = "",
    val version: String = "5.3",
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { encodeDefaults = true }

        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

        fun timestamp(): String =
            Instant.now().atOffset(ZoneOffset.UTC).format(formatter)
    }
}

/**
 * Complete Jupyter message with header, parent header, metadata, and content.
 */
data class JupyterMessage(
    var header: Header,
    var parentHeader: Header? = null,
    var metadata: Map<String, Any> = emptyMap(),
    var content: Map<String, Any> = emptyMap(),
    val identities: MutableList<ByteArray> = mutableListOf(),
) {
    fun contentToJson(): String = json.encodeToString(serializableContent())

    /**
     * Create a reply message to this request.
     */
    fun createReply(replyType: MessageType): JupyterMessage = JupyterMessage(
        header = Header(
            session = header.session,
            msgType = replyType.value,
        ),
        parentHeader = header,
        identities = identities,
    )

    @Suppress("UNCHECKED_CAST")
    private fun serializableContent(): Map<String, kotlinx.serialization.json.JsonElement> =
        content.mapValues { (_, v) ->
            when (v) {
                is String -> kotlinx.serialization.json.JsonPrimitive(v)
                is Number -> kotlinx.serialization.json.JsonPrimitive(v)
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
            }
        }

    companion object {
        private val json = Json { encodeDefaults = true }

        /**
         * Create a new message with the given type and session.
         */
        fun create(type: MessageType, session: String): JupyterMessage = JupyterMessage(
            header = Header(
                session = session,
                msgType = type.value,
            ),
        )
    }
}
