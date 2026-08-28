resource "aws_s3_bucket" "codedeploy_revision" {
  bucket = var.codedeploy_revision_bucket_name
}

resource "aws_s3_bucket_versioning" "codedeploy_revision" {
  bucket = aws_s3_bucket.codedeploy_revision.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_ownership_controls" "codedeploy_revision" {
  bucket = aws_s3_bucket.codedeploy_revision.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "codedeploy_revision" {
  bucket = aws_s3_bucket.codedeploy_revision.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "codedeploy_revision" {
  bucket = aws_s3_bucket.codedeploy_revision.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

data "aws_iam_policy_document" "codedeploy_revision_bucket" {
  statement {
    sid    = "DenyInsecureTransport"
    effect = "Deny"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions   = ["s3:*"]
    resources = [aws_s3_bucket.codedeploy_revision.arn, "${aws_s3_bucket.codedeploy_revision.arn}/*"]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  statement {
    sid    = "DenyNonKmsRedisPasswordObject"
    effect = "Deny"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.codedeploy_revision.arn}/${var.redis_password_object_key}"]

    condition {
      test     = "StringNotEquals"
      variable = "s3:x-amz-server-side-encryption"
      values   = ["aws:kms"]
    }
  }

  statement {
    sid    = "DenyWrongKmsKeyForRedisPasswordObject"
    effect = "Deny"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.codedeploy_revision.arn}/${var.redis_password_object_key}"]

    condition {
      test     = "StringNotEquals"
      variable = "s3:x-amz-server-side-encryption-aws-kms-key-id"
      values   = [data.aws_kms_key.s3.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "codedeploy_revision" {
  bucket = aws_s3_bucket.codedeploy_revision.id
  policy = data.aws_iam_policy_document.codedeploy_revision_bucket.json
}

output "codedeploy_revision_bucket_name" {
  description = "CodeDeploy revision bucket"
  value       = aws_s3_bucket.codedeploy_revision.bucket
}
