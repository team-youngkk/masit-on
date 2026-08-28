# DNS는 검증된 단일 앱 EC2의 EIP만 가리킨다.
resource "aws_route53_record" "app" {
  for_each = local.route53_record_key == "enabled" ? { enabled = true } : {}

  zone_id = var.route53_zone_id
  name    = var.route53_record_name
  type    = "A"

  ttl     = 60
  records = [aws_eip.app.public_ip]
}
