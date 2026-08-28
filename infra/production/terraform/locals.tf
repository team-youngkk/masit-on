locals {
  common_tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
    Service     = "masiton"
  }

  blue_target_name   = "${var.name_prefix}-blue"
  blue_asg_name      = "${var.name_prefix}-blue-asg"
  route53_record_key = var.route53_zone_id != null && var.route53_record_name != null ? "enabled" : "disabled"
}
