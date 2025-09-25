package com.github.albertocavalcante.groovylsp

import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("GroovyLSP")

fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: "stdio"

    try {
        when (mode) {
            "stdio" -> runStdio()
            "socket" -> {
                val port = args.getOrNull(1)?.toIntOrNull() ?: 8080
                runSocket(port)
            }
            "--help", "-h" -> {
                printHelp()
                exitProcess(0)
            }
            else -> {
                logger.error("Unknown mode: $mode")
                printHelp()
                exitProcess(1)
            }
        }
    } catch (e: Exception) {
        logger.error("Failed to start Groovy Language Server", e)
        exitProcess(1)
    }
}

private fun runStdio() {
    logger.info("Starting Groovy Language Server in stdio mode")
    val input = System.`in`
    val output = System.out

    // Redirect System.out to System.err to prevent pollution of LSP messages
    System.setOut(System.err)

    startServer(input, output)
}

private fun runSocket(port: Int) {
    logger.info("Starting Groovy Language Server on port $port")

    ServerSocket(port).use { serverSocket ->
        logger.info("Listening on port $port...")

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                serverSocket.close()
                logger.info("Server socket closed")
            } catch (e: Exception) {
                logger.error("Error closing server socket", e)
            }
        })

        while (!serverSocket.isClosed) {
            try {
                val socket = serverSocket.accept()
                logger.info("Client connected from ${socket.inetAddress}")

                // Handle each client in a separate thread
                Thread({
                    socket.use {
                        try {
                            startServer(it.getInputStream(), it.getOutputStream())
                        } catch (e: Exception) {
                            logger.error("Error handling client connection", e)
                        }
                    }
                }, "groovy-lsp-client-${socket.inetAddress}").start()
            } catch (e: Exception) {
                if (!serverSocket.isClosed) {
                    logger.error("Error accepting connection", e)
                }
            }
        }
    }
}

private fun startServer(input: InputStream, output: OutputStream) {
    val server = GroovyLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(server, input, output)

    val client = launcher.remoteProxy
    server.connect(client)

    logger.info("Language Server initialized and listening")
    launcher.startListening().get()
}

private fun printHelp() {
    println("""
        Groovy Language Server

        Usage: groovy-lsp [mode] [options]

        Modes:
          stdio              Run in stdio mode (default)
          socket [port]      Run in socket mode on specified port (default: 8080)

        Options:
          --help, -h         Show this help message

        Examples:
          groovy-lsp                    # Start in stdio mode
          groovy-lsp stdio              # Start in stdio mode
          groovy-lsp socket             # Start in socket mode on port 8080
          groovy-lsp socket 9000        # Start in socket mode on port 9000
    """.trimIndent())
}