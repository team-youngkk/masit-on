variable "aws_region" {
  description = "전용 Redis를 배치할 AWS 리전"
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
  description = "모든 리소스 이름의 접두사"
  type        = string
  default     = "masiton-prod"
}

variable "environment" {
  description = "리소스 태그의 환경 이름"
  type        = string
  default     = "production"
}

variable "vpc_id" {
  description = "기존 VPC ID. 이 모듈은 VPC를 만들지 않는다"
  type        = string
}

variable "redis_subnet_id" {
  description = "전용 Redis를 둘 기존 private subnet ID. 퍼블릭 IP를 붙이지 않는다"
  type        = string
}

variable "private_route_table_ids" {
  description = "S3 gateway endpoint를 연결할 private route table ID 목록"
  type        = list(string)

  validation {
    condition     = length(var.private_route_table_ids) > 0
    error_message = "S3 gateway endpoint를 연결할 route table을 하나 이상 지정해야 한다."
  }
}

variable "redis_ami_id" {
  description = "docker와 digest 고정 Redis 이미지를 미리 담은 AMI ID. secret은 S3에서 런타임에 읽는다"
  type        = string
}

variable "redis_instance_type" {
  description = "전용 Redis 인스턴스 유형"
  type        = string
  default     = "t4g.nano"
}

variable "redis_root_volume_size_gib" {
  description = "Redis 컨테이너 이미지와 운영 파일을 담는 root EBS 용량"
  type        = number
  default     = 8
}

variable "redis_data_volume_size_gib" {
  description = "AOF·RDB 데이터를 담는 교체 독립 EBS 용량"
  type        = number
  default     = 8
}

variable "redis_port" {
  description = "Redis가 수신하는 포트"
  type        = number
  default     = 6379
}

variable "app_security_group_ids" {
  description = "Redis 6379에 접근할 애플리케이션 security group ID 목록"
  type        = list(string)

  validation {
    condition     = length(var.app_security_group_ids) > 0
    error_message = "Redis에 접근할 app security group을 하나 이상 지정해야 한다."
  }
}

variable "redis_assets_bucket" {
  description = "Redis 설정 파일을 스테이징할 기존 S3 bucket 이름"
  type        = string
}

variable "redis_assets_prefix" {
  description = "Redis 설정 파일 S3 prefix. 끝에 슬래시를 넣지 않는다"
  type        = string
  default     = "masiton/redis/assets"
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

variable "redis_assets_source_dir" {
  description = "저장소의 Redis 배포 파일 경로. 이 모듈 기준 상대 경로다"
  type        = string
  default     = "../../../deploy"
}

variable "manage_host_parameter" {
  description = "true일 때 Redis 사설 IP를 /masiton/redis/host parameter로 관리한다"
  type        = bool
  default     = true
}

variable "host_parameter_name" {
  description = "애플리케이션이 읽는 Redis host parameter 이름"
  type        = string
  default     = "/masiton/redis/host"
}

variable "port_parameter_name" {
  description = "애플리케이션이 읽는 Redis port parameter 이름"
  type        = string
  default     = "/masiton/redis/port"
}
