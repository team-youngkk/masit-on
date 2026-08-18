resource "aws_lb" "app" {
  name               = local.alb_name
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.alb_subnet_ids
}

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

resource "aws_lb_target_group" "green" {
  name        = local.green_target_name
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

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = var.acm_certificate_arn == null ? "forward" : "redirect"

    dynamic "forward" {
      for_each = var.acm_certificate_arn == null ? [1] : []
      content {
        target_group {
          arn = aws_lb_target_group.blue.arn
        }
      }
    }

    dynamic "redirect" {
      for_each = var.acm_certificate_arn != null ? [1] : []
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count             = var.acm_certificate_arn != null ? 1 : 0
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = var.acm_certificate_arn
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.blue.arn
  }
}
