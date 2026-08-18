---
related_documents:
  - ../terraform/README.md
  - ../../../docs/07-adr/data/data-005-redis-refresh-token.md
  - ../../../docs/07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../../../docs/08-planning/deployment-hardening-impact-review.md
---

# 전용 private Redis Terraform

앱 인스턴스에 동거하던 Redis를 사설 subnet의 전용 인스턴스로 분리한다. [배포 고도화 영향 검토 8.2절](../../../docs/08-planning/deployment-hardening-impact-review.md)이 이것을 1단계로 지정했고, Blue-Green 무중단의 전제다. 색상이 바뀌어도 세션·Refresh Token·rate-limit 상태가 유지되어야 하기 때문이다.

`../terraform`(운영 ASG·Blue-Green)과 state를 분리한다. 그 모듈은 `redis_security_group_id`를 **입력으로만** 받고 Redis를 만들지 않는다. 이 모듈의 `redis_security_group_id` 출력을 그 입력에 넣는다.

## 이 모듈이 만드는 것

| 자원 | 비고 |
|---|---|
| 전용 Redis EC2 | 사설 subnet, 퍼블릭 IP 없음([ADR-DATA-005](../../../docs/07-adr/data/data-005-redis-refresh-token.md) 10·11절) |
| Redis data EBS | root volume과 분리된 암호화 gp3. user-data·AMI 변경에 따른 인스턴스 교체에도 `prevent_destroy`로 상태를 보존한다 |
| Redis security group | 6379는 app security group 출처만. egress는 SSM endpoint·S3·VPC DNS로 좁힌다 |
| S3 gateway endpoint | 무료. 설정 파일을 받는 경로 |
| `ssm` 인터페이스 endpoint | `redis-render-conf.sh`가 **매 기동마다** Parameter Store를 읽는다. 설정이 tmpfs에 있어 재기동마다 렌더링해야 하므로 상시 필요하다 |
| EC2 Instance Connect Endpoint | 관리 접속. SSM Agent용 `ssmmessages`·`ec2messages` endpoint를 두지 않아 RunCommand는 쓸 수 없다 |
| IAM role·instance profile | Parameter Store 1개, KMS 복호화, 설정 파일 prefix 읽기만 |
| S3 설정 객체 | 저장소의 `deploy/redis`·`deploy/scripts` 파일을 스테이징한다 |
| `/masiton/redis/host`·`port` | 애플리케이션과 `app-deploy.sh`가 읽는 접속 정보 |

## 적용 전 필수 확인

- **`ssm` endpoint의 private DNS는 VPC 전역에 적용된다.** `ssm.<region>.amazonaws.com`이 이 endpoint로 해석되므로, SSM Agent를 쓰는 **기존 인스턴스의 security group을 `ssm_endpoint_client_security_group_ids`에 반드시 포함**해야 한다. 빠뜨리면 그 인스턴스가 SSM에서 이탈한다. 2026-08-18에 실제로 기존 운영 인스턴스가 약 5분간 이탈했다.
- `redis_ami_id`는 docker와 digest 고정 Redis 이미지를 미리 담은 AMI다. `docker pull`로 받은 이미지를 그대로 담아야 `RepoDigest`가 보존되고 `masiton-redis.service`의 digest 고정이 동작한다. `docker load`로 적재한 이미지는 digest 참조가 깨진다.
- `preserve_client_ip`는 `false`를 유지한다. `true`면 대상 인스턴스가 보는 출처가 원래 클라이언트 IP가 되어 22를 endpoint security group 출처로 허용한 규칙에 매칭되지 않는다. 값을 바꾸면 endpoint가 교체된다.
- 전용 Redis는 복제가 없는 단일 장애점이다. [ADR-DATA-005](../../../docs/07-adr/data/data-005-redis-refresh-token.md) 12절이 Redis 장애를 fail-closed로 정했으므로 **Redis가 죽으면 앱이 살아 있어도 인증이 전면 중단된다.**
- Redis AOF·RDB는 별도 data EBS에 `/opt/masiton/redis/data`로 mount한다. Terraform이 Redis 인스턴스를 교체하면 attachment만 새 인스턴스로 바뀌며, data volume은 자동 삭제하지 않는다. volume을 의도적으로 폐기할 때는 `prevent_destroy`를 해제하는 별도 승인과 백업 확인이 필요하다.
- [ADR-DATA-005](../../../docs/07-adr/data/data-005-redis-refresh-token.md) 6절은 2026-07-30에 공동 owner 합의로 "앱 인스턴스 동거"로 개정한 조항이다. **분리는 그 개정을 되돌리므로 같은 owner의 합의가 다시 필요하다.**

## AMI 만드는 절차

퍼블릭 subnet에 임시 인스턴스를 띄워 이미지를 받아 굽고 종료한다. 이미지 digest를 갱신할 때 같은 절차를 반복한다.

```bash
# user-data: dnf install -y docker; systemctl enable --now docker;
#            docker pull redis@sha256:<digest>
aws ec2 run-instances --image-id <al2023-arm64> --instance-type t4g.nano \
  --subnet-id <public-subnet> --user-data fileb://ami-build-ud.sh
aws ec2 create-image --instance-id <builder> --name masiton-redis-al2023-arm64-<digest-prefix>
aws ec2 terminate-instances --instance-ids <builder>
```

## 검증

```powershell
terraform init -backend=false
terraform fmt -check -recursive
terraform validate
```

실제 적용은 원격 state를 준비한 뒤 수행한다.

```powershell
terraform init -backend-config=backend.hcl
terraform plan -out=redis.tfplan
terraform show -no-color redis.tfplan
```

`apply`는 plan에서 기존 자원에 `replace`·`destroy`가 없는 것을 확인하고 승인한 뒤 실행한다.

`imports.tf`는 2026-08-18에 CLI로 먼저 만든 자원을 흡수하기 위한 일회성 파일이다. 흡수가 끝나면 삭제한다.
