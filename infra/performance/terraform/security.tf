resource "aws_security_group" "app" {
  name        = "${local.app_name}-sg"
  description = "Issue #207 isolated performance app only"
  vpc_id      = data.aws_vpc.existing.id

  egress {
    description = "Bootstrap, ECR, SSM and runtime egress"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "loadgen" {
  name        = "${local.loadgen_name}-sg"
  description = "Issue #207 isolated performance load generator only"
  vpc_id      = data.aws_vpc.existing.id

  egress {
    description = "k6 and SSM egress"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db" {
  name        = "${local.db_name}-sg"
  description = "Issue #207 isolated performance RDS only"
  vpc_id      = data.aws_vpc.existing.id

  egress {
    description = "Stateful RDS response and AWS service egress"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
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
