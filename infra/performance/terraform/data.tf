data "aws_caller_identity" "current" {}

data "aws_vpc" "existing" {
  id = var.vpc_id
}

data "aws_subnet" "public" {
  id = var.public_subnet_id

  lifecycle {
    postcondition {
      condition     = self.vpc_id == var.vpc_id
      error_message = "public_subnet_id가 지정 VPC에 없다."
    }
  }
}

data "aws_subnet" "private" {
  for_each = toset(var.private_subnet_ids)
  id       = each.value

  lifecycle {
    postcondition {
      condition     = self.vpc_id == var.vpc_id && self.map_public_ip_on_launch == false
      error_message = "private_subnet_ids가 지정 VPC에 없거나 private subnet이 아니다."
    }
  }
}
