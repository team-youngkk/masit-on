resource "aws_codedeploy_app" "app" {
  name             = local.codedeploy_app
  compute_platform = "Server"
}

resource "aws_codedeploy_deployment_group" "app" {
  app_name               = aws_codedeploy_app.app.name
  deployment_group_name  = local.codedeploy_group
  service_role_arn       = aws_iam_role.codedeploy.arn
  deployment_config_name = "CodeDeployDefault.OneAtATime"

  autoscaling_groups = [
    aws_autoscaling_group.blue.name,
  ]

  deployment_style {
    deployment_option = "WITH_TRAFFIC_CONTROL"
    deployment_type   = "BLUE_GREEN"
  }

  blue_green_deployment_config {
    green_fleet_provisioning_option {
      action = "COPY_AUTO_SCALING_GROUP"
    }

    deployment_ready_option {
      action_on_timeout = "CONTINUE_DEPLOYMENT"
    }

    terminate_blue_instances_on_deployment_success {
      # 최초 seeding에서는 Terraform이 소유한 seed ASG를 원본으로 사용하므로
      # KEEP_ALIVE를 유지한다. replacement ASG가 다음 배포의 원본이 된 것을
      # 운영자가 확인한 뒤 codedeploy_termination_enabled를 true로 바꾼다.
      # 그때부터 대기 시간이 rollback 가능 시간의 상한이 되고, CodeDeploy가
      # original 인스턴스와 ASG를 자동 정리한다.
      action                           = var.codedeploy_termination_enabled ? "TERMINATE" : "KEEP_ALIVE"
      termination_wait_time_in_minutes = var.codedeploy_deployment_wait_minutes
    }
  }

  auto_rollback_configuration {
    # 되돌릴 성공 revision이 없는 최초 seeding 구간에서는 rollback이 같은 결함
    # revision을 다시 배포해 교체 ASG만 남기고 다음 시도를 막는다. 정상 운영
    # 기본값은 true다.
    enabled = var.deployment_auto_rollback_enabled
    events  = ["DEPLOYMENT_FAILURE", "DEPLOYMENT_STOP_ON_ALARM"]
  }

  alarm_configuration {
    alarms = local.deployment_alarm_names
    # 최초 배포에서는 blue가 아직 unhealthy해 blue-unhealthy alarm이 ALARM 상태다.
    # 그 상태로 배포를 시작하면 DEPLOYMENT_STOP_ON_ALARM으로 즉시 중단되므로
    # seeding 구간에만 끈다. 정상 운영 기본값은 true다.
    enabled                   = var.deployment_alarms_enabled
    ignore_poll_alarm_failure = false
  }

  # CodeDeploy는 배포가 성공하면 이 목록을 replacement 환경 ASG로 갱신한다.
  # Terraform이 이 값을 되돌리면 매 배포·매 apply마다 소유권이 충돌하고, 복사
  # 원본이 앱 없는 seed ASG가 된다. 따라서 첫 배포 이후 이 필드의 소유자는
  # CodeDeploy다. ASG 자체 설정을 바꿀 때는 seed ASG를 키워 다시 seeding하는
  # 절차가 필요하다. TERMINATE는 replacement ASG가 이 목록에 들어온 것을
  # 확인한 뒤에만 활성화해야 Terraform state의 seed ASG 삭제를 막을 수 있다.
  lifecycle {
    precondition {
      condition     = var.deployment_alarms_enabled || var.initial_alarm_seeding
      error_message = "deployment_alarms_enabled=false requires initial_alarm_seeding=true; alarm bypass is permitted only for explicit initial seeding."
    }

    precondition {
      condition     = !var.initial_alarm_seeding || !var.redis_recovery_mode
      error_message = "initial_alarm_seeding=true cannot be combined with redis_recovery_mode=true."
    }

    precondition {
      condition     = !var.initial_alarm_seeding || !var.deployment_auto_rollback_enabled
      error_message = "initial_alarm_seeding=true requires deployment_auto_rollback_enabled=false; automatic rollback is disabled only for the initial seed deployment."
    }

    precondition {
      condition     = var.initial_alarm_seeding || var.deployment_auto_rollback_enabled
      error_message = "initial_alarm_seeding=false requires deployment_auto_rollback_enabled=true; automatic rollback may be disabled only for the explicit initial seed deployment."
    }

    precondition {
      condition     = var.initial_alarm_seeding ? (!var.deployment_alarms_enabled && !var.deployment_auto_rollback_enabled && !var.redis_recovery_mode) : (var.deployment_alarms_enabled && var.deployment_auto_rollback_enabled)
      error_message = "Only the explicit initial seed combination may disable deployment alarms and automatic rollback: initial_alarm_seeding=true, deployment_alarms_enabled=false, deployment_auto_rollback_enabled=false, redis_recovery_mode=false."
    }

    precondition {
      condition     = !var.redis_recovery_mode || var.deployment_alarms_enabled
      error_message = "redis_recovery_mode=true requires deployment_alarms_enabled=true; ALB 5xx, latency, and unhealthy-host protections must remain enabled."
    }

    ignore_changes = [autoscaling_groups]
  }

  load_balancer_info {
    # EC2/On-Premises(compute_platform = "Server") blue-green은 target group 하나에
    # 교체 인스턴스를 등록하고 원본을 해제하는 방식이다. listener를 바꾸지 않는다.
    # target_group_pair_info는 CodeDeploy API에서 ECS task set 전용이라 Server
    # 플랫폼에서는 InvalidLoadBalancerInfoException으로 거부된다.
    target_group_info {
      name = aws_lb_target_group.blue.name
    }
  }
}
