output "target_group_arn" {
  value = aws_lb_target_group.blue.arn
}

output "autoscaling_group_names" {
  description = "이력 보존을 위해 유지하는 Terraform seed ASG 이름"
  value       = [aws_autoscaling_group.blue.name]
}

output "security_group_ids" {
  value = {
    app        = aws_security_group.app.id
    direct_app = aws_security_group.direct_app.id
  }
}

output "app_instance_id" {
  description = "Docker Hub + SSH 배포 대상인 운영 앱 EC2 ID"
  value       = aws_instance.app.id
}

output "app_public_ip" {
  description = "운영 앱 EC2에 연결된 EIP"
  value       = aws_eip.app.public_ip
}
