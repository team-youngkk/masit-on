variable "aws_region" {
  description = "운영 인프라를 배치할 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "AWS CLI 프로파일. AWS_PROFILE을 사용할 때는 null로 둔다"
  type        = string
  default     = null
  nullable    = true
}

variable "name_prefix" {
  description = "모든 운영 리소스 이름의 접두사"
  type        = string
  default     = "masiton-prod"
}

variable "environment" {
  description = "리소스 태그의 환경 이름"
  type        = string
  default     = "production"
}

variable "vpc_id" {
  description = "기존 VPC ID. VPC는 이 모듈이 생성하지 않는다"
  type        = string
}

variable "alb_subnet_ids" {
  description = "기존 인터넷 연결 public subnet ID 목록"
  type        = list(string)

  validation {
    condition     = length(var.alb_subnet_ids) >= 2
    error_message = "ALB에는 서로 다른 AZ의 subnet을 2개 이상 지정해야 한다."
  }
}

variable "app_subnet_ids" {
  description = "기존 app subnet ID 목록. app_subnet_is_private 값에 따라 public 또는 private route를 검증한다"
  type        = list(string)

  validation {
    condition     = length(var.app_subnet_ids) >= 2
    error_message = "ASG에는 서로 다른 AZ의 app subnet을 2개 이상 지정해야 한다."
  }
}

variable "ami_id" {
  description = "blue/green Launch Template에 사용할 기존 AMI ID"
  type        = string
}

variable "instance_type" {
  description = "original·replacement ASG 인스턴스 유형"
  type        = string
  default     = "t4g.medium"
}

variable "app_port" {
  description = "ALB가 Nginx로 전달하는 인스턴스 포트. ACM 종단 후 Nginx에서 재암호화한다"
  type        = number
  default     = 443
}

variable "app_protocol" {
  description = "ALB가 Nginx로 전달하는 프로토콜"
  type        = string
  default     = "HTTPS"

  validation {
    condition     = contains(["HTTP", "HTTPS"], var.app_protocol)
    error_message = "app_protocol은 HTTP 또는 HTTPS여야 한다."
  }
}

variable "health_check_path" {
  description = "ALB target group health check 경로. /internal/**는 외부 경계상 사용하지 않는다"
  type        = string
  default     = "/_masiton/alb-health"
}

variable "acm_certificate_arn" {
  description = "ALB HTTPS listener와 Nginx 재암호화에 사용할 기존 ACM 인증서 ARN. 운영 배포 경로에서 필수다"
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^arn:aws:acm:[^:]+:[0-9]{12}:certificate/.+", var.acm_certificate_arn))
    error_message = "acm_certificate_arn은 유효한 ACM certificate ARN이어야 한다."
  }
}

variable "alb_ingress_cidr_blocks" {
  description = "ALB HTTP/HTTPS ingress CIDR. 기본값은 인터넷 공개"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "rds_security_group_id" {
  description = "기존 RDS security group ID"
  type        = string
}

variable "redis_security_group_id" {
  description = "기존 Redis security group ID"
  type        = string
}

variable "rds_port" {
  description = "기존 RDS가 수신하는 포트"
  type        = number
  default     = 5432
}

variable "redis_port" {
  description = "기존 Redis가 수신하는 포트"
  type        = number
  default     = 6379
}

# RDS와 Redis를 하나의 플래그로 묶으면 안 된다. Redis ingress는 전용 Redis를
# 소유하는 ../terraform-redis 레이어가 관리하므로 이 모듈이 같이 만들면 규칙이
# 중복된다. 대상별로 분리해 소유자를 한 곳으로 유지한다.
variable "manage_rds_ingress_rule" {
  description = "true일 때 기존 RDS SG에 app SG 출처 ingress rule을 추가한다. 새 ASG가 DB에 접근하려면 필요하다"
  type        = bool
  default     = false
}

variable "manage_redis_ingress_rule" {
  description = "true일 때 기존 Redis SG에 app SG 출처 ingress rule을 추가한다. ../terraform-redis가 그 규칙을 관리하면 false로 둔다"
  type        = bool
  default     = false
}

variable "blue_min_size" {
  type    = number
  default = 1
}

variable "blue_desired_capacity" {
  type    = number
  default = 1
}

variable "blue_max_size" {
  type    = number
  default = 4
}

variable "root_volume_size_gib" {
  description = "Launch Template root EBS 용량"
  type        = number
  default     = 30
}

variable "user_data" {
  description = "인스턴스 초기화 스크립트. 비밀값 대신 SSM parameter 이름을 참조하도록 작성한다"
  type        = string
  default     = null
  nullable    = true
  sensitive   = true
}

variable "ssm_parameter_arns" {
  description = "애플리케이션이 런타임에 읽을 SecureString parameter ARN 목록"
  type        = list(string)

  validation {
    condition     = length(var.ssm_parameter_arns) > 0
    error_message = "ASG 인스턴스가 읽을 Parameter Store ARN을 하나 이상 지정해야 한다."
  }
}

variable "ecr_repository_arns" {
  description = "backend/frontend 이미지를 pull할 ECR repository ARN 목록"
  type        = list(string)

  validation {
    condition     = length(var.ecr_repository_arns) > 0
    error_message = "ECR repository ARN을 하나 이상 지정해야 한다."
  }
}

variable "kms_key_arns" {
  description = "SSM SecureString 복호화에 사용할 KMS key ARN 목록"
  type        = list(string)

  validation {
    condition     = length(var.kms_key_arns) > 0
    error_message = "SSM SecureString 복호화용 KMS key ARN을 하나 이상 지정해야 한다."
  }
}

variable "codedeploy_revision_bucket_name" {
  description = "CodeDeploy revision을 저장할 전용 S3 bucket 이름"
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.codedeploy_revision_bucket_name))
    error_message = "codedeploy_revision_bucket_name은 유효한 S3 bucket 이름이어야 한다."
  }
}

