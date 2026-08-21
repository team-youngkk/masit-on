resource "aws_security_group" "app" {
  name        = "${local.app_name}-sg"
  description = "Issue #207 isolated performance app only"
  vpc_id      = data.aws_vpc.existing.id
}

resource "aws_security_group" "loadgen" {
  name        = "${local.loadgen_name}-sg"
  description = "Issue #207 isolated performance load generator only"
  vpc_id      = data.aws_vpc.existing.id
}

resource "aws_security_group" "deps" {
  name        = "${local.deps_name}-sg"
  description = "Issue #207 isolated performance dependencies (WireMock, Redis) only"
  vpc_id      = data.aws_vpc.existing.id
}

resource "aws_security_group" "db" {
  name        = "${local.db_name}-sg"
  description = "Issue #207 isolated performance RDS only; no egress"
  vpc_id      = data.aws_vpc.existing.id
}

resource "aws_vpc_security_group_egress_rule" "app_https" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "HTTPS for package, image, fixture and SSM access"
}

resource "aws_vpc_security_group_egress_rule" "app_dns_udp" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "udp"
  description       = "VPC DNS resolution"
}

resource "aws_vpc_security_group_egress_rule" "app_dns_tcp" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "tcp"
  description       = "VPC DNS resolution fallback"
}

resource "aws_vpc_security_group_egress_rule" "app_to_db" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.db.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  description                  = "Only the isolated app may reach the performance database"
}

resource "aws_vpc_security_group_egress_rule" "loadgen_https" {
  security_group_id = aws_security_group.loadgen.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "HTTPS for k6 download and SSM access"
}

resource "aws_vpc_security_group_egress_rule" "loadgen_dns_udp" {
  security_group_id = aws_security_group.loadgen.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "udp"
  description       = "VPC DNS resolution"
}

resource "aws_vpc_security_group_egress_rule" "loadgen_dns_tcp" {
  security_group_id = aws_security_group.loadgen.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "tcp"
  description       = "VPC DNS resolution fallback"
}

resource "aws_vpc_security_group_egress_rule" "loadgen_to_app" {
  security_group_id            = aws_security_group.loadgen.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
  description                  = "Only the isolated load generator may call the app"
}

resource "aws_vpc_security_group_ingress_rule" "app_from_loadgen" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.loadgen.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
  description                  = "Only the isolated load generator may call the app"
}

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  description                  = "Only the isolated app may call the performance database"
}

resource "aws_vpc_security_group_egress_rule" "deps_https" {
  security_group_id = aws_security_group.deps.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "HTTPS for package, image, fixture and SSM access"
}

resource "aws_vpc_security_group_egress_rule" "deps_dns_udp" {
  security_group_id = aws_security_group.deps.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "udp"
  description       = "VPC DNS resolution"
}

resource "aws_vpc_security_group_egress_rule" "deps_dns_tcp" {
  security_group_id = aws_security_group.deps.id
  cidr_ipv4         = data.aws_vpc.existing.cidr_block
  from_port         = 53
  to_port           = 53
  ip_protocol       = "tcp"
  description       = "VPC DNS resolution fallback"
}

resource "aws_vpc_security_group_egress_rule" "app_to_deps_wiremock" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.deps.id
  from_port                    = 8081
  to_port                      = 8081
  ip_protocol                  = "tcp"
  description                  = "Only the isolated app may call WireMock"
}

resource "aws_vpc_security_group_egress_rule" "app_to_deps_redis" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.deps.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
  description                  = "Only the isolated app may call the performance Redis"
}

resource "aws_vpc_security_group_ingress_rule" "deps_wiremock_from_app" {
  security_group_id            = aws_security_group.deps.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 8081
  to_port                      = 8081
  ip_protocol                  = "tcp"
  description                  = "Only the isolated app may call WireMock"
}

resource "aws_vpc_security_group_ingress_rule" "deps_redis_from_app" {
  security_group_id            = aws_security_group.deps.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
  description                  = "Only the isolated app may call the performance Redis"
}
