# 저장소의 Redis 배포 파일을 S3에 스테이징한다. 인스턴스는 부팅 때 이 객체를
# 받아 redis-install.sh를 실행한다. 비밀값은 포함되지 않는다.
resource "aws_s3_object" "redis_asset" {
  for_each = toset(local.redis_asset_files)

  bucket = var.redis_assets_bucket
  key    = "${var.redis_assets_prefix}/${each.value}"
  source = "${var.redis_assets_source_dir}/${each.value == "redis.conf" || each.value == "masiton-redis.service" ? "redis" : "scripts"}/${each.value}"
  etag   = filemd5("${var.redis_assets_source_dir}/${each.value == "redis.conf" || each.value == "masiton-redis.service" ? "redis" : "scripts"}/${each.value}")
}
