data "aws_vpc" "existing" {
  id = var.vpc_id
}

data "aws_kms_key" "s3" {
  key_id = "alias/aws/s3"
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

# app_subnet_is_private이 true면 IGW 기본 경로가 없어야 하고, false면 있어야 한다.
# 어느 쪽이든 "의도한 배치가 아닌 subnet을 넣는 실수"를 plan에서 잡는다.
data "aws_route_table" "app" {
  for_each  = toset(var.app_subnet_ids)
  subnet_id = each.value

  lifecycle {
    postcondition {
      condition = anytrue([
        for route in self.routes :
        route.cidr_block == "0.0.0.0/0" && can(regex("^igw-", route.gateway_id))
      ]) != var.app_subnet_is_private
      error_message = "app_subnet_is_private=true면 app_subnet_ids의 route table에 IGW를 향한 0.0.0.0/0 경로가 없어야 하고, false면 있어야 한다."
    }

    # 이 모듈의 private 모드는 NAT egress 경로를 전제로 한다. endpoint-only
    # 토폴로지는 필요한 서비스 목록과 subnet 연결을 별도 계약으로 관리해야 하므로
    # 현재 모듈의 기본 경로로 허용하지 않는다.
    postcondition {
      condition = !var.app_subnet_is_private || anytrue([
        for route in self.routes :
        route.cidr_block == "0.0.0.0/0" && can(regex("^nat-", route.nat_gateway_id))
      ])
      error_message = "app_subnet_is_private=true면 app_subnet_ids의 route table에 0.0.0.0/0 -> NAT gateway 경로가 있어야 한다."
    }
  }
}

data "aws_subnet" "direct_app" {
  id = var.app_subnet_id

  lifecycle {
    postcondition {
      condition     = self.vpc_id == var.vpc_id
      error_message = "app_subnet_id에 지정한 subnet이 vpc_id와 다르다."
    }
  }
}

data "aws_route_table" "direct_app" {
  subnet_id = var.app_subnet_id

  lifecycle {
    postcondition {
      condition = anytrue([
        for route in self.routes :
        route.cidr_block == "0.0.0.0/0" && can(regex("^igw-", route.gateway_id))
      ])
      error_message = "직접 서비스할 app_subnet_id에는 0.0.0.0/0 -> IGW 경로가 있어야 한다."
    }
  }
}
