terraform {
  required_version = ">= 1.5.7"

  backend "remote" {
    hostname     = "app.terraform.io"
    organization = "alberto"

    workspaces {
      name = "groovy-lsp-runner"
    }
  }

  required_providers {
    mgc = {
      source = "magalucloud/mgc"
    }
  }
}

provider "mgc" {
  region = "br-se1" // SouthEast 1 (Standard)
}

module "runner" {
  source = "github.com/albertocavalcante/magalu-github-runner?ref=main"

  runner_count                 = 1
  runner_name_prefix           = "groovy-lsp-ci"
  github_repository_url        = "https://github.com/albertocavalcante/groovy-lsp"
  github_personal_access_token = var.github_token
  machine_type                 = "BV1-1-40" // 1vCPU, 4GB RAM (Sufficient for LSP tests)

  # Ephemeral labeling
  runner_labels = ["self-hosted", "magalu", "groovy-lsp"]
}
