package com.github.albertocavalcante.groovyspock

/**
 * Spock block types as defined by the Spock Framework specification.
 *
 * @see [Spock Blocks](http://spockframework.org/spock/docs/current/spock_primer.html#_blocks)
 */
enum class SpockBlock {
    GIVEN,
    SETUP,
    WHEN,
    THEN,
    EXPECT,
    WHERE,
    CLEANUP,
    AND;

    companion object {
        /**
         * Parse a statement label into a SpockBlock.
         *
         * @param label The label text (e.g., "given", "when")
         * @return The corresponding SpockBlock, or null if not a valid Spock label
         */
        fun fromLabel(label: String): SpockBlock? = when (label.lowercase()) {
            "given" -> GIVEN
            "setup" -> SETUP
            "when" -> WHEN
            "then" -> THEN
            "expect" -> EXPECT
            "where" -> WHERE
            "cleanup" -> CLEANUP
            "and" -> AND
            else -> null
        }
    }
}
