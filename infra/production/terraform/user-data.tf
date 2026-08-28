# 기존 ASG와 직접 서비스할 단일 EC2는 bootstrap 계약이 다르다. 기존 ASG는
# CodeDeploy를 계속 사용하고, 직접 EC2는 SSM Run Command를 사용한다. 두 경로를
# 같은 user_data에 묶으면 legacy replacement가 CodeDeploy 없이 기동하는 회귀가
# 생기므로 템플릿과 local을 분리한다.
locals {
  rendered_legacy_user_data = coalesce(var.user_data, templatefile("${path.module}/templates/app-user-data.sh.tftpl", {
    aws_region                = var.aws_region
    nginx_trusted_proxy_cidrs = join(",", var.nginx_trusted_proxy_cidrs)
  }))

  rendered_direct_user_data = templatefile("${path.module}/templates/direct-app-user-data.sh.tftpl", {
    aws_region = var.aws_region
  })
}
