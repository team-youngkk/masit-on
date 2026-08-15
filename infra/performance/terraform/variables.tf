variable "aws_region" {
  description = "성능 검증 환경을 만들 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "AWS CLI 프로파일. SSO 프로파일은 AWS_PROFILE을 사용할 때 null로 둔다"
  type        = string
  default     = null
  nullable    = true
}

variable "run_id" {
  description = "실행 식별자. 예: 20260815-01"
  type        = string

  validation {
    condition     = can(regex("^[0-9]{8}-[0-9]{2}$", var.run_id))
    error_message = "run_id는 YYYYMMDD-NN 형식이어야 한다."
  }
}

variable "vpc_id" {
  description = "기존 VPC ID. VPC 자체는 Terraform이 생성·수정하지 않는다"
  type        = string
}

variable "public_subnet_id" {
  description = "앱·부하 생성기 EC2를 둘 기존 public subnet ID"
  type        = string
}

variable "private_subnet_ids" {
  description = "RDS subnet group에 사용할 기존 private subnet ID 2개 이상"
  type        = list(string)

  validation {
    condition     = length(var.private_subnet_ids) >= 2
    error_message = "RDS subnet group에는 private subnet을 2개 이상 지정해야 한다."
  }
}

variable "ami_id" {
  description = "Amazon Linux 2023 arm64 AMI ID. latest 자동 선택 금지"
  type        = string
}

variable "app_image_uri" {
  description = "ECR backend 이미지의 digest 고정 URI"
  type        = string

  validation {
    condition     = can(regex("@sha256:[0-9a-f]{64}$", var.app_image_uri))
    error_message = "app_image_uri는 @sha256:<64자리 hex> digest로 끝나야 한다."
  }
}

variable "ecr_repository_name" {
  description = "앱 이미지가 있는 ECR repository 이름"
  type        = string
  default     = "masiton-backend"
}

variable "db_password" {
  description = "성능 전용 RDS 비밀번호. TF_VAR_db_password로 주입하며 저장소에 기록하지 않는다"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_password) >= 16 && length(var.db_password) <= 128
    error_message = "db_password는 16~128자여야 한다."
  }
}

variable "db_name" {
  description = "성능 전용 데이터베이스 이름"
  type        = string
  default     = "masiton"
}

variable "db_username" {
  description = "성능 전용 RDS 관리자 계정"
  type        = string
  default     = "masiton"
}

variable "wiremock_image" {
  description = "WireMock 이미지. 기술 정책의 고정 태그 사용"
  type        = string
  default     = "wiremock/wiremock:3.13.2-alpine"
}

variable "redis_image" {
  description = "Redis 이미지. 기술 정책의 고정 태그 사용"
  type        = string
  default     = "redis:8.8-alpine"
}

variable "k6_version" {
  description = "부하 생성기에 설치할 k6 버전"
  type        = string
  default     = "2.1.0"

  validation {
    condition     = var.k6_version == "2.1.0"
    error_message = "ADR-PERF-001에 따라 k6는 v2.1.0만 허용한다."
  }
}

variable "k6_arm64_sha256" {
  description = "k6 linux-arm64 tarball SHA-256"
  type        = string
  default     = "191fa8d89512a4e5083f3fabcb4c3828af9f5b9eee016de8443f6473c029ffb5"

  validation {
    condition     = can(regex("^[0-9a-f]{64}$", var.k6_arm64_sha256))
    error_message = "k6_arm64_sha256는 64자리 소문자 SHA-256이어야 한다."
  }
}

variable "root_volume_size_gib" {
  description = "EC2 root EBS 용량"
  type        = number
  default     = 30
}
