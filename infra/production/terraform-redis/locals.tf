locals {
  common_tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
    Service     = "masiton"
  }

  redis_asset_files = [
    "redis.conf",
    "masiton-redis.service",
    "redis-render-conf.sh",
    "redis-install.sh",
  ]
}
