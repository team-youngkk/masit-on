resource "aws_ssm_parameter" "redis_password" {
  name        = "/masiton/perf-207/${var.run_id}/redis/password"
  description = "Issue #207 isolated performance Redis requirepass"
  type        = "SecureString"
  value       = var.redis_password
  tier        = "Standard"
}

resource "aws_ssm_parameter" "db_password" {
  name        = "/masiton/perf-207/${var.run_id}/db/password"
  description = "Issue #207 isolated performance RDS password"
  type        = "SecureString"
  value       = var.db_password
  tier        = "Standard"
}
