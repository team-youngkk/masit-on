data "aws_vpc" "existing" {
  id = var.vpc_id
}

data "aws_subnet" "redis" {
  id = var.redis_subnet_id

  lifecycle {
    postcondition {
      condition     = self.vpc_id == var.vpc_id
      error_message = "redis_subnet_id로 지정한 subnet이 vpc_id와 다르다."
    }
  }
}

data "aws_prefix_list" "s3" {
  name = "com.amazonaws.${var.aws_region}.s3"
}
