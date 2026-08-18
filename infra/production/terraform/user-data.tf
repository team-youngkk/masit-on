# 앱 인스턴스 bootstrap은 저장소에서 버전 관리한다. user_data 변수를 지정하면
# 그 값이 우선하며, 지정하지 않으면 이 템플릿을 사용한다.
locals {
  rendered_user_data = coalesce(var.user_data, templatefile("${path.module}/templates/app-user-data.sh.tftpl", {
    aws_region                = var.aws_region
    nginx_trusted_proxy_cidrs = join(",", var.nginx_trusted_proxy_cidrs)
  }))
}
