#!/bin/bash
set -e

# Configuration
RUNNER_VERSION="2.329.0"

# Detect Architecture
ARCH_NAME=$(uname -m)
if [ "$ARCH_NAME" = "x86_64" ]; then
    RUNNER_ARCH="x64"
    # TODO: Add x64 hash if needed
    RUNNER_SHA256_HASH=""
elif [ "$ARCH_NAME" = "arm64" ]; then
    RUNNER_ARCH="arm64"
    RUNNER_SHA256_HASH="50c0d409040cc52e701ac1d5afb4672cb7803a65c1292a30e96c42051dfa690f"
else
    echo "Unsupported architecture: $ARCH_NAME"
    exit 1
fi

RUNNER_DIR="$HOME/actions-runner"
REPO_URL="https://github.com/albertocavalcante/groovy-lsp"

if [ -z "$1" ]; then
  echo "Usage: $0 <RUNNER_TOKEN> [RUNNER_NAME]"
  echo ""
  echo "Arguments:"
  echo "  <RUNNER_TOKEN>  Required. Get this from:"
  echo "                  $REPO_URL/settings/actions/runners/new"
  echo "  [RUNNER_NAME]   Optional. Name for the runner (default: $(hostname))"
  echo ""
  exit 1
fi

TOKEN="$1"
RUNNER_NAME="${2:-$(hostname)}"

echo "⚙️  Setting up GitHub Actions Runner for macOS ($RUNNER_ARCH)..."
echo "📂 Target Directory: $RUNNER_DIR"

# Create directory
if [ -d "$RUNNER_DIR" ]; then
    echo "⚠️  Directory $RUNNER_DIR already exists."
    read -p "Do you want to remove it and start fresh? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "cleaning up..."
        rm -rf "$RUNNER_DIR"
        mkdir -p "$RUNNER_DIR"
    else
        echo "Using existing directory... (Setup might fail if already configured)"
    fi
else
    mkdir -p "$RUNNER_DIR"
fi

cd "$RUNNER_DIR"

# Download
TAR_FILE="actions-runner-osx-${RUNNER_ARCH}-${RUNNER_VERSION}.tar.gz"
DOWNLOAD_URL="https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/${TAR_FILE}"

if [ ! -f "$TAR_FILE" ]; then
    echo "📥 Downloading runner v${RUNNER_VERSION}..."
    echo "   URL: $DOWNLOAD_URL"
    curl -o "$TAR_FILE" -L "$DOWNLOAD_URL"
else
    echo "✅ Runner tarball already present."
fi

# Validate hash
if [ -n "$RUNNER_SHA256_HASH" ]; then
    echo "${RUNNER_SHA256_HASH}  $TAR_FILE" | shasum -a 256 -c
else
    echo "⚠️  Skipping hash validation (no hash provided for $ARCH_NAME)"
fi

# Extract
echo "📦 Extracting..."
tar xzf "./$TAR_FILE"

# Configure
echo "⚙️  Configuring runner..."
# --replace allows overwriting an existing runner with the same name
./config.sh \
    --url "$REPO_URL" \
    --token "$TOKEN" \
    --name "$RUNNER_NAME" \
    --labels "self-hosted,macOS,${RUNNER_ARCH},local-dev" \
    --work "_work" \
    --unattended \
    --replace

echo ""
echo "✅ Setup complete!"
echo "------------------------------------------------------------------"
echo "To start the runner interactively:"
echo "  cd $RUNNER_DIR && ./run.sh"
echo ""
echo "To install as a background service:"
echo "  cd $RUNNER_DIR"
echo "  ./svc.sh install"
echo "  ./svc.sh start"
echo "------------------------------------------------------------------"
