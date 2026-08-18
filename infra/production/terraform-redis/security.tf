resource "aws_security_group" "redis" {
  name        = "${var.name_prefix}-redis-sg"
  description = "masit-on dedicated private Redis: 6379 from app SG only"
  vpc_id      = data.aws_vpc.existing.id
}

resource "aws_security_group" "vpce" {
  name        = "${var.name_prefix}-vpce-sg"
  description = "masit-on interface VPC endpoints: 443 from private workloads only"
  vpc_id      = data.aws_vpc.existing.id
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_app" {
  for_each = toset(var.app_security_group_ids)

  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = each.value
  from_port                    = var.redis_port
  to_port                      = var.redis_port
  ip_protocol                  = "tcp"
  description                  = "masiton-prod app ASG to dedicated Redis"
}

# 관리 접속은 EC2 Instance Connect Endpoint로만 들어온다. 퍼블릭 IP가 없고
# SSM Agent용 ssmmessages/ec2messages endpoint를 두지 않기 때문이다.
resource "aws_vpc_security_group_ingress_rule" "redis_from_eice" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.vpce.id
  from_port                    = 22
  to_port                      = 22
  ip_protocol                  = "tcp"
  description                  = "EC2 Instance Connect Endpoint management access"
}

resource "aws_vpc_security_group_ingress_rule" "vpce_from_redis" {
  security_group_id            = aws_security_group.vpce.id
  referenced_security_group_id = aws_security_group.redis.id
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
  description                  = "dedicated Redis to SSM interface endpoint"
}

# private DNS를 켠 인터페이스 endpoint는 VPC 전역에서 서비스 도메인을 가로챈다.
# SSM Agent를 쓰는 기존 인스턴스의 SG를 빼면 그 인스턴스의 SSM 제어 경로가 끊긴다.
# 2026-08-18에 이 규칙 누락으로 기존 운영 인스턴스가 약 5분간 SSM에서 이탈했다.
resource "aws_vpc_security_group_ingress_rule" "vpce_from_clients" {
  for_each = toset(var.ssm_endpoint_client_security_group_ids)

  security_group_id            = aws_security_group.vpce.id
  referenced_security_group_id = each.value
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
  description                  = "SSM interface endpoint client"
}

# EC2 Instance Connect Endpoint가 대상 인스턴스의 22로 나가는 경로다.
resource "aws_vpc_security_group_egress_rule" "vpce_to_redis_ssh" {
  security_group_id            = aws_security_group.vpce.id
  referenced_security_group_id = aws_security_group.redis.id
  from_port                    = 22
  to_port                      = 22
  ip_protocol                  = "tcp"
  description                  = "EICE to dedicated Redis SSH"
}

resource "aws_vpc_security_group_egress_rule" "redis_to_vpce" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.vpce.id
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
  description                  = "Redis to SSM interface endpoint for Parameter Store"
}

resource "aws_vpc_security_group_egress_rule" "redis_to_s3" {
  security_group_id = aws_security_group.redis.id
  prefix_list_id    = data.aws_prefix_list.s3.id
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "Redis to S3 gateway endpoint for deploy assets"
}

resource "aws_vpc_security_group_egress_rule" "redis_to_vpc_dns_udp" {
  security_group_id = aws_security_group.redis.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "udp"
  description       = "VPC DNS resolution"
}

resource "aws_vpc_security_group_egress_rule" "redis_to_vpc_dns_tcp" {
  security_group_id = aws_security_group.redis.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "tcp"
  description       = "VPC DNS resolution fallback"
}
