package com.github.albertocavalcante.groovyjupyter.jenkins

import com.github.albertocavalcante.groovyjupyter.handlers.ExecuteHandler
import com.github.albertocavalcante.groovyjupyter.handlers.HeartbeatHandler
import com.github.albertocavalcante.groovyjupyter.handlers.KernelInfoHandler
import com.github.albertocavalcante.groovyjupyter.handlers.ShutdownHandler
import com.github.albertocavalcante.groovyjupyter.kernel.KernelServer
import com.github.albertocavalcante.groovyjupyter.protocol.ConnectionFile
import com.github.albertocavalcante.groovyjupyter.security.HmacSigner
import com.github.albertocavalcante.groovyjupyter.zmq.JupyterConnection
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("JenkinsKernelMain")

    if (args.isEmpty()) {
        logger.error("Usage: java -jar jenkins-kernel.jar <connection_file>")
        exitProcess(1)
    }

    val connectionFilePath = args[0]
    logger.info("Starting Jenkins Kernel with connection file: {}", connectionFilePath)

    try {
        val connectionFileContent = File(connectionFilePath).readText()
        val connectionFile = ConnectionFile.parse(connectionFileContent)

        // Initialize Crypto
        val signer = HmacSigner(connectionFile.key)

        // Initialize Connection
        val connection = JupyterConnection(connectionFile, signer)

        // Initialize Executors specific to this kernel
        val executor = JenkinsExecutor()

        // Initialize Handlers
        val heartbeatHandler = HeartbeatHandler(connection.heartbeatSocket)

        val handlers = listOf(
            ExecuteHandler(executor),
            KernelInfoHandler(
                languageName = "jenkins-groovy",
                languageVersion = "2.4.21", // Hardcoded for now
            ),
            ShutdownHandler { connection.close() },
        )

        // Start Server
        KernelServer(connection, handlers, heartbeatHandler).use { server ->
            server.run()
        }
    } catch (e: Exception) {
        logger.error("Fatal error starting kernel", e)
        exitProcess(1)
    }
}
