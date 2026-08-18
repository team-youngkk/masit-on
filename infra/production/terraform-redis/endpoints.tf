# S3 gateway endpoint는 무료이고 route table에 붙는다. Redis 설정 파일을 받는 경로다.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = data.aws_vpc.existing.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = var.private_route_table_ids

  tags = {
    Name = "${var.name_prefix}-s3-gateway"
  }
}

# redis-render-conf.sh가 매 기동마다 Parameter Store를 읽는다. 최초 프로비저닝용이
# 아니라 상시 필요한 경로다. 설정이 tmpfs에 있어 재기동마다 다시 렌더링해야 한다.
resource "aws_vpc_endpoint" "ssm" {
  vpc_id              = data.aws_vpc.existing.id
  service_name        = "com.amazonaws.${var.aws_region}.ssm"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [var.redis_subnet_id]
  security_group_ids  = [aws_security_group.vpce.id]
  private_dns_enabled = true

  tags = {
    Name = "${var.name_prefix}-ssm-endpoint"
  }
}

resource "aws_ec2_instance_connect_endpoint" "management" {
  subnet_id          = var.redis_subnet_id
  security_group_ids = [aws_security_group.vpce.id]

  # false로 두어야 대상 인스턴스가 보는 출처가 이 endpoint가 된다. true면 원래
  # 클라이언트 IP가 그대로 전달되어 22를 endpoint security group 출처로 허용한
  # 규칙에 매칭되지 않는다. 값을 바꾸면 endpoint가 교체된다.
  preserve_client_ip = false

  tags = {
    Name = "${var.name_prefix}-eice"
  }
}
