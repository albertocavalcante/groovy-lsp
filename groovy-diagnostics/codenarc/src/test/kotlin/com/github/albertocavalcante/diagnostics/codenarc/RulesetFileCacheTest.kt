package com.github.albertocavalcante.diagnostics.codenarc

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RulesetFileCacheTest {

    @Test
    fun `same ruleset content reuses cached file path`() {
        val cacheDir = Files.createTempDirectory("ruleset-cache-test").also { it.toFile().deleteOnExit() }
        val cache = RulesetFileCache(cacheDir, ConcurrentHashMap())

        val rulesetContent = "ruleset { TrailingWhitespace }"
        val first = cache.getOrCreate(rulesetContent)
        val second = cache.getOrCreate(rulesetContent)

        assertEquals(first, second)
        assertTrue(Files.exists(first))
    }

    @Test
    fun `different ruleset content uses different cached file paths`() {
        val cacheDir = Files.createTempDirectory("ruleset-cache-test").also { it.toFile().deleteOnExit() }
        val cache = RulesetFileCache(cacheDir, ConcurrentHashMap())

        val first = cache.getOrCreate("ruleset { TrailingWhitespace }")
        val second = cache.getOrCreate("ruleset { EmptyClass }")

        assertTrue(first != second)
        assertTrue(Files.exists(first))
        assertTrue(Files.exists(second))
    }

    @Test
    fun `cached ruleset file contains the original content`() {
        val cacheDir = Files.createTempDirectory("ruleset-cache-test").also { it.toFile().deleteOnExit() }
        val cache = RulesetFileCache(cacheDir, ConcurrentHashMap())

        val rulesetContent = "ruleset { TrailingWhitespace }"
        val path = cache.getOrCreate(rulesetContent)

        val stored = Files.readString(path)
        assertNotNull(stored)
        assertEquals(rulesetContent, stored)
    }
}
