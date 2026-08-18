output "alb_dns_name" {
  description = "운영 ALB DNS 이름"
  value       = aws_lb.app.dns_name
}

output "alb_zone_id" {
  description = "Route53 alias에 사용할 ALB zone ID"
  value       = aws_lb.app.zone_id
}

output "blue_green_target_group_arns" {
  description = "CodeDeploy blue/green target group ARN"
  value = {
    blue  = aws_lb_target_group.blue.arn
    green = aws_lb_target_group.green.arn
  }
}

output "autoscaling_group_names" {
  description = "CodeDeploy가 교체 환경의 기준으로 사용하는 원본 ASG 이름"
  value       = [aws_autoscaling_group.blue.name]
}

output "codedeploy_application_name" {
  value = aws_codedeploy_app.app.name
}

output "codedeploy_deployment_group_name" {
  value = aws_codedeploy_deployment_group.app.deployment_group_name
}

output "security_group_ids" {
  description = "이 모듈이 생성한 ALB/app security group ID"
  value = {
    alb = aws_security_group.alb.id
    app = aws_security_group.app.id
  }
}
