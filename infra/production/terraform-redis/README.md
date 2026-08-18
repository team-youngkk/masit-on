---
related_documents:
  - ../terraform/README.md
  - ../../../docs/07-adr/data/data-005-redis-refresh-token.md
  - ../../../docs/07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../../../docs/08-planning/deployment-hardening-impact-review.md
---

# 전용 private Redis Terraform

이 Accepted 배포 고도화 모듈은 M2에서 앱 인스턴스에 동거하던 Redis를 사설 subnet의 전용 인스턴스로 분리한다. [배포 고도화 영향 검토 8.2절](../../../docs/08-planning/deployment-hardening-impact-review.md)이 정의한 1단계이며, Blue-Green 무중단의 전제다. 2026-08-18 owner 재합의로 [ADR-DATA-005](../../../docs/07-adr/data/data-005-redis-refresh-token.md) 6절의 배포 고도화 운영 배치와 [ADR-DEPLOY-005](../../../docs/07-adr/platform/deploy-005-asg-blue-green-rollout.md)가 Accepted 확정되었다. 실제 Redis 데이터 이전·cutover·복구는 운영 runbook과 리허설을 통과한 뒤 수행한다.

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

## 기존 Redis 상태가 있을 때 최초 전환

새 data EBS가 비어 있으면 bootstrap은 파일시스템을 생성하고 mount한다. 따라서 기존 root volume의 AOF·RDB를 가진 환경에서 바로 `apply`하면 기존 세션·Refresh Token·rate-limit 상태가 새 volume에 나타나지 않는다. 운영 데이터가 있을 수 있으면 아래 offline copy를 먼저 수행하고, 확인 전에는 Redis 레이어를 apply하지 않는다.

```bash
# 1) 현재 Redis를 중지하고 원본 data를 변경하지 않는다.
sudo systemctl stop masiton-redis.service

# 2) Redis 인스턴스와 같은 AZ에 암호화 EBS를 만든 뒤 현재 인스턴스에 attach한다.
aws ec2 create-volume --availability-zone <redis-az> --size <gib> --volume-type gp3 --encrypted
aws ec2 attach-volume --volume-id <new-volume-id> --instance-id <current-redis-instance-id> --device /dev/sdf

# 3) 새 volume을 임시 경로에 mount하고 원본 data를 복사한다.
sudo mkfs.ext4 /dev/nvme1n1
sudo mkdir -p /mnt/masiton-redis-data
sudo mount /dev/nvme1n1 /mnt/masiton-redis-data
sudo rsync -aHAX --numeric-ids /opt/masiton/redis/data/ /mnt/masiton-redis-data/

# Redis 7+ multipart AOF의 manifest와 파일을 확인한다. redis-check-aof는
# Redis service가 사용하는 동일한 digest 이미지로 실행해 호스트 도구 차이를 없앤다.
sudo test -f /mnt/masiton-redis-data/appendonlydir/appendonly.aof.manifest
REDIS_IMAGE="$(sudo sed -n 's/^Environment=IMAGE=//p' /etc/systemd/system/masiton-redis.service)"
AOF_FILES="$(sudo find /mnt/masiton-redis-data/appendonlydir -type f \
  \( -name '*.base.aof' -o -name '*.incr.aof' \) -print)"
[ -n "$AOF_FILES" ]
while IFS= read -r aof_file; do
  sudo docker run --rm -v /mnt/masiton-redis-data/appendonlydir:/data:ro \
    "$REDIS_IMAGE" redis-check-aof "/data/$(basename "$aof_file")"
done <<< "$AOF_FILES"

# 4) 복사본을 unmount하고 attachment를 분리한다. 이후 volume을 Terraform state에 import한다.
sudo umount /mnt/masiton-redis-data
aws ec2 detach-volume --volume-id <new-volume-id>
terraform import aws_ebs_volume.redis_data <new-volume-id>
terraform plan -out=redis.tfplan
```

`/dev/nvme1n1`은 예시다. `lsblk`와 volume ID를 대조해 실제 장치를 확인한다. 새 Redis를 기동한 뒤에는 운영용 비밀값을 셸의 `REDISCLI_AUTH`에 안전하게 주입하고 아래처럼 기존 fixture key가 복구됐는지 확인한다.

```bash
sudo systemctl start masiton-redis.service
sudo docker exec -e REDISCLI_AUTH="$REDISCLI_AUTH" masiton-redis \
  redis-cli --raw EXISTS '<known-fixture-key>' | grep -qx 1
```

manifest·`redis-check-aof`·known fixture key 검증을 모두 통과한 뒤에만 replacement 인스턴스와 attachment 교체를 승인한다. 운영 데이터가 없는 신규 도입이면 그 사실을 plan 승인 기록에 남기고 빈 volume 경로를 사용한다.

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
