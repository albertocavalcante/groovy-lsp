package com.github.albertocavalcante.groovyspock

import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpockDetectorTest {

    @Test
    fun `detects spock spec by filename`() {
        val uri = URI.create("file:///src/test/groovy/com/example/FooSpec.groovy")
        val content = "class FooSpec {}"

        assertTrue(SpockDetector.isLikelySpockSpec(uri, content))
    }

    @Test
    fun `detects spock spec by content markers`() {
        val uri = URI.create("file:///src/test/groovy/com/example/FooTest.groovy")
        val content =
            """
            import spock.lang.Specification

            class FooTest extends Specification {
            }
            """.trimIndent()

        assertTrue(SpockDetector.isLikelySpockSpec(uri, content))
    }

    @Test
    fun `does not detect spock when no markers present`() {
        val uri = URI.create("file:///src/main/groovy/com/example/Foo.groovy")
        val content =
            """
            class Foo {
                def bar() {}
            }
            """.trimIndent()

        assertFalse(SpockDetector.isLikelySpockSpec(uri, content))
    }
}
