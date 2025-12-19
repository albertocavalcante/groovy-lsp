# Infrastructure (Self-Hosted Runners)

This directory contains the Terraform/OpenTofu configuration to provision ephemeral GitHub Actions runners on Magalu Cloud.

## Required Secrets

To run the `Magalu Runner Integration` workflow, the following repository secrets must be set in GitHub:

| Secret | Description |
| :--- | :--- |
| `TF_API_TOKEN` | Terraform Cloud User API Token (for state management) |
| `GH_PAT_RUNNER` | GitHub PAT with `repo` scope (used by the runner to register itself) |
| `MGC_API_KEY` | Magalu Cloud API Key (for provisioning VMs) |

## Workflow

The workflow is defined in `.github/workflows/runner-infra.yml`:
1.  **Provision**: Creates a VM on Magalu Cloud.
2.  **Test**: Runs `./gradlew test` on the new `self-hosted` runner.
3.  **Destroy**: Tears down the VM independently of test success/failure.
