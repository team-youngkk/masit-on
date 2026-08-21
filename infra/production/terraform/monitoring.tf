locals {
  deployment_alarm_names = concat(
    [
      aws_cloudwatch_metric_alarm.target_5xx.alarm_name,
      aws_cloudwatch_metric_alarm.target_latency.alarm_name,
      aws_cloudwatch_metric_alarm.blue_unhealthy.alarm_name,
    ],
    var.redis_recovery_mode ? [] : [
      aws_cloudwatch_metric_alarm.fleet_dependency_redis.alarm_name,
      aws_cloudwatch_metric_alarm.redis_memory_utilization.alarm_name,
    ],
  )
}

resource "aws_cloudwatch_metric_alarm" "target_5xx" {
  alarm_name          = "${var.name_prefix}-alb-target-5xx"
  alarm_description   = "ALB target 5xx during CodeDeploy blue-green deployment"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.app.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "target_latency" {
  alarm_name          = "${var.name_prefix}-alb-target-latency"
  alarm_description   = "ALB target latency during CodeDeploy blue-green deployment"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "TargetResponseTime"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = 2
  unit                = "Seconds"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.app.arn_suffix
  }
}

# ALB health check는 `/internal/health/ready`만 본다. ready 그룹에는 db만 있고
# Redis가 없어, Redis가 끊겨도 target은 healthy로 남아 트래픽을 계속 받는다.
# 공개 GET은 200이지만 인증은 ADR-AUTH-007 12절대로 fail-closed가 되는 구간이
# 어디에도 드러나지 않는다.
#
# 이것을 ready 그룹에 넣어 ALB가 드레인하게 하지 않는다. Redis는 fleet 전체가
# 인스턴스 하나를 공유하므로 모든 target이 동시에 unhealthy가 되어 Redis와
# 무관한 공개 탐색까지 전면 중단된다. 감지 대신 가용성을 잃는 교환이다.
#
# 그래서 트래픽 경로가 아니라 배포 게이트에 건다. Redis가 끊긴 상태에서는 새
# 배포가 시작되지 않고 진행 중이면 자동 rollback되며, 알람으로 드러난다.
# 이미 서비스 중인 트래픽은 끊지 않는다.
resource "aws_cloudwatch_metric_alarm" "fleet_dependency_redis" {
  alarm_name        = "${var.name_prefix}-fleet-dependency-redis"
  alarm_description = "An app instance reports the Redis dependency as DOWN"
  namespace         = "masiton/health"
  # health-metrics.sh가 Environment=asg 차원으로 올리는 fleet 집계 지표다. Minimum이므로
  # 한 대라도 0을 올리면 0이 된다. InstanceId 차원 지표는 인스턴스가 계속 바뀌는 ASG에서
  # 알람 대상으로 고정할 수 없어 쓰지 않는다.
  metric_name = "FleetDependencyRedis"
  statistic   = "Minimum"
  period      = 60
  # 지표 수집 주기가 1분이므로 기존 운영 알람과 같은 "연속 3회 실패" 기준을 쓴다.
  # 재기동 중 한두 번의 순간적인 실패로 배포를 막지 않는다.
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  # 정상 운영과 복구 모드 모두 결측은 감지 경로 장애이므로 breaching으로 처리한다.
  # 복구 모드는 이 alarm을 CodeDeploy 목록에서만 일시적으로 제외하며, 이 리소스의
  # missing-data 계약 자체를 바꾸지 않는다.
  treat_missing_data = "breaching"

  dimensions = {
    Environment = "asg"
  }
}

resource "aws_cloudwatch_metric_alarm" "redis_memory_utilization" {
  alarm_name          = "${var.name_prefix}-redis-memory-utilization"
  alarm_description   = "Dedicated Redis used_memory/maxmemory utilization is at or above 80%"
  namespace           = "masiton/health"
  metric_name         = "RedisMemoryUtilizationPercent"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 80
  unit                = "Percent"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  # Capacity data is produced by the same timer as FleetDependencyRedis. A
  # missing series means the recovery signal is unavailable, so normal
  # deployment monitoring remains fail-closed.
  treat_missing_data = "breaching"

  dimensions = {
    Environment = "asg"
  }
}

resource "aws_cloudwatch_metric_alarm" "blue_unhealthy" {
  alarm_name          = "${var.name_prefix}-blue-unhealthy-host"
  alarm_description   = "Blue target group has an unhealthy host"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.app.arn_suffix
    TargetGroup  = aws_lb_target_group.blue.arn_suffix
  }
}

