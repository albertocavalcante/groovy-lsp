package com.github.albertocavalcante.groovylsp.progress

import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Handles LSP progress reporting to show dependency resolution progress to users.
 * Provides a clean API for starting, updating, and completing progress notifications.
 */
class ProgressReporter(private val client: LanguageClient?) {
    private val logger = LoggerFactory.getLogger(ProgressReporter::class.java)
    private val progressToken = "groovy-lsp-deps-${UUID.randomUUID()}"
    private var isActive = false

    /**
     * Starts a new progress notification for dependency resolution.
     *
     * @param title The title to display in the progress UI
     * @param initialMessage Optional initial progress message
     */
    // FIXME: Replace with specific exception types (JsonRpcException, IllegalStateException)
    @Suppress("TooGenericExceptionCaught")
    fun startDependencyResolution(
        title: String = "Resolving Gradle dependencies",
        initialMessage: String = "Initializing...",
    ) {
        if (client == null) {
            logger.debug("No client available for progress reporting")
            return
        }

        try {
            // Create progress token
            client.createProgress(
                WorkDoneProgressCreateParams().apply {
                    token = Either.forLeft(progressToken)
                },
            )

            // Begin progress
            client.notifyProgress(
                ProgressParams().apply {
                    token = Either.forLeft(progressToken)
                    value = Either.forLeft(
                        WorkDoneProgressBegin().apply {
                            this.title = title
                            this.message = initialMessage
                            this.percentage = 0
                            this.cancellable = false // Dependencies can't be cancelled safely
                        },
                    )
                },
            )

            isActive = true
            logger.debug("Started progress reporting: $title")
        } catch (e: Exception) {
            logger.warn("Failed to start progress reporting", e)
        }
    }

    /**
     * Updates the progress with a new message and optional percentage.
     *
     * @param message The progress message to display
     * @param percentage Optional completion percentage (0-100)
     */
    // FIXME: Replace with specific exception types (JsonRpcException, IllegalStateException)
    @Suppress("TooGenericExceptionCaught")
    fun updateProgress(message: String, percentage: Int? = null) {
        if (client == null || !isActive) {
            logger.debug("Progress update skipped - client unavailable or not active: $message")
            return
        }

        try {
            client.notifyProgress(
                ProgressParams().apply {
                    token = Either.forLeft(progressToken)
                    value = Either.forLeft(
                        WorkDoneProgressReport().apply {
                            this.message = message
                            this.percentage = percentage
                        },
                    )
                },
            )

            logger.debug("Updated progress: $message ${percentage?.let { "($it%)" } ?: ""}")
        } catch (e: Exception) {
            logger.warn("Failed to update progress", e)
        }
    }

    /**
     * Completes the progress notification with a final message.
     *
     * @param message The completion message to display
     */
    // FIXME: Replace with specific exception types (JsonRpcException, IllegalStateException)
    @Suppress("TooGenericExceptionCaught")
    fun complete(message: String) {
        if (client == null || !isActive) {
            logger.debug("Progress completion skipped - client unavailable or not active: $message")
            return
        }

        try {
            client.notifyProgress(
                ProgressParams().apply {
                    token = Either.forLeft(progressToken)
                    value = Either.forLeft(
                        WorkDoneProgressEnd().apply {
                            this.message = message
                        },
                    )
                },
            )

            isActive = false
            logger.debug("Completed progress: $message")
        } catch (e: Exception) {
            logger.warn("Failed to complete progress", e)
        }
    }

    /**
     * Completes progress reporting due to an error.
     *
     * @param errorMessage The error message to display
     */
    fun completeWithError(errorMessage: String) {
        complete("⚠️ $errorMessage")
    }

    /**
     * Checks if progress reporting is currently active.
     */
    fun isActive(): Boolean = isActive

    /**
     * Cancels/ends progress reporting if active.
     */
    fun cancel() {
        if (isActive) {
            complete("Cancelled")
        }
    }
}
