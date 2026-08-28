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

  statement {
    effect = "Allow"
    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket",
    ]
    resources = [aws_s3_bucket.codedeploy_revision.arn]
  }

  statement {
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:GetObjectVersion"]
    resources = ["${aws_s3_bucket.codedeploy_revision.arn}/masiton/codedeploy/*"]
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

data "aws_iam_policy_document" "codedeploy_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["codedeploy.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "codedeploy" {
  name               = "${var.name_prefix}-codedeploy-role"
  assume_role_policy = data.aws_iam_policy_document.codedeploy_assume_role.json
}

resource "aws_iam_role_policy_attachment" "codedeploy" {
  role       = aws_iam_role.codedeploy.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSCodeDeployRole"
}

# AWSCodeDeployRole은 autoscaling 액션은 모두 주지만 ec2:RunInstances,
# ec2:CreateTags, iam:PassRole은 주지 않는다. COPY_AUTO_SCALING_GROUP은
# CodeDeploy가 교체 ASG를 만들고 launch template의 instance profile을 넘겨야
# 하므로 이 세 가지가 없으면 배포가 AmazonAutoScaling 권한 오류로 실패한다.
data "aws_iam_policy_document" "codedeploy_green_fleet" {
  # RunInstances와 CreateTags는 자원을 미리 특정할 수 없어 리전으로만 좁힌다.
  statement {
    sid       = "LaunchGreenFleet"
    effect    = "Allow"
    actions   = ["ec2:RunInstances", "ec2:CreateTags"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
  }

  # 교체 환경 인스턴스에 넘길 수 있는 role을 이 모듈이 만든 앱 role 하나로 제한한다.
  statement {
    sid       = "PassAppInstanceRole"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.app.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "codedeploy_green_fleet" {
  name   = "${var.name_prefix}-codedeploy-green-fleet"
  role   = aws_iam_role.codedeploy.id
  policy = data.aws_iam_policy_document.codedeploy_green_fleet.json
}

data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:GetObjectVersion",
      "s3:AbortMultipartUpload",
    ]
    resources = ["${aws_s3_bucket.codedeploy_revision.arn}/masiton/codedeploy/*"]
  }

  statement {
    effect    = "Allow"
    actions   = ["s3:GetBucketLocation"]
    resources = [aws_s3_bucket.codedeploy_revision.arn]
  }

  statement {
    effect = "Allow"
    actions = [
      "codedeploy:CreateDeployment",
    ]
    resources = [aws_codedeploy_deployment_group.app.arn]
  }

  # CreateDeployment에 revision을 실어 보내면 CodeDeploy가 그 revision을
  # application에 먼저 등록한다. 등록·조회 권한의 리소스 타입은 deployment group이
  # 아니라 application이므로 위 statement로는 덮이지 않는다.
  statement {
    effect = "Allow"
    actions = [
      "codedeploy:RegisterApplicationRevision",
      "codedeploy:GetApplicationRevision",
    ]
    resources = [aws_codedeploy_app.app.arn]
  }

  statement {
    effect = "Allow"
    actions = [
      "codedeploy:GetApplication",
      "codedeploy:GetDeploymentGroup",
    ]
    resources = [
      aws_codedeploy_app.app.arn,
      aws_codedeploy_deployment_group.app.arn,
    ]
  }

  # Deployment ID와 deployment config 이름은 AWS가 실행 시 생성·선택하므로
  # 조회 API에는 고정된 모듈 ARN을 줄 수 없다. wildcard가 필요한 조회만
  # 별도 statement로 격리하고, 배포 생성은 위 deployment group으로 제한한다.
  statement {
    effect    = "Allow"
    actions   = ["codedeploy:GetDeployment", "codedeploy:GetDeploymentConfig"]
    resources = ["*"]
  }

  # CI timeout/cancel 시 진행 중인 deployment를 중지하고 자동 rollback을
  # 요청해야 한다. AWS가 생성하는 deployment ID 때문에 resource는 wildcard다.
  statement {
    effect    = "Allow"
    actions   = ["codedeploy:StopDeployment"]
    resources = ["*"]
  }

  # deployment ID를 S3 pointer에 저장하기 전에 runner가 죽으면 cleanup job이
  # 중지 대상을 잃는다. 진행 중인 deployment를 revision key로 역조회해 되찾는
  # 재조정 경로에 필요하다. 조회 범위는 이 deployment group으로 제한한다.
  statement {
    effect    = "Allow"
    actions   = ["codedeploy:ListDeployments"]
    resources = [aws_codedeploy_deployment_group.app.arn]
  }

  # batch-get-deployments도 deployment group 리소스 범위를 지원하므로
  # 이 배포 group 외의 deployment 정보는 조회하지 못하도록 제한한다.
  statement {
    effect    = "Allow"
    actions   = ["codedeploy:BatchGetDeployments"]
    resources = [aws_codedeploy_deployment_group.app.arn]
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  count  = var.github_actions_role_name == null ? 0 : 1
  name   = "${var.name_prefix}-codedeploy-deploy"
  role   = var.github_actions_role_name
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}

# 직접 배포 경로의 최소 권한이다. legacy CodeDeploy policy는 cutover 검증이 끝난
# 뒤 별도 정리 단계에서 제거한다.
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
