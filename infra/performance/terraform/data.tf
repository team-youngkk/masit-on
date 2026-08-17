data "aws_caller_identity" "current" {}

data "aws_vpc" "existing" {
  id = var.vpc_id
}

data "aws_subnet" "public" {
  id = var.public_subnet_id

  lifecycle {
    postcondition {
      condition     = self.vpc_id == var.vpc_id
      error_message = "public_subnet_id가 지정 VPC에 속하지 않는다."
    }
  }
}

data "aws_route_tables" "public_association" {
  vpc_id = var.vpc_id

  filter {
    name   = "association.subnet-id"
    values = [var.public_subnet_id]
  }

  lifecycle {
    postcondition {
      condition     = length(self.ids) <= 1
      error_message = "public_subnet_id에 연결된 route table이 둘 이상이다."
    }
  }
}

data "aws_route_table" "public" {
  route_table_id = try(
    data.aws_route_tables.public_association.ids[0],
    data.aws_vpc.existing.main_route_table_id
  )

  lifecycle {
    postcondition {
      condition = anytrue([
        for route in self.route :
        route.cidr_block == "0.0.0.0/0" && startswith(route.gateway_id == null ? "" : route.gateway_id, "igw-")
      ])
      error_message = "public_subnet_id의 route table에 0.0.0.0/0 인터넷 게이트웨이 경로가 없다."
    }
  }
}

data "aws_subnet" "private" {
  for_each = toset(var.private_subnet_ids)
  id       = each.value

  lifecycle {
    postcondition {
      condition     = self.vpc_id == var.vpc_id
      error_message = "private_subnet_ids에 지정 VPC 외 subnet이 포함됐다."
    }
  }
}

data "aws_route_tables" "private_association" {
  for_each = toset(var.private_subnet_ids)
  vpc_id   = var.vpc_id

  filter {
    name   = "association.subnet-id"
    values = [each.value]
  }

  lifecycle {
    postcondition {
      condition     = length(self.ids) <= 1
      error_message = "private_subnet_ids의 subnet에 연결된 route table이 둘 이상이다."
    }
  }
}

data "aws_route_table" "private" {
  for_each = toset(var.private_subnet_ids)

  route_table_id = try(
    data.aws_route_tables.private_association[each.key].ids[0],
    data.aws_vpc.existing.main_route_table_id
  )

  lifecycle {
    postcondition {
      condition = alltrue([
        for route in self.route :
        !(route.cidr_block == "0.0.0.0/0" && startswith(route.gateway_id == null ? "" : route.gateway_id, "igw-"))
      ])
      error_message = "private_subnet_ids에 인터넷 게이트웨이 기본 경로가 있는 subnet이 포함됐다."
    }
  }
}
