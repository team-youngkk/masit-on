# 운영 앱은 고정된 단일 EC2에서 실행한다. 기존 인스턴스를 이 리소스에
# import한 뒤 EIP를 연결해야 cutover 중 공인 주소와 Route53 record가 바뀌지 않는다.
resource "aws_instance" "app" {
  ami                         = var.ami_id
  instance_type               = var.instance_type
  subnet_id                   = data.aws_subnet.direct_app.id
  vpc_security_group_ids      = [aws_security_group.app.id, aws_security_group.direct_app.id]
  iam_instance_profile        = aws_iam_instance_profile.app.name
  associate_public_ip_address = true
  # 단일 EC2에서는 CloudWatch 상세 모니터링을 사용하지 않아 기본 모니터링을 쓴다.
  monitoring                  = false

  user_data = local.rendered_direct_user_data
  # user_data 변경이 기존 운영 인스턴스 교체로 이어지지 않게 한다. 앱 배포와
  # 롤백은 Docker Hub + SSH 경로가 소유하고, bootstrap 변경은 별도 cutover로 적용한다.
  user_data_replace_on_change = false

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
    Name = "${var.name_prefix}-app"
    Role = "application"
  }
}

# ALB가 사라져도 DNS가 EC2의 임시 public IP를 가리키지 않도록 EIP를 사용한다.
resource "aws_eip" "app" {
  domain = "vpc"

  tags = {
    Name = "${var.name_prefix}-app"
  }
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}
