locals {
  common_tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
    Service     = "masiton"
  }

  alb_name             = "${var.name_prefix}-alb"
  blue_target_name     = "${var.name_prefix}-blue"
  green_target_name    = "${var.name_prefix}-green"
  blue_asg_name        = "${var.name_prefix}-blue-asg"
  codedeploy_app       = "${var.name_prefix}-codedeploy"
  codedeploy_group     = "${var.name_prefix}-deployment-group"
  route53_record_key   = var.route53_zone_id != null && var.route53_record_name != null ? "enabled" : "disabled"
  traffic_listener_arn = var.acm_certificate_arn != null ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn
}
