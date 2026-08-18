# 전용 Redis 인스턴스. 퍼블릭 IP를 붙이지 않고 사설 subnet에만 둔다
# (ADR-DATA-005 10·11절). 복제가 없는 단일 장애점이므로 Redis 장애는
# fail-closed로 전면 인증 중단이 된다(배포 고도화 영향 검토 6.3절).
resource "aws_instance" "redis" {
  ami                         = var.redis_ami_id
  instance_type               = var.redis_instance_type
  subnet_id                   = var.redis_subnet_id
  vpc_security_group_ids      = [aws_security_group.redis.id]
  iam_instance_profile        = aws_iam_instance_profile.redis.name
  associate_public_ip_address = false

  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/templates/redis-user-data.sh.tftpl", {
    aws_region    = var.aws_region
    assets_bucket = var.redis_assets_bucket
    assets_prefix = var.redis_assets_prefix
    asset_files   = local.redis_asset_files
  })

  root_block_device {
    encrypted             = true
    volume_size           = var.redis_root_volume_size_gib
    volume_type           = "gp3"
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    instance_metadata_tags      = "disabled"
  }

  tags = {
    Name = "${var.name_prefix}-redis"
  }

  depends_on = [
    aws_s3_object.redis_asset,
    aws_vpc_endpoint.s3,
    aws_vpc_endpoint.ssm,
    aws_iam_role_policy.redis_read,
    aws_vpc_security_group_egress_rule.redis_to_vpce,
    aws_vpc_security_group_egress_rule.redis_to_s3,
    aws_vpc_security_group_egress_rule.redis_to_vpc_dns_udp,
    aws_vpc_security_group_egress_rule.redis_to_vpc_dns_tcp,
  ]
}

# 애플리케이션과 배포 스크립트가 읽는 접속 정보다. 비밀값이 아니므로 String으로 둔다.
resource "aws_ssm_parameter" "redis_host" {
  count = var.manage_host_parameter ? 1 : 0

  name        = var.host_parameter_name
  type        = "String"
  value       = aws_instance.redis.private_ip
  description = "Dedicated private Redis host"
  overwrite   = true
}

resource "aws_ssm_parameter" "redis_port" {
  name        = var.port_parameter_name
  type        = "String"
  value       = tostring(var.redis_port)
  description = "Dedicated private Redis port"
  overwrite   = true
}
