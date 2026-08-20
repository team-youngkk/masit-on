data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "app" {
  name               = "${local.name_prefix}-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role" "deps" {
  name               = "${local.name_prefix}-deps-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role" "loadgen" {
  name               = "${local.name_prefix}-loadgen-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role_policy_attachment" "app_ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# WireMock 이미지와 fixture는 공개 경로에서 받는다. ECR과 Parameter Store
# 권한은 주지 않는다.
resource "aws_iam_role_policy_attachment" "deps_ssm" {
  role       = aws_iam_role.deps.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "loadgen_ssm" {
  role       = aws_iam_role.loadgen.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "app_ecr_read" {
  statement {
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer"
    ]
    resources = [local.ecr_repository_arn]
  }
}

resource "aws_iam_role_policy" "app_ecr_read" {
  name   = "${local.name_prefix}-ecr-read"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app_ecr_read.json
}

data "aws_iam_policy_document" "app_parameter_read" {
  statement {
    effect = "Allow"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters"
    ]
    resources = [aws_ssm_parameter.db_password.arn]
  }

  statement {
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.ssm.target_key_arn]
  }
}

data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

resource "aws_iam_role_policy" "app_parameter_read" {
  name   = "${local.name_prefix}-parameter-read"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app_parameter_read.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${local.name_prefix}-profile"
  role = aws_iam_role.app.name
}

resource "aws_iam_instance_profile" "deps" {
  name = "${local.name_prefix}-deps-profile"
  role = aws_iam_role.deps.name
}

resource "aws_iam_instance_profile" "loadgen" {
  name = "${local.name_prefix}-loadgen-profile"
  role = aws_iam_role.loadgen.name
}
