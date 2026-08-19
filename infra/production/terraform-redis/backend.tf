terraform {
  backend "s3" {
    key     = "production/redis/terraform.tfstate"
    region  = "ap-northeast-2"
    encrypt = true
  }
}
