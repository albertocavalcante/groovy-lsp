# Groovy Language Support

[![CI](https://github.com/albertocavalcante/vscode-groovy/actions/workflows/main.yml/badge.svg)](https://github.com/albertocavalcante/vscode-groovy/actions/workflows/main.yml)

A VS Code extension that adds Groovy language support with syntax highlighting, code completion, and error checking.

> **Status:** Early release - actively maintained and stable for everyday use.

## Features

- [x] Syntax highlighting for all Groovy file types
- [x] Code completion and IntelliSense
- [x] Real-time error detection and diagnostics
- [x] Hover documentation
- [x] Automatic language server management

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

If you need to specify a custom Java installation:

```json
{
  "groovy.java.home": "/path/to/your/java17"
}
```

## Commands

- `Groovy: Restart Server` - Restart the language server if something goes wrong

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
