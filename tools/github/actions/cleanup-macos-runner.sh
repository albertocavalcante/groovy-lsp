#!/bin/bash
# cleanup-macos-runner.sh - Clean up GitHub Actions runners for groovy-lsp
#
# This script will:
# 1. Find all groovy-lsp runner directories
# 2. Stop and uninstall services
# 3. Unregister runners from GitHub
# 4. Remove directories and optionally cache
#
# Usage:
#   ./cleanup-macos-runner.sh [--all] [--skip-unregister]

set -e

REPO_OWNER="albertocavalcante"
REPO_NAME="groovy-lsp"

# Parse arguments
CLEAN_CACHE=false
SKIP_UNREGISTER=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --all)
            CLEAN_CACHE=true
            shift
            ;;
        --skip-unregister)
            SKIP_UNREGISTER=true
            shift
            ;;
        --help|-h)
            cat << EOF
GitHub Actions Runner Cleanup

USAGE:
    $(basename "$0") [OPTIONS]

OPTIONS:
    --all              Also remove cached runner downloads
    --skip-unregister  Skip GitHub unregistration (faster, leaves orphaned entries)
    --help             Show this help

DESCRIPTION:
    Removes all groovy-lsp runners from this machine:
    - Stops and uninstalls services
    - Unregisters from GitHub (unless --skip-unregister)
    - Removes runner directories (~/.gha-runners/groovy-lsp-*)
    - Optionally removes cache (--all)

EOF
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "GitHub Actions Runner Cleanup"
echo ""

# Find all groovy-lsp runner directories
RUNNER_DIRS=($(find ~/.gha-runners -maxdepth 1 -type d -name "groovy-lsp-*" 2>/dev/null || true))

if [ ${#RUNNER_DIRS[@]} -eq 0 ]; then
    echo "No groovy-lsp runners found in ~/.gha-runners/"

    if [ "$CLEAN_CACHE" = true ]; then
        echo "Checking for cache..."
    else
        echo "Nothing to clean up."
        exit 0
    fi
else
    echo "Found ${#RUNNER_DIRS[@]} runner(s):"
    for dir in "${RUNNER_DIRS[@]}"; do
        echo "  - $(basename "$dir")"
    done
    echo ""

    read -p "Remove all runners? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Cancelled."
        exit 0
    fi
    echo ""

    # Process each runner directory
    for RUNNER_DIR in "${RUNNER_DIRS[@]}"; do
        echo "Processing: $(basename "$RUNNER_DIR")"
        cd "$RUNNER_DIR"

        # Stop and uninstall service
        if [ -f "./svc.sh" ]; then
            echo "  Stopping service..."
            ./svc.sh stop 2>/dev/null || echo "  (service not running)"

            echo "  Uninstalling service..."
            ./svc.sh uninstall 2>/dev/null || echo "  (service not installed)"
        fi

        # Unregister from GitHub
        if [ "$SKIP_UNREGISTER" = false ]; then
            if [ -f "./config.sh" ]; then
                echo "  Unregistering from GitHub..."

                # Generate removal token via gh CLI
                if command -v gh &> /dev/null && gh auth status &> /dev/null; then
                    REMOVAL_TOKEN=$(gh api \
                        --method POST \
                        -H "Accept: application/vnd.github+json" \
                        "/repos/${REPO_OWNER}/${REPO_NAME}/actions/runners/remove-token" \
                        --jq '.token' 2>/dev/null || true)

                    if [ -n "$REMOVAL_TOKEN" ]; then
                        ./config.sh remove --token "$REMOVAL_TOKEN" 2>/dev/null || echo "  (unregistration failed - may be orphaned)"
                    else
                        echo "  (could not get removal token - skipping unregistration)"
                    fi
                else
                    echo "  (gh CLI not available - skipping unregistration)"
                fi
            fi
        else
            echo "  Skipping unregistration (--skip-unregister)"
        fi

        echo "  Removing directory..."
        cd ~
        rm -rf "$RUNNER_DIR"
        echo "  Done."
        echo ""
    done
fi

# Clean cache if requested
if [ "$CLEAN_CACHE" = true ]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
    CACHE_DIR="$PROJECT_ROOT/.github/.cache/runners"

    if [ -d "$CACHE_DIR" ]; then
        echo "Cleaning cache: $CACHE_DIR"
        rm -rf "$CACHE_DIR"
        echo "Cache removed."
    else
        echo "No cache found at: $CACHE_DIR"
    fi
fi

echo ""
echo "Cleanup complete!"
