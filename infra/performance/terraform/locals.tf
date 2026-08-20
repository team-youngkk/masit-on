locals {
  name_prefix = "masiton-perf-207-${var.run_id}"

  common_tags = {
    Project     = "masit-on"
    Environment = "isolated-performance"
    Purpose     = "issue-207"
    RunId       = var.run_id
    ManagedBy   = "terraform"
  }

  app_name           = "${local.name_prefix}-app"
  deps_name          = "${local.name_prefix}-deps"
  loadgen_name       = "${local.name_prefix}-loadgen"
  db_name            = "${local.name_prefix}-db"
  ecr_repository_arn = "arn:aws:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${var.ecr_repository_name}"
}
