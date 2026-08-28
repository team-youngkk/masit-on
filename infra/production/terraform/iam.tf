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
  name               = "${var.name_prefix}-ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role_policy_attachment" "app_ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "app_parameter_read" {
  # app-secrets-render.sh는 backend unit의 ExecStartPre로 실행되며 경로 단위로
  # 읽는다(GetParametersByPath). 이 action이 없으면 backend가 기동하지 못한다.
  statement {
    effect    = "Allow"
    actions   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = var.ssm_parameter_arns
  }

  statement {
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
      values   = ["arn:aws:ssm:${var.aws_region}:*:parameter/masiton/*"]
    }
  }

  statement {
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # app-deploy.sh는 태그로 받은 이미지를 digest로 굳히기 위해 DescribeImages를 쓴다
  # (ADR-RUNTIME-001 11·13절). 이 action이 없으면 배포가 digest 해석에서 멈춘다.
  statement {
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = var.ecr_repository_arns
  }

  # nginx-install.sh가 tls-deploy-cert.sh로 ACM 인증서를 내보내 Nginx에 반영한다.
  # ALB 종단과 Nginx 재암호화가 같은 인증서를 쓰므로 acm_certificate_arn을 그대로 쓴다.
  dynamic "statement" {
    for_each = var.acm_certificate_arn == null ? [] : [var.acm_certificate_arn]

    content {
      sid       = "ExportNginxCertificate"
      effect    = "Allow"
      actions   = ["acm:ExportCertificate", "acm:DescribeCertificate"]
      resources = [statement.value]
    }
  }

  # health-metrics.sh가 1분 주기로 상태 지표를 올린다. PutMetricData는 resource
  # 수준 제한을 지원하지 않으므로 namespace 조건으로 이 서비스의 지표만 허용한다.
  # CloudWatch Agent도 이 role을 사용하며 masiton/host namespace로 host 지표를 보낸다.
  statement {
    effect    = "Allow"
    actions   = ["cloudwatch:PutMetricData"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = ["masiton/health", "masiton/host"]
    }
  }

  # cloudwatch-install.sh가 설치하는 agent는 Nginx·컨테이너 로그를 CloudWatch Logs로
  # 보낸다. 설정이 선언한 로그 그룹으로 범위를 제한한다.
  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:PutRetentionPolicy",
      "logs:DescribeLogStreams",
    ]
    resources = [
      "arn:aws:logs:${var.aws_region}:*:log-group:/masiton/*",
      "arn:aws:logs:${var.aws_region}:*:log-group:/masiton/*:log-stream:*",
    ]
  }
}

resource "aws_iam_role_policy" "app_parameter_read" {
  name   = "${var.name_prefix}-parameter-read"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app_parameter_read.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.name_prefix}-ec2-profile"
  role = aws_iam_role.app.name
}

# 직접 배포 경로의 최소 권한이다.
data "aws_iam_policy_document" "github_actions_ssm_deploy" {
  statement {
    effect  = "Allow"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript",
      "arn:aws:ec2:${var.aws_region}:*:instance/${aws_instance.app.id}",
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:AbortMultipartUpload",
    ]
    resources = ["${aws_s3_bucket.codedeploy_revision.arn}/masiton/ssm/*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "ssm:CancelCommand",
      "ssm:GetCommandInvocation",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_actions_ssm_deploy" {
  count  = var.github_actions_role_name == null ? 0 : 1
  name   = "${var.name_prefix}-ssm-deploy"
  role   = var.github_actions_role_name
  policy = data.aws_iam_policy_document.github_actions_ssm_deploy.json
}
