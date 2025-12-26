package com.github.albertocavalcante.groovylsp.version

/**
 * Parsed Groovy version with a comparison strategy suitable for worker selection.
 */
data class GroovyVersion(
    val raw: String,
    val major: Int,
    val minor: Int,
    val patch: Int,
    private val parts: List<Int>,
) : Comparable<GroovyVersion> {

    override fun compareTo(other: GroovyVersion): Int {
        val maxSize = maxOf(parts.size, other.parts.size)
        for (index in 0 until maxSize) {
            val left = parts.getOrNull(index) ?: DEFAULT_PART
            val right = other.parts.getOrNull(index) ?: DEFAULT_PART
            if (left != right) {
                return left.compareTo(right)
            }
        }
        return 0
    }

    companion object {
        fun parse(raw: String?): GroovyVersion? {
            val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val tokens = trimmed.split(DELIMITER_REGEX)
            if (tokens.isEmpty()) return null

            val major = tokens.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = tokens.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = tokens.getOrNull(2)?.toIntOrNull() ?: 0
            val parts = tokens.map(::parsePart)

            return GroovyVersion(trimmed, major, minor, patch, parts)
        }

        private fun parsePart(part: String): Int = part.toIntOrNull() ?: when (part.lowercase()) {
            "snapshot" -> 999
            "alpha" -> -3
            "beta" -> -2
            "rc" -> -1
            else -> DEFAULT_PART
        }
    }
}

private val DELIMITER_REGEX = Regex("[.-]")
private const val DEFAULT_PART = 0
