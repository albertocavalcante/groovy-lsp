# Groovy Language Support

[![CI](https://github.com/albertocavalcante/vscode-groovy/actions/workflows/main.yml/badge.svg)](https://github.com/albertocavalcante/vscode-groovy/actions/workflows/main.yml)

A VS Code extension that adds Groovy language support with syntax highlighting, code completion, and error checking.

> **Status:** Early release - actively maintained and stable for everyday use.

## Features

### Core Language Support
- [x] Syntax highlighting for all Groovy file types
- [x] Code completion and IntelliSense with type information
- [x] Real-time error detection and diagnostics
- [x] Hover documentation with GroovyDoc/JavaDoc
- [x] Go to Definition & Type Definition
- [x] Find All References
- [x] Rename Symbol (workspace-wide)
- [x] Signature Help (parameter hints)
- [x] Code Formatting (OpenRewrite-based)
- [x] Document & Workspace Symbols
- [x] Automatic language server management

### Static Analysis
- [x] CodeNarc integration with project-specific rulesets
- [x] Auto-detection of project types (Jenkins, Gradle, Spock)
- [x] Quick fixes for common issues
- [x] TODO/FIXME comment scanning

### Build Integration
- [x] Gradle project support
- [x] Automatic dependency resolution
- [x] Multi-workspace support

### Jenkins Pipeline
- [x] Jenkinsfile syntax support
- [x] Shared library configuration
- [x] GDSL file support for DSL extensions
- [x] Custom pipeline step completion

## Supported Files

| File Type | Extensions | What it's for |
|-----------|------------|---------------|
| Groovy Scripts | `.groovy`, `.gvy`, `.gy`, `.gsh` | General Groovy code |
| Gradle Build | `.gradle` | Build scripts |
| Jenkins Pipeline | `Jenkinsfile`, `Jenkinsfile.*` | CI/CD pipelines |

## Setup

**Requires Java 17+** to be installed on your system.

1. Install this extension from the VS Code Marketplace
2. Open any `.groovy`, `.gradle`, or `Jenkinsfile` - language support starts automatically

That's it! The extension will find Java automatically from your PATH or `JAVA_HOME`.

## Configuration

### Basic Settings

```json
{
  // Java configuration (required)
  "groovy.java.home": "/path/to/your/java17",

  // Enable/disable code formatting
  "groovy.format.enable": true,

  // Trace LSP communication (off, messages, verbose)
  "groovy.trace.server": "off"
}
```

### CodeNarc Static Analysis

```json
{
  // Enable CodeNarc linting
  "groovy.codenarc.enabled": true,

  // Path to custom CodeNarc config file
  "groovy.codenarc.propertiesFile": "/path/to/codenarc.properties",

  // Auto-detect project type and apply appropriate ruleset
  "groovy.codenarc.autoDetect": true
}
```

### Jenkins Pipeline Support

For Jenkins shared libraries:

```json
{
  // File patterns to recognize as Jenkinsfiles
  "groovy.jenkins.filePatterns": ["Jenkinsfile", "*.jenkins", "*.jenkinsfile"],

  // Configure shared libraries
  "groovy.jenkins.sharedLibraries": [
    {
      "name": "my-pipeline-lib",
      "jar": "/path/to/my-pipeline-lib.jar",
      "sourcesJar": "/path/to/my-pipeline-lib-sources.jar"
    }
  ],

  // GDSL files for DSL enhancements
  "groovy.jenkins.gdslPaths": ["/path/to/jenkins.gdsl"]
}
```

### Compilation Settings

```json
{
  // Compilation mode: "workspace" (accurate) or "single-file" (fast)
  "groovy.compilation.mode": "workspace",

  // Files changed threshold for full recompilation
  "groovy.compilation.incrementalThreshold": 50,

  // Maximum files to compile in workspace mode
  "groovy.compilation.maxWorkspaceFiles": 500
}
```

### TODO Comment Scanning

```json
{
  // Enable TODO/FIXME scanning
  "groovy.todo.scanEnabled": true,

  // Configure patterns and severity levels
  "groovy.todo.patterns": {
    "TODO": "Information",
    "FIXME": "Warning",
    "BUG": "Error"
  }
}
```

## Commands

- `Groovy: Restart Language Server` - Restart the language server if something goes wrong
- `Groovy: Show Language Server Version` - Display the current LSP server version

## Development

Want to contribute? Check out the [Contributing Guide](CONTRIBUTING.md).

```bash
git clone https://github.com/albertocavalcante/vscode-groovy.git
npm install
npm run compile
```

## Copilot coding agent

See [COPILOT.md](COPILOT.md) for agent-specific setup and the `copilot-setup-steps` workflow.

## Troubleshooting

**Extension not working?**

1. Check you have Java 17+ installed: `java -version`
2. Try restarting the language server: `Cmd/Ctrl+Shift+P` → "Groovy: Restart Server"
3. Check the "Groovy Language Server" output panel for error messages

**File not recognized?** Make sure it has a supported extension or is named `Jenkinsfile`.

## Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for setup and guidelines.

---

**Links:** [GitHub](https://github.com/albertocavalcante/vscode-groovy) • [Issues](https://github.com/albertocavalcante/vscode-groovy/issues) • [Language Server](https://github.com/albertocavalcante/groovy-lsp)
