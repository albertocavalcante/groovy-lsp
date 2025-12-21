/**
 * Default CodeNarc ruleset for plain Groovy projects.
 * This provides a sensible baseline of rules for code quality.
 */
ruleset {
    description 'Default ruleset for Groovy LSP'

    // Basic rules for common errors
    ruleset('rulesets/basic.xml')

    // Import rules for clean imports
    ruleset('rulesets/imports.xml') {
        // Exclude noisy import rules (we'll configure them separately below)
        exclude 'DuplicateImport'
        exclude 'UnnecessaryGroovyImport'
    }

    // Unnecessary code
    ruleset('rulesets/unnecessary.xml') {
        // Exclude rules we want to disable
        exclude 'UnnecessaryGetter'
        exclude 'UnnecessarySetter'
    }

    // Unused code
    ruleset('rulesets/unused.xml')

    // Formatting rules
    ruleset('rulesets/formatting.xml') {
        // Exclude line length (too noisy)
        exclude 'LineLength'
    }

    // Groovyism - idiomatic Groovy
    ruleset('rulesets/groovyism.xml')

    // Exception handling
    ruleset('rulesets/exceptions.xml')
}

// Configure individual rules outside ruleset blocks
DuplicateImport {
    priority = 3
}

UnnecessaryGroovyImport {
    priority = 3
}

UnnecessaryPublicModifier {
    priority = 3
}

UnnecessarySemicolon {
    priority = 3
}

UnusedVariable {
    priority = 2
}

UnusedPrivateField {
    priority = 2
}

UnusedPrivateMethod {
    priority = 2
}

SpaceAroundClosureArrow {
    enabled = true
}

TrailingWhitespace {
    priority = 3
}

GStringExpressionWithinString {
    priority = 3
}

ExplicitCallToEqualsMethod {
    priority = 3
}

CatchException {
    priority = 2
}

CatchThrowable {
    priority = 1
}

ThrowException {
    priority = 2
}
