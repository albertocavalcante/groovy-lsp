package com.github.albertocavalcante.groovylsp.gradle

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class GradleDependencyResolverRetryTest {

    @Test
    fun `retries with isolated Gradle user home when init scripts break model fetch`(@TempDir projectDir: Path) {
        projectDir.resolve("build.gradle").toFile().writeText("")

        val connectionFactory = mockk<GradleConnectionFactory>()

        val firstConnection = mockk<ProjectConnection>()
        val secondConnection = mockk<ProjectConnection>()

        val modelBuilder = mockk<ModelBuilder<IdeaProject>>()
        every { modelBuilder.withArguments(any(), any(), any(), any()) } returns modelBuilder
        every { modelBuilder.setJvmArguments(any(), any()) } returns modelBuilder

        val error = RuntimeException(
            "Could not open cp_init generic class cache for initialization script '~/.gradle/init.d/foo.gradle'\n" +
                "Unsupported class file major version 65",
        )
        // Fail both attempts; this test only asserts we try an isolated user home fallback.
        every { modelBuilder.get() } throws error
        every { firstConnection.model(IdeaProject::class.java) } returns modelBuilder
        every { secondConnection.model(IdeaProject::class.java) } returns modelBuilder

        every { connectionFactory.getConnection(any(), any()) } returnsMany listOf(firstConnection, secondConnection)

        val resolver = GradleDependencyResolver(connectionFactory)

        val result = resolver.resolve(projectDir = projectDir, onDownloadProgress = null)

        assertEquals(0, result.dependencies.size)
        assertEquals(0, result.sourceDirectories.size)

        verify(exactly = 2) { connectionFactory.getConnection(any(), any()) }
        // First call uses default Gradle user home (null), second call uses an isolated directory.
        verify { connectionFactory.getConnection(any(), null) }
        verify { connectionFactory.getConnection(any(), match { it != null }) }
    }

    @Test
    fun `does not retry when model fetch fails for unrelated reasons`(@TempDir projectDir: Path) {
        projectDir.resolve("build.gradle").toFile().writeText("")

        val connectionFactory = mockk<GradleConnectionFactory>()
        val connection = mockk<ProjectConnection>()
        val modelBuilder = mockk<ModelBuilder<IdeaProject>>()
        every { modelBuilder.withArguments(any(), any(), any(), any()) } returns modelBuilder
        every { modelBuilder.setJvmArguments(any(), any()) } returns modelBuilder
        every { modelBuilder.get() } throws RuntimeException("some other failure")
        every { connection.model(IdeaProject::class.java) } returns modelBuilder

        every { connectionFactory.getConnection(any(), any()) } returns connection

        val resolver = GradleDependencyResolver(connectionFactory)
        resolver.resolve(projectDir = projectDir, onDownloadProgress = null)

        verify(exactly = 1) { connectionFactory.getConnection(any(), any()) }
        verify { connectionFactory.getConnection(any(), null) }
        verify(exactly = 0) { connectionFactory.getConnection(any(), match { it != null }) }
    }
}
