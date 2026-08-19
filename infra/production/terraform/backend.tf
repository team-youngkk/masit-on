terraform {
  backend "s3" {
    key     = "production/deployment-hardening/terraform.tfstate"
    region  = "ap-northeast-2"
    encrypt = true
  }
}
