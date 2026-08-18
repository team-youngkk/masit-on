resource "aws_route53_record" "alb" {
  for_each = local.route53_record_key == "enabled" && var.initial_blue_verified ? { enabled = true } : {}

  zone_id = var.route53_zone_id
  name    = var.route53_record_name
  type    = "A"

  alias {
    name                   = aws_lb.app.dns_name
    zone_id                = aws_lb.app.zone_id
    evaluate_target_health = var.route53_evaluate_target_health
  }
}
