variable "github_token" {
  description = "PAT for registering the runner"
  type        = string
  sensitive   = true
}

variable "mgc_api_key" {
  description = "Magalu Cloud API Key"
  type        = string
  sensitive   = true
}
