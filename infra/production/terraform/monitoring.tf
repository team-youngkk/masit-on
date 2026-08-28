# 직접 앱 EC2의 상태와 의존성만 감시한다.
# 직접 앱 EC2의 상태와 의존성만 감시한다.
resource "aws_cloudwatch_metric_alarm" "direct_app_live" {
  alarm_name          = "${var.name_prefix}-direct-app-live"
  alarm_description   = "The direct application instance is not reporting live"
  namespace           = "masiton/health"
  metric_name         = "HealthLive"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"

  dimensions = {
    InstanceId = aws_instance.app.id
  }
}

resource "aws_cloudwatch_metric_alarm" "direct_app_ready" {
  alarm_name          = "${var.name_prefix}-direct-app-ready"
  alarm_description   = "The direct application instance is not ready"
  namespace           = "masiton/health"
  metric_name         = "HealthReady"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"

  dimensions = {
    InstanceId = aws_instance.app.id
  }
}

resource "aws_cloudwatch_metric_alarm" "direct_dependency_postgres" {
  alarm_name          = "${var.name_prefix}-direct-dependency-postgres"
  alarm_description   = "The direct application reports PostgreSQL as DOWN"
  namespace           = "masiton/health"
  metric_name         = "DependencyPostgres"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"

  dimensions = {
    InstanceId = aws_instance.app.id
  }
}

resource "aws_cloudwatch_metric_alarm" "direct_dependency_redis" {
  alarm_name          = "${var.name_prefix}-direct-dependency-redis"
  alarm_description   = "The direct application reports dedicated Redis as DOWN"
  namespace           = "masiton/health"
  metric_name         = "DependencyRedis"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"

  dimensions = {
    InstanceId = aws_instance.app.id
  }
}
