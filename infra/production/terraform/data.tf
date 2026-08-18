data "aws_vpc" "existing" {
  id = var.vpc_id
}

data "aws_subnet" "alb" {
  for_each = toset(var.alb_subnet_ids)
  id       = each.value

  lifecycle {
    postcondition {
      condition     = self.vpc_id == var.vpc_id
      error_message = "alb_subnet_ids에 지정한 subnet이 vpc_id와 다르다."
    }
  }
}

data "aws_subnet" "app" {
  for_each = toset(var.app_subnet_ids)
  id       = each.value

  lifecycle {
    postcondition {
      condition     = self.vpc_id == var.vpc_id
      error_message = "app_subnet_ids에 지정한 subnet이 vpc_id와 다르다."
    }
  }
}
