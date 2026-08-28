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

variable "app_subnet_ids" {
  description = "기존 app subnet ID 목록. app_subnet_is_private 값에 따라 public 또는 private route를 검증한다"
  type        = list(string)

  validation {
    condition     = length(var.app_subnet_ids) >= 2
    error_message = "ASG에는 서로 다른 AZ의 app subnet을 2개 이상 지정해야 한다."
  }
}

variable "ami_id" {
  description = "seed ASG와 직접 앱 EC2에 사용할 기존 AMI ID"
  type        = string
}

variable "instance_type" {
  description = "직접 서비스할 앱 EC2의 인스턴스 유형"
  type        = string
  default     = "t4g.micro"
}

variable "seed_instance_type" {
  description = "보존 중인 seed ASG의 인스턴스 유형"
  type        = string
  default     = "t4g.small"
}

variable "app_port" {
  description = "직접 접속할 Nginx 인스턴스 포트"
  type        = number
  default     = 443
}

variable "app_protocol" {
  description = "직접 앱 EC2에 연결할 프로토콜"
  type        = string
  default     = "HTTPS"

  validation {
    condition     = contains(["HTTP", "HTTPS"], var.app_protocol)
    error_message = "app_protocol은 HTTP 또는 HTTPS여야 한다."
  }
}

variable "health_check_path" {
  description = "보존 중인 seed target group의 health check 경로"
  type        = string
  default     = "/_masiton/alb-health"
}

variable "acm_certificate_arn" {
  description = "직접 앱 EC2의 Nginx HTTPS 종단에 사용할 기존 ACM 인증서 ARN"
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^arn:aws:acm:[^:]+:[0-9]{12}:certificate/.+", var.acm_certificate_arn))
    error_message = "acm_certificate_arn은 유효한 ACM certificate ARN이어야 한다."
  }
}

variable "rds_security_group_id" {
  description = "기존 DB/RDS security group ID (호환·롤백 경계)"
  type        = string
}

variable "redis_security_group_id" {
  description = "기존 Redis security group ID"
  type        = string
}

variable "rds_port" {
  description = "기존 DB endpoint가 수신하는 포트"
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
  description = "SSM command pointer와 기존 배포 산출물을 보관하는 S3 bucket 이름"
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.codedeploy_revision_bucket_name))
    error_message = "codedeploy_revision_bucket_name은 유효한 S3 bucket 이름이어야 한다."
  }
}

variable "redis_password_object_key" {
  description = "Redis 비밀번호를 담은 S3 SSE-KMS 객체 key"
  type        = string
  default     = "masiton/redis/secret/redis-password"

  validation {
    condition     = length(trimspace(var.redis_password_object_key)) > 0
    error_message = "redis_password_object_key는 비어 있을 수 없다."
  }
}

variable "github_actions_role_name" {
  description = "기존 GitHub Actions OIDC role 이름. 지정하면 단일 EC2 SSM 배포 정책을 추가한다"
  type        = string
  default     = null
  nullable    = true
}

variable "nginx_trusted_proxy_cidrs" {
  description = "Nginx가 신뢰할 proxy CIDR 목록"
  type        = list(string)

  validation {
    condition     = length(var.nginx_trusted_proxy_cidrs) > 0
    error_message = "Nginx가 신뢰할 proxy CIDR을 하나 이상 지정해야 한다."
  }
}

variable "route53_zone_id" {
  description = "기존 Route53 hosted zone ID. record_name과 함께 지정할 때만 앱 A record를 관리한다"
  type        = string
  default     = null
  nullable    = true
}

variable "route53_record_name" {
  description = "앱 EC2 EIP를 가리킬 Route53 A record 이름"
  type        = string
  default     = null
  nullable    = true
}

variable "asg_health_check_type" {
  description = "보존 중인 seed ASG health check 종류. seed는 현재 0대로 유지한다"
  type        = string
  default     = "ELB"

  validation {
    condition     = contains(["ELB", "EC2"], var.asg_health_check_type)
    error_message = "asg_health_check_type은 ELB 또는 EC2여야 한다."
  }
}

variable "mail_smtp_port" {
  description = "애플리케이션이 외부 SMTP relay로 나가는 포트. dependency health의 mail 항목이 이 경로를 사용한다"
  type        = number
  default     = 587
}

# app 인스턴스의 subnet 배치 의도를 명시한다. 오배치를 plan에서 잡되 어느 쪽이
# 의도인지는 아키텍처 결정에 따라 달라지므로 코드에 한 방향을 굳히지 않는다.
#
# 현재 운영은 false다. 앱을 사설 subnet에 두면 ECR·Parameter Store·CloudWatch
# 때문에 NAT 또는 인터페이스 엔드포인트가 필요하고, 배포 고도화 비용·일정 영향
# 검토 6.6절이 그 비용으로 8.1절 예산을 넘긴다고 판정해 앱은 public subnet에
# 둔다. 인터넷에서 앱으로 들어오는 경계는 security group이 지킨다.
variable "app_subnet_is_private" {
  description = "true면 app subnet에 IGW 기본 경로가 없어야 하고, false면 public subnet임을 요구한다"
  type        = bool
  default     = false
}

# 직접 서비스할 앱 EC2의 네트워크 입력이다. Route53 record는 직접 앱 EIP를 가리킨다.
variable "app_subnet_id" {
  description = "직접 서비스할 단일 앱 EC2의 public subnet ID"
  type        = string
}

variable "app_ingress_cidr_blocks" {
  description = "직접 앱 EC2의 HTTP/HTTPS ingress CIDR 목록"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "database_security_group_id" {
  description = "RDS 또는 전환 후 PostgreSQL EC2의 security group ID"
  type        = string
}

variable "database_port" {
  description = "전환 대상 PostgreSQL이 수신하는 포트"
  type        = number
  default     = 5432
}

variable "direct_traffic_enabled" {
  description = "단일 EC2 전환 완료 여부. 운영 구성에서는 true만 허용한다"
  type        = bool
  default     = false

  validation {
    condition     = var.direct_traffic_enabled
    error_message = "단일 EC2 정리 구성에서는 direct_traffic_enabled=true여야 한다."
  }
}
