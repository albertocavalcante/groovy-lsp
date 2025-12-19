# Output Values
# Useful information about the provisioned runner infrastructure

output "runner_info" {
  description = "Summary of provisioned runner configuration"
  value = {
    runner_count  = 1
    runner_prefix = "groovy-lsp-ci"
    labels        = ["self-hosted", "magalu", "groovy-lsp"]
    machine_type  = var.machine_type
    region        = "br-ne1"
  }
}
