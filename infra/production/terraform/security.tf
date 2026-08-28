resource "aws_security_group" "app" {
  name        = "${var.name_prefix}-app-sg"
  description = "Application instances reachable only from the ALB"
  vpc_id      = data.aws_vpc.existing.id
}

# 직접 EC2의 public ingress는 legacy ASG와 공유하지 않는다. outbound와 기존
# Redis/RDS 연결은 병행 기간 동안 app SG를 함께 붙여 유지하고, public 80/443은
# 이 SG만 직접 앱 인스턴스에 연결한다.
resource "aws_security_group" "direct_app" {
  name        = "${var.name_prefix}-direct-app-sg"
  description = "Internet ingress for the direct application instance"
  vpc_id      = data.aws_vpc.existing.id

  egress = []
}

# direct_traffic_enabled 전환 뒤에는 ALB가 아닌 인터넷이 EIP로 직접 들어온다.
# legacy ALB rule은 cutover가 끝날 때까지 보존하고, 직접 경로는 별도 CIDR rule로
# 준비해 DNS 전환 전에 SG 변경을 검증할 수 있게 한다.
resource "aws_vpc_security_group_ingress_rule" "app_http" {
  for_each          = toset(var.app_ingress_cidr_blocks)
  security_group_id = aws_security_group.direct_app.id
  cidr_ipv4         = each.value
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
  description       = "Direct HTTP ingress to the application instance"
}

resource "aws_vpc_security_group_ingress_rule" "app_https" {
  for_each          = toset(var.app_ingress_cidr_blocks)
  security_group_id = aws_security_group.direct_app.id
  cidr_ipv4         = each.value
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "Direct HTTPS ingress to the application instance"
}

resource "aws_vpc_security_group_egress_rule" "app_to_rds" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = var.rds_security_group_id
  from_port                    = var.rds_port
  to_port                      = var.rds_port
  ip_protocol                  = "tcp"
  description                  = "Application to existing RDS"
}

resource "aws_vpc_security_group_egress_rule" "app_to_redis" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = var.redis_security_group_id
  from_port                    = var.redis_port
  to_port                      = var.redis_port
  ip_protocol                  = "tcp"
  description                  = "Application to existing Redis"
}

# Spring Mail이 외부 SMTP relay로 나간다. DEPENDENCY_HEALTH_COMPONENTS에 mail이
# 있어 이 경로가 막히면 dependency health가 DOWN이 되고 runtime-health.sh가 실패해
# CodeDeploy ValidateService 단계에서 배포가 실패한다. relay IP를 특정할 수 없어
# 포트로만 좁힌다.
resource "aws_vpc_security_group_egress_rule" "app_to_smtp" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = var.mail_smtp_port
  to_port           = var.mail_smtp_port
  ip_protocol       = "tcp"
  description       = "Application to external SMTP relay"
}

resource "aws_vpc_security_group_egress_rule" "app_to_aws_services" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "Private subnet NAT or VPC endpoints for CodeDeploy, SSM, ECR, and S3"
}

resource "aws_vpc_security_group_egress_rule" "app_to_vpc_dns_udp" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "udp"
  description       = "VPC DNS resolution"
}

resource "aws_vpc_security_group_egress_rule" "app_to_vpc_dns_tcp" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "tcp"
  description       = "VPC DNS resolution fallback"
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_app" {
  count = var.manage_rds_ingress_rule ? 1 : 0

  security_group_id            = var.rds_security_group_id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = var.rds_port
  to_port                      = var.rds_port
  ip_protocol                  = "tcp"
  description                  = "Allow production application to existing RDS"
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_app" {
  count = var.manage_redis_ingress_rule ? 1 : 0

  security_group_id            = var.redis_security_group_id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = var.redis_port
  to_port                      = var.redis_port
  ip_protocol                  = "tcp"
  description                  = "Allow production application to existing Redis"
}

# PostgreSQL EC2 전환 중에는 기존 앱 경로와 direct 앱 경로를 함께 허용한다.
# legacy app rule은 기존 RDS SG와 새 DB SG가 다를 때만 별도로 만들지만,
# direct 앱은 DB SG를 재사용하는 경우에도 별도 source rule이 필요하다.
resource "aws_vpc_security_group_egress_rule" "app_to_database" {
  count = var.database_security_group_id == var.rds_security_group_id ? 0 : 1

  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = var.database_security_group_id
  from_port                    = var.database_port
  to_port                      = var.database_port
  ip_protocol                  = "tcp"
  description                  = "Application to PostgreSQL EC2"
}

resource "aws_vpc_security_group_ingress_rule" "database_from_app" {
  count = var.database_security_group_id == var.rds_security_group_id ? 0 : 1

  security_group_id            = var.database_security_group_id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = var.database_port
  to_port                      = var.database_port
  ip_protocol                  = "tcp"
  description                  = "Allow legacy production application to PostgreSQL EC2"
}

resource "aws_vpc_security_group_ingress_rule" "database_from_direct_app" {
  # PostgreSQL EC2가 기존 RDS SG를 재사용해도 direct 앱은 별도 SG 출처다.
  # target SG ID equality를 기준으로 이 rule을 끄면 direct 앱이 5432에
  # 접근하지 못한다.
  count = 1

  security_group_id            = var.database_security_group_id
  referenced_security_group_id = aws_security_group.direct_app.id
  from_port                    = var.database_port
  to_port                      = var.database_port
  ip_protocol                  = "tcp"
  description                  = "Allow direct production application to PostgreSQL EC2"
}
