resource "aws_db_subnet_group" "performance" {
  name       = "${local.db_name}-subnet-group"
  subnet_ids = [for subnet in data.aws_subnet.private : subnet.id]

  tags = {
    Name = "${local.db_name}-subnet-group"
  }

  lifecycle {
    precondition {
      condition     = length(toset([for subnet in data.aws_subnet.private : subnet.availability_zone])) >= 2
      error_message = "RDS private subnet은 서로 다른 가용 영역 2개 이상이어야 한다."
    }
  }
}

resource "aws_db_instance" "performance" {
  identifier                 = local.db_name
  engine                     = "postgres"
  engine_version             = "17.10"
  instance_class             = "db.t4g.micro"
  allocated_storage          = 20
  storage_type               = "gp3"
  storage_encrypted          = true
  db_name                    = var.db_name
  username                   = var.db_username
  password                   = var.db_password
  port                       = 5432
  db_subnet_group_name       = aws_db_subnet_group.performance.name
  vpc_security_group_ids     = [aws_security_group.db.id]
  publicly_accessible        = false
  multi_az                   = false
  backup_retention_period    = 0
  delete_automated_backups   = true
  deletion_protection        = false
  skip_final_snapshot        = true
  apply_immediately          = true
  auto_minor_version_upgrade = false
  copy_tags_to_snapshot      = true

  lifecycle {
    precondition {
      condition     = !startswith(local.db_name, "masiton-db")
      error_message = "운영 RDS 식별자와 겹치는 이름은 허용하지 않는다."
    }
  }
}
