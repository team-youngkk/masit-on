# 기존 deployment-hardening state가 새 단일 EC2 주소로 자연스럽게 이동하도록
# 이름만 바뀐 공유 리소스의 state 주소를 보존한다. 실제 앱 EC2와 EIP는 운영자가
# 확인한 기존 리소스를 별도로 import해야 하며, 이 파일은 자동 import를 수행하지 않는다.
moved {
  from = aws_route53_record.alb["enabled"]
  to   = aws_route53_record.app["enabled"]
}
