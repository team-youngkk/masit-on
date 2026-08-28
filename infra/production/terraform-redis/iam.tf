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

resource "aws_iam_role" "redis" {
  name               = "${var.name_prefix}-redis-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

# SSM Agent 관리 권한(AmazonSSMManagedInstanceCore)은 붙이지 않는다.
# ssmmessages/ec2messages endpoint가 없어 Agent가 연결할 수 없고, 관리 접속은
# EC2 Instance Connect Endpoint로 하기 때문이다.
data "aws_iam_policy_document" "redis_read" {
  statement {
    sid       = "ReadRedisSecret"
    effect    = "Allow"
    actions   = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = [var.redis_password_parameter_arn]
  }

  statement {
    sid       = "DecryptSecureString"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = var.kms_key_arns

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.aws_region}.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "kms:EncryptionContext:PARAMETER_ARN"
      values   = [var.redis_password_parameter_arn]
    }
  }

  statement {
    sid       = "ReadRedisDeployAssets"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:GetObjectVersion"]
    resources = ["arn:aws:s3:::${var.redis_assets_bucket}/${var.redis_assets_prefix}/*"]
  }

  statement {
    sid       = "LocateBucket"
    effect    = "Allow"
    actions   = ["s3:GetBucketLocation", "s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.redis_assets_bucket}"]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["${var.redis_assets_prefix}/*"]
    }
  }
}

resource "aws_iam_role_policy" "redis_read" {
  name   = "${var.name_prefix}-redis-read"
  role   = aws_iam_role.redis.id
  policy = data.aws_iam_policy_document.redis_read.json
}

resource "aws_iam_instance_profile" "redis" {
  name = "${var.name_prefix}-redis-profile"
  role = aws_iam_role.redis.name
}
