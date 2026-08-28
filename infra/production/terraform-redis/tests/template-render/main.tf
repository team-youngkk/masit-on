terraform {
  required_version = "= 1.6.6"
}

locals {
  rendered_user_data = templatefile("${path.module}/../../templates/redis-user-data.sh.tftpl", {
    aws_region                = "ap-northeast-2"
    assets_bucket             = "fixture-bucket"
    assets_prefix             = "masiton/redis/assets"
    redis_password_object_key = "masiton/redis/secret/redis-password"
    asset_files               = ["redis-install.sh", "redis-render-conf.sh"]
    data_volume_id            = "vol-0123abcd"
  })
}

output "rendered_user_data" {
  value = local.rendered_user_data
}
