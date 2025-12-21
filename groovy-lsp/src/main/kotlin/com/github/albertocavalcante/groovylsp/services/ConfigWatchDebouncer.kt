package com.github.albertocavalcante.groovylsp.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Debounces rapid file changes to avoid spamming recompilations.
 * Waits for a quiet period (500ms) before executing the action.
 */
class ConfigWatchDebouncer(private val scope: CoroutineScope, private val debounceDelayMs: Long = 500) {
    private val logger = LoggerFactory.getLogger(ConfigWatchDebouncer::class.java)
    private val pendingJobs = mutableMapOf<String, Job>()

    /**
     * Schedules an action to be executed after a debounce delay.
     * If called again before the delay expires, the previous action is cancelled.
     *
     * @param key Unique key identifying the file/change type
     * @param action The action to execute after debounce
     */
    fun debounce(key: String, action: suspend () -> Unit) {
        // Cancel existing job for this key
        pendingJobs[key]?.cancel()

        // Schedule new job
        val job = scope.launch {
            delay(debounceDelayMs)
            try {
                action()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Re-throw cancellation to preserve coroutine cancellation
            } catch (e: Exception) {
                logger.warn("Debounced action failed for key: $key", e)
            } finally {
                pendingJobs.remove(key)
            }
        }

        pendingJobs[key] = job
    }

    /**
     * Cancels all pending debounced actions.
     */
    fun cancelAll() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
    }
}
