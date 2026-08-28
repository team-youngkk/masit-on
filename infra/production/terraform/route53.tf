# Route53는 초기에는 기존 ALB를 계속 가리킨다. 직접 EC2 경로의 외부 smoke와
# health를 확인한 뒤 direct_traffic_enabled=true로 바꾸는 별도 plan에서 EIP로
# 전환한다. state-migrations.tf가 기존 record 주소를 보존한다.
resource "aws_route53_record" "app" {
  for_each = local.route53_record_key == "enabled" ? { enabled = true } : {}

  zone_id = var.route53_zone_id
  name    = var.route53_record_name
  type    = "A"

  ttl     = var.direct_traffic_enabled ? 60 : null
  records = var.direct_traffic_enabled ? [aws_eip.app.public_ip] : null

  dynamic "alias" {
    for_each = var.direct_traffic_enabled ? [] : [true]

    content {
      name                   = aws_lb.app.dns_name
      zone_id                = aws_lb.app.zone_id
      evaluate_target_health = var.route53_evaluate_target_health
    }
  }
}
