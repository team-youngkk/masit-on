resource "aws_launch_template" "blue" {
  name_prefix   = "${var.name_prefix}-blue-"
  image_id      = var.ami_id
  instance_type = var.instance_type
  user_data     = base64encode(local.rendered_user_data)

  iam_instance_profile {
    name = aws_iam_instance_profile.app.name
  }

  # public app subnet 모드에서는 subnet의 map_public_ip_on_launch 설정과 무관하게
  # replacement 인스턴스가 인터넷으로 나갈 수 있도록 public IPv4를 명시한다.
  # private 모드에서는 NAT 경로를 data.tf postcondition으로 검증한다.
  network_interfaces {
    device_index                = 0
    associate_public_ip_address = !var.app_subnet_is_private
    security_groups             = [aws_security_group.app.id]
  }

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      encrypted             = true
      volume_size           = var.root_volume_size_gib
      volume_type           = "gp3"
      delete_on_termination = true
    }
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }

  tag_specifications {
    resource_type = "instance"

    tags = {
      Name = "${var.name_prefix}-blue"
    }
  }
}

resource "aws_autoscaling_group" "blue" {
  name                = local.blue_asg_name
  min_size            = var.blue_min_size
  desired_capacity    = var.blue_desired_capacity
  max_size            = var.blue_max_size
  vpc_zone_identifier = var.app_subnet_ids
  target_group_arns   = [aws_lb_target_group.blue.arn]
  health_check_type   = var.asg_health_check_type

  # CodeDeploy가 이 ASG를 복사할 때 version 값도 그대로 복사한다. 숫자로 고정하면
  # 교체 환경 체인이 그 버전에 머물러 이후 user_data 변경이 전파되지 않는다.
  launch_template {
    id      = aws_launch_template.blue.id
    version = "$Latest"
  }

  lifecycle {
    create_before_destroy = true
    ignore_changes        = [desired_capacity]
  }
}
