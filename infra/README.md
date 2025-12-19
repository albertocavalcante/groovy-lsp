# Infrastructure (Self-Hosted Runners)

This directory contains the Terraform/OpenTofu configuration to provision ephemeral GitHub Actions runners on Magalu Cloud.

## Architecture

```
┌─────────────────────────────┐     ┌─────────────────────────────┐
│  runner-provision.yml       │     │  runner-destroy.yml         │
│  (Manual trigger)           │     │  (Manual + Nightly cleanup) │
│                             │     │                             │
│  Provisions Magalu Cloud VM │     │  Tears down VM              │
│  Registers GitHub runner    │     │  Deregisters runner         │
└─────────────────────────────┘     └─────────────────────────────┘
            │                                    │
            └──────────┬─────────────────────────┘
                       │
                       ▼
              ┌─────────────────┐
              │     ci.yml      │
              │                 │
              │ runner_label:   │
              │   - self-hosted │
              │   - magalu      │
              │   - cloud       │
              └─────────────────┘
```

## Workflow Usage

### 1. Provision a Runner
```bash
# Via GitHub UI: Actions → "Infra: Provision Magalu Runner" → Run workflow
# Or via CLI:
gh workflow run runner-provision.yml
```

### 2. Run CI on Magalu
```bash
# Via GitHub UI: Actions → CI → Run workflow → runner_label: magalu
# Or via CLI:
gh workflow run ci.yml -f runner_label=magalu
```

### 3. Destroy Runner (when done)
```bash
# Via GitHub UI: Actions → "Infra: Destroy Magalu Runner" → Run workflow
# Or wait for nightly cleanup at 3 AM UTC
gh workflow run runner-destroy.yml
```

## Required Secrets

To run the Magalu Runner workflows, the following repository secrets must be set in GitHub:

| Secret | Description |
| :--- | :--- |
| `TF_API_TOKEN` | Terraform Cloud User API Token (for state management) |
| `GH_PAT_RUNNER` | GitHub Personal Access Token for runner registration. **Required scopes:** `repo` (for repo-level runners) or `admin:org` (for org-level runners). See [GitHub docs](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/autoscaling-with-self-hosted-runners#authentication-requirements). |
| `MGC_API_KEY` | Magalu Cloud API Key (for provisioning VMs) |

## GitHub Environments

The workflows use GitHub Environments for approval gates:

| Environment | Purpose |
| :--- | :--- |
| `production` | Manual approval required for infrastructure changes |
| `cleanup` | Auto-approval for scheduled nightly cleanup |

Configure environments in: Settings → Environments

## Terraform State

State is managed remotely via Terraform Cloud:
- Organization: `alberto`
- Workspace: `groovy-lsp-runner`
