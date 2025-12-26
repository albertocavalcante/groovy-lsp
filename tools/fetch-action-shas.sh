#!/bin/bash
# List of actions to check
declare -a actions=(
    "actions/checkout"
    "actions/setup-java"
    "gradle/actions" # for gradle/actions/setup-gradle
    "actions/setup-node"
    "actions/upload-artifact"
    "actions/download-artifact"
    "softprops/action-gh-release"
    "mislav/bump-homebrew-formula-action"
)

echo "Resolving latest versions..."

for repo in "${actions[@]}"; do
    # Try getting the latest release first
    TAG=$(gh release list --repo "$repo" --limit 1 --exclude-drafts --exclude-pre-releases --json tagName --jq '.[0].tagName')
    
    # If no release found, try tags (some actions don't use releases)
    if [ -z "$TAG" ] || [ "$TAG" == "null" ]; then
        TAG=$(gh api "repos/$repo/tags" --jq '.[0].name')
    fi
    
    if [ -n "$TAG" ] && [ "$TAG" != "null" ]; then
        # Get the commit SHA for the tag
        # We need to handle if the tag is lightweight or annotated, checking the ref is safest
        SHA=$(gh api "repos/$repo/git/ref/tags/$TAG" --jq '.object.sha')
        
        # If it's an annotated tag, we might have gotten the tag object SHA, not commit SHA
        # Let's double check type. If "tag", assume we need to dereference? 
        # Actually simplest is typical "commits/TAG" endpoint:
        SHA=$(gh api "repos/$repo/commits/$TAG" --jq '.sha')
        
        echo "$repo|$SHA|$TAG"
    else
        echo "$repo|ERROR|Could not find tag"
    fi
done
