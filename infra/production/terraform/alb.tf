resource "aws_lb_target_group" "blue" {
  name        = local.blue_target_name
  port        = var.app_port
  protocol    = var.app_protocol
  target_type = "instance"
  vpc_id      = data.aws_vpc.existing.id

  health_check {
    enabled             = true
    path                = var.health_check_path
    protocol            = var.app_protocol
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}
