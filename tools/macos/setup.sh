#!/bin/bash
set -e

echo "🔧 Setting up macOS Development Environment with SDKMAN..."

# Function to check if a command exists
command_exists() {
    type "$1" &> /dev/null
}

# Install SDKMAN via Homebrew
if ! command_exists sdk; then
    # SDKMAN is not in the default path, check if installed via brew but not sourced
    if [ -d "$(brew --prefix)/opt/sdkman-cli" ] || [ -d "$HOME/.sdkman" ]; then
         echo "✅ SDKMAN appears to be installed."
    else
         echo "📦 Installing SDKMAN via Homebrew..."
         # SDKMAN is often in a specific tap
         brew tap sdkman/tap
         brew install sdkman-cli
    fi
    
    # Init SDKMAN (Brew installs it, but we still need to source it)
    export SDKMAN_DIR=$(brew --prefix sdkman-cli)/libexec
    if [ -s "${SDKMAN_DIR}/bin/sdkman-init.sh" ]; then
        source "${SDKMAN_DIR}/bin/sdkman-init.sh"
    else
        echo "❌ Could not find sdkman-init.sh at ${SDKMAN_DIR}/bin/sdkman-init.sh"
        exit 1
    fi
else
    # SDKMAN already installed, try to source it
    export SDKMAN_DIR=$(brew --prefix sdkman-cli)/libexec
    if [ -s "${SDKMAN_DIR}/bin/sdkman-init.sh" ]; then
        source "${SDKMAN_DIR}/bin/sdkman-init.sh"
    elif [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
        source "$HOME/.sdkman/bin/sdkman-init.sh"
    fi
    echo "✅ SDKMAN already installed"
fi

# Install Java version from .sdkmanrc
if [ -f ".sdkmanrc" ]; then
    echo "📦 Installing SDKs from .sdkmanrc..."
    sdk env install
else
    echo "❌ Error: .sdkmanrc not found! This project requires an .sdkmanrc file to define the Java version."
    exit 1
fi

# Install direnv if missing
if ! command_exists direnv; then
    echo "📦 Installing direnv..."
    brew install direnv
else
    echo "✅ direnv already installed"
fi

echo ""
echo "🎉 Setup complete!"
echo ""
echo "⚠️  NOTE: The 'sdk' command is not yet available in your current shell."
echo ""
echo "👉 OPTION 1: To use 'sdk' immediately, run this command:"
echo "   export SDKMAN_DIR=\$(brew --prefix sdkman-cli)/libexec && source \"\$SDKMAN_DIR/bin/sdkman-init.sh\""
echo ""
echo "👉 OPTION 2: To enable automatic JAVA_HOME switching (Recommended):"
echo "   direnv allow"
echo ""
echo "   (If direnv doesn't work, ensure you have hooked it into your shell: https://direnv.net/docs/hook.html)"
