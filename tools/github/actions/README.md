# GitHub Actions Utilities

Utility scripts for managing GitHub Actions workflows and runners.

## macOS Self-Hosted Runner Setup

### Quick Start (Auto-Mode)

```bash
./tools/github/actions/setup-macos-runner.sh
```

By default, this will:
1. Generate a runner token via `gh` CLI
2. Download and configure the runner
3. **Install and start the background service** automatically

### Prerequisites

1. **gh CLI** installed and authenticated:
   ```bash
   brew install gh
   gh auth login
   ```

2. **Admin access** to the repository (for token generation)

### Usage Options

```bash
# Auto-mode (default: generates token + installs service)
./tools/github/actions/setup-macos-runner.sh

# Auto-mode WITHOUT installing service (interactive run only)
./tools/github/actions/setup-macos-runner.sh --no-svc

# With custom runner version
./tools/github/actions/setup-macos-runner.sh --version 2.329.0

# With custom name and labels
./tools/github/actions/setup-macos-runner.sh --name my-mac-builder --labels "fast,ssd"

# Manual mode (explicit token)
./tools/github/actions/setup-macos-runner.sh <RUNNER_TOKEN>

# Help
./setup-macos-runner.sh --help
```

### Runner Labels

| Label | Purpose |
|-------|---------|
| `self-hosted` | Required by GitHub Actions |
| `macOS` | OS type |
| `arm64` / `x64` | Architecture |
| `groovy-lsp` | Project identifier |
| `local-macos` | CI targeting |

### CI Integration

Target local macOS runners:
- **Manual dispatch**: Set `runner_label` to `local-macos`
- **Automatic**: Set repo variable `CI_RUNNER_LABEL=local-macos`
