package com.github.albertocavalcante.groovyspock

import java.net.URI

object SpockDetector {
    fun isLikelySpockSpec(uri: URI, content: String): Boolean {
        val path = uri.path ?: return false
        if (!path.endsWith(".groovy", ignoreCase = true)) return false

        if (path.endsWith("Spec.groovy", ignoreCase = true)) {
            return true
        }

        // NOTE: Heuristic / tradeoff:
        // Spock is typically identified by extending `spock.lang.Specification`, but that requires either AST or
        // classpath-aware type resolution. We use light string markers to enable quick, dependency-free detection.
        // TODO: Prefer AST-driven detection (ClassNode.superClass) and/or classpath resolution when available.
        val normalized = content
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        return normalized.contains("spock.lang.Specification") ||
            normalized.contains("import spock.")
    }
}
