resource "aws_instance" "app" {
  ami                         = var.ami_id
  instance_type               = var.app_instance_type
  subnet_id                   = data.aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.app.name
  associate_public_ip_address = true
  monitoring                  = true

  user_data = templatefile("${path.module}/templates/app-user-data.sh.tftpl", {
    aws_region      = var.aws_region
    deps_private_ip = aws_instance.deps.private_ip
    db_host         = aws_db_instance.performance.address
    db_port         = aws_db_instance.performance.port
    db_name         = var.db_name
    db_username     = var.db_username
    db_param_name   = aws_ssm_parameter.db_password.name
    app_image_uri   = var.app_image_uri
  })

  user_data_replace_on_change = true

  root_block_device {
    encrypted             = true
    volume_size           = var.root_volume_size_gib
    volume_type           = "gp3"
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }

  tags = {
    Name = local.app_name
    Role = "application-under-test"
  }
}

# WireMock과 Redis 전용 인스턴스다. 운영 앱 인스턴스는 두 자원을 동거시키지
# 않으므로 성능 환경도 분리해야 호스트 메모리 여유와 Redis 왕복이 운영과
# 같아진다. 근거는 docs/08-planning/post-cutover-runtime-baseline.md 5절이다.
resource "aws_instance" "deps" {
  ami                         = var.ami_id
  instance_type               = var.deps_instance_type
  subnet_id                   = data.aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.deps.id]
  iam_instance_profile        = aws_iam_instance_profile.deps.name
  associate_public_ip_address = true
  monitoring                  = true

  user_data = templatefile("${path.module}/templates/deps-user-data.sh.tftpl", {
    wiremock_image          = var.wiremock_image
    wiremock_fixture_commit = var.wiremock_fixture_commit
    wiremock_fixture_sha256 = var.wiremock_fixture_sha256
    redis_image             = var.redis_image
  })

  user_data_replace_on_change = true

  root_block_device {
    encrypted             = true
    volume_size           = var.root_volume_size_gib
    volume_type           = "gp3"
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }

  tags = {
    Name = local.deps_name
    Role = "performance-dependencies"
  }
}

resource "aws_instance" "loadgen" {
  ami                         = var.ami_id
  instance_type               = "t4g.small"
  subnet_id                   = data.aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.loadgen.id]
  iam_instance_profile        = aws_iam_instance_profile.loadgen.name
  associate_public_ip_address = true
  monitoring                  = true

  user_data = templatefile("${path.module}/templates/loadgen-user-data.sh.tftpl", {
    k6_version      = var.k6_version
    k6_arm64_sha256 = var.k6_arm64_sha256
  })

  user_data_replace_on_change = true

  root_block_device {
    encrypted             = true
    volume_size           = var.root_volume_size_gib
    volume_type           = "gp3"
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }

  tags = {
    Name = local.loadgen_name
    Role = "k6-load-generator"
  }
}
