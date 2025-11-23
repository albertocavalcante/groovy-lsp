#!/bin/bash
set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

DEVCONTAINER_JSON=".devcontainer/devcontainer.json"

if [ ! -f "$DEVCONTAINER_JSON" ]; then
    echo -e "${RED}Error: $DEVCONTAINER_JSON not found.${NC}"
    exit 1
fi

echo -e "${BLUE}Checking for tool updates...${NC}"

# Helper to get current version from devcontainer.json
get_current_version() {
    local key=$1
    grep "\"$key\"" "$DEVCONTAINER_JSON" | cut -d: -f2 | tr -d ' ",s'
}

# Helper to get latest version from GitHub
get_latest_version() {
    local repo=$1
    local tag=$(gh release list --repo "$repo" --limit 1 | cut -f3)
    # Remove 'v' prefix or 'jq-' prefix if present to match version number format
    echo "$tag" | sed 's/^v//' | sed 's/^jq-//'
}

check_tool() {
    local name=$1
    local key=$2
    local repo=$3
    
    local current=$(get_current_version "$key")
    local latest=$(get_latest_version "$repo")
    
    if [ "$current" == "$latest" ]; then
        echo -e "${GREEN}✔ $name is up to date: $current${NC}"
    else
        echo -e "${YELLOW}➜ $name update available: $current -> $latest${NC}"
    fi
}

# Check Tools
check_tool "jq"      "JQ_VERSION"  "jqlang/jq"
check_tool "ripgrep" "RG_VERSION"  "BurntSushi/ripgrep"
check_tool "fd"      "FD_VERSION"  "sharkdp/fd"
check_tool "bat"     "BAT_VERSION" "sharkdp/bat"
check_tool "fzf"     "FZF_VERSION" "junegunn/fzf"