variable "github_actions_role_name" {
  description = "기존 GitHub Actions OIDC role 이름. 지정하면 production revision/CodeDeploy 정책을 추가한다"
  type        = string
  default     = null
  nullable    = true
}

variable "nginx_trusted_proxy_cidrs" {
  description = "ALB가 위치한 private 경계의 신뢰할 proxy CIDR 목록"
  type        = list(string)

  validation {
    condition     = length(var.nginx_trusted_proxy_cidrs) > 0
    error_message = "Nginx가 신뢰할 ALB proxy CIDR을 하나 이상 지정해야 한다."
  }
}

variable "route53_zone_id" {
  description = "기존 Route53 hosted zone ID. record_name과 함께 지정할 때만 alias record를 관리한다"
  type        = string
  default     = null
  nullable    = true
}

variable "route53_record_name" {
  description = "ALB alias record 이름"
  type        = string
  default     = null
  nullable    = true
}

variable "route53_evaluate_target_health" {
  description = "Route53 alias의 evaluate_target_health 설정"
  type        = bool
  default     = true
}

variable "initial_blue_verified" {
  description = "known-good revision이 blue ASG에서 검증되어 Route53 alias를 연결해도 된다는 운영 확인"
  type        = bool
  default     = false
}

variable "codedeploy_deployment_wait_minutes" {
  description = "배포 성공 후 original 인스턴스를 종료하기까지의 대기 시간(1~15분). 이 값이 rollback 가능 시간의 상한이며 CI 배포 폴링 한도 45분에서 provisioning·hook 시간을 위한 여유를 남기는 보수적 운영 상한이다"
  type        = number
  default     = 15

  validation {
    condition     = var.codedeploy_deployment_wait_minutes >= 1 && var.codedeploy_deployment_wait_minutes <= 15 && var.codedeploy_deployment_wait_minutes == floor(var.codedeploy_deployment_wait_minutes)
    error_message = "codedeploy_deployment_wait_minutes는 1 이상 15 이하의 정수여야 한다. CI 배포 폴링 45분과 provisioning·hook 시간을 함께 고려한 보수적 상한이다."
  }
}

variable "codedeploy_termination_enabled" {
  description = "CodeDeploy 성공 후 original 인스턴스와 ASG를 자동 종료할지 여부. 최초 seeding에서는 false로 두고 replacement ASG가 deployment group의 원본으로 전환된 것을 확인한 뒤 true로 바꾼다"
  type        = bool
  default     = false
}

variable "asg_health_check_type" {
  description = "blue ASG health check 종류. 정상 운영은 ELB다. 앱이 배포되지 않은 최초 seeding 구간에만 EC2로 낮춰 빈 인스턴스가 교체 루프에 빠지지 않게 한다"
  type        = string
  default     = "ELB"

  validation {
    condition     = contains(["ELB", "EC2"], var.asg_health_check_type)
    error_message = "asg_health_check_type은 ELB 또는 EC2여야 한다."
  }
}

variable "deployment_alarms_enabled" {
  description = "CodeDeploy deployment group의 alarm 게이트. 정상 운영은 true다. blue가 아직 unhealthy한 최초 배포에서만 false로 두어야 배포가 시작 즉시 중단되지 않는다"
  type        = bool
  default     = true
}

variable "redis_recovery_mode" {
  description = "승인된 단일 Redis 복구 배포에서만 true로 두며, CodeDeploy alarm 목록에서 Redis alarm만 제외한다. ALB·latency·unhealthy-host alarm과 polling 실패 차단은 유지한다"
  type        = bool
  default     = false
}

variable "mail_smtp_port" {
  description = "애플리케이션이 외부 SMTP relay로 나가는 포트. dependency health의 mail 항목이 이 경로를 사용한다"
  type        = number
  default     = 587
}

variable "deployment_auto_rollback_enabled" {
  description = "CodeDeploy 자동 rollback. 정상 운영은 true다. 성공한 revision이 아직 없는 최초 seeding 구간에서는 rollback이 같은 결함 revision을 재배포하고 교체 ASG를 남기므로 false로 둔다"
  type        = bool
  default     = true
}

# app 인스턴스의 subnet 배치 의도를 명시한다. 오배치를 plan에서 잡되 어느 쪽이
# 의도인지는 아키텍처 결정에 따라 달라지므로 코드에 한 방향을 굳히지 않는다.
#
# 현재 운영은 false다. 앱을 사설 subnet에 두면 ECR·Parameter Store·CloudWatch
# 때문에 NAT 또는 인터페이스 엔드포인트가 필요하고, 배포 고도화 비용·일정 영향
# 검토 6.6절이 그 비용으로 8.1절 예산을 넘긴다고 판정해 "앱은 public subnet에
# 남기고 보안 그룹 인바운드만 ALB 출처로 좁히는" 구성을 전제로 금액을 산정했다.
# 인터넷에서 앱으로 들어오는 경계는 subnet이 아니라 security group이 지킨다.
variable "app_subnet_is_private" {
  description = "true면 app subnet에 IGW 기본 경로가 없어야 하고, false면 ALB와 같은 public subnet임을 요구한다"
  type        = bool
  default     = false
}
