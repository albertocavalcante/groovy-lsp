#!/usr/bin/env bash
# wait-for-runner.sh - Polls GitHub API until a self-hosted runner with specified label is online
#
# Required environment variables:
#   GITHUB_TOKEN        - GitHub PAT with repo scope
#   LABEL_NAME          - Runner label to wait for
#   MAX_WAIT_MINUTES    - Maximum wait time in minutes
#   POLL_INTERVAL       - Seconds between API calls
#   GITHUB_REPOSITORY   - Owner/repo (automatically set by GitHub Actions)
#
# Outputs (set via GITHUB_OUTPUT):
#   runner_name   - Name of the runner that came online
#   runner_status - Status of the runner (online)

set -euo pipefail

# ============================================================================
# Configuration
# ============================================================================
OWNER="${GITHUB_REPOSITORY%/*}"
REPO="${GITHUB_REPOSITORY#*/}"
MAX_ATTEMPTS=$((MAX_WAIT_MINUTES * 60 / POLL_INTERVAL))

echo "::group::⚙️ Configuration"
echo "  Repository:    ${OWNER}/${REPO}"
echo "  Label:         ${LABEL_NAME}"
echo "  Max wait:      ${MAX_WAIT_MINUTES} minutes (${MAX_ATTEMPTS} attempts)"
echo "  Poll interval: ${POLL_INTERVAL} seconds"
echo "::endgroup::"

# ============================================================================
# Polling Loop
# ============================================================================
attempt=1
while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
  echo "⏳ Attempt ${attempt}/${MAX_ATTEMPTS}..."
  
  # Fetch runners from GitHub API
  response=$(curl -sS --fail-with-body \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/${OWNER}/${REPO}/actions/runners?per_page=100" 2>&1) || {
    echo "::warning::GitHub API request failed: ${response}"
    sleep "$POLL_INTERVAL"
    attempt=$((attempt + 1))
    continue
  }
  
  # Check for API error messages
  if echo "$response" | jq -e '.message' > /dev/null 2>&1; then
    error_msg=$(echo "$response" | jq -r '.message')
    echo "::warning::GitHub API error: ${error_msg}"
    sleep "$POLL_INTERVAL"
    attempt=$((attempt + 1))
    continue
  fi
  
  # Look for online runner with matching label
  runner_info=$(echo "$response" | jq -e --arg label "$LABEL_NAME" \
    '.runners[] | select(any(.labels[]?; .name == $label) and .status == "online")' 2>/dev/null) || runner_info=""
  
  if [ -n "$runner_info" ]; then
    runner_name=$(echo "$runner_info" | jq -r '.name')
    runner_status=$(echo "$runner_info" | jq -r '.status')
    
    echo ""
    echo "✅ Found online runner with label '${LABEL_NAME}'!"
    echo ""
    echo "::group::📋 Runner Details"
    echo "$runner_info" | jq '{name: .name, status: .status, labels: [.labels[].name], busy: .busy}'
    echo "::endgroup::"
    
    # Set outputs for downstream steps
    {
      echo "runner_name=${runner_name}"
      echo "runner_status=${runner_status}"
    } >> "$GITHUB_OUTPUT"
    
    # Write job summary
    {
      echo "## 🏃 Runner Ready"
      echo ""
      echo "| Property | Value |"
      echo "| :--- | :--- |"
      echo "| **Name** | \`${runner_name}\` |"
      echo "| **Status** | ${runner_status} |"
      echo "| **Label** | \`${LABEL_NAME}\` |"
      echo "| **Wait Time** | ~$((attempt * POLL_INTERVAL)) seconds |"
    } >> "$GITHUB_STEP_SUMMARY"
    
    exit 0
  fi
  
  echo "  Runner not ready yet. Sleeping for ${POLL_INTERVAL}s..."
  sleep "$POLL_INTERVAL"
  attempt=$((attempt + 1))
done

# ============================================================================
# Timeout
# ============================================================================
echo ""
echo "❌ Timeout: No online runner found with label '${LABEL_NAME}' after ${MAX_WAIT_MINUTES} minutes"
echo ""

# Write failure to job summary
{
  echo "## ❌ Runner Wait Timeout"
  echo ""
  echo "No runner with label \`${LABEL_NAME}\` came online within ${MAX_WAIT_MINUTES} minutes."
  echo ""
  echo "**Troubleshooting:**"
  echo "- Check if the Terraform apply completed successfully"
  echo "- Verify the runner VM has network connectivity"
  echo "- Check runner registration logs in Magalu Cloud console"
} >> "$GITHUB_STEP_SUMMARY"

exit 1
