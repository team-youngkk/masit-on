output "app_instance_id" {
  description = "SSM 대상인 성능 검증 앱 EC2 ID"
  value       = aws_instance.app.id
}

output "loadgen_instance_id" {
  description = "SSM 대상인 k6 부하 생성기 EC2 ID"
  value       = aws_instance.loadgen.id
}

output "deps_instance_id" {
  description = "SSM 대상인 WireMock·Redis 의존 EC2 ID"
  value       = aws_instance.deps.id
}

output "deps_private_ip" {
  description = "의존 EC2 private IP. 앱 EC2에서만 8081·6379로 접근한다"
  value       = aws_instance.deps.private_ip
}

output "app_private_ip" {
  description = "앱 EC2 private IP. loadgen에서만 접근한다"
  value       = aws_instance.app.private_ip
}

output "rds_endpoint" {
  description = "성능 전용 RDS endpoint"
  value       = aws_db_instance.performance.address
}

output "rds_port" {
  description = "성능 전용 RDS port"
  value       = aws_db_instance.performance.port
}

output "db_password_parameter_name" {
  description = "성능 전용 DB 비밀번호의 SecureString Parameter 이름"
  value       = aws_ssm_parameter.db_password.name
}

output "resource_scope" {
  description = "이 state가 관리하는 리소스 경계"
  value = {
    environment = local.common_tags.Environment
    purpose     = local.common_tags.Purpose
    run_id      = var.run_id
    name_prefix = local.name_prefix
  }
}
