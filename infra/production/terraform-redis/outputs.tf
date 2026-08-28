output "redis_instance_id" {
  value = aws_instance.redis.id
}

output "redis_private_ip" {
  description = "애플리케이션이 접속할 Redis 사설 IP"
  value       = aws_instance.redis.private_ip
}

output "redis_security_group_id" {
  description = "운영 앱 모듈의 redis_security_group_id 입력값"
  value       = aws_security_group.redis.id
}

output "vpc_endpoint_ids" {
  value = {
    s3   = aws_vpc_endpoint.s3.id
    eice = aws_ec2_instance_connect_endpoint.management.id
  }
}
