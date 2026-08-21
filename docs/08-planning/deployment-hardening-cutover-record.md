---
status: In progress
started_date: 2026-08-19
owners:
  - 이우람
related_documents:
  - deployment-hardening-impact-review.md
  - ../troubleshooting/ops-2026-08-19-alb-cutover-review.md
  - blue-green-cleanup-runbook.md
  - ../07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../07-adr/data/data-005-redis-refresh-token.md
  - ../../infra/production/README.md
---

# 배포 고도화 전환 기록

## 1. 문서 목적

[배포 고도화 비용·일정 영향 검토](deployment-hardening-impact-review.md) 8.2절이 나눈 세 단계를 실제 AWS에 적용한 결과를 남긴다. 검토 문서가 "무엇을 왜 하는가"라면 이 문서는 "무엇이 실제로 일어났는가"이며, 특히 **리허설에서 드러나지 않고 실제 전환에서만 드러난 결함 3건**을 기록한다. 진단 과정과 재발 방지는 [운영 전환 트러블슈팅](../troubleshooting/ops-2026-08-19-alb-cutover-review.md)에 별도로 남겼다.

이 저장소는 공개되어 있다. 보안 그룹 ID, EC2 인스턴스 ID, 사설 IP, hosted zone ID는 `<...>` 자리표시자로 마스킹했다. 실제 값은 AWS 콘솔에서 확인한다.

## 2. 전환 시점의 구성

| 자원 | 값 |
|---|---|
| 배포 리비전 | `7deaf23` (`v0.7.0`, `main`) |
| 성공한 CodeDeploy 배포 | `d-HBU3LWSBK`, 2026-08-19 18:10:59 KST |
| 활성 인스턴스 | `<blue-instance-id>` (t4g.small), ALB target group `masiton-prod-blue`에 healthy |
| 전용 Redis | `<redis-instance-id>` (t4g.nano), 사설 서브넷, 데이터 EBS volume 연결 |
| 기존 단일 인스턴스 | `<legacy-app-instance-id>` (t4g.medium). 전환 시점에는 rollback 경로로 유지했고 같은 날 종료했다(6.1절) |

## 3. 실행 순서와 결과

### 3.1. 1단계 — Redis 분리

전용 Redis 인스턴스와 데이터 volume은 2026-08-18에 생성돼 있었다. 다만 **애플리케이션이 읽는 endpoint가 함께 갱신되지 않았다.** 4.2절에 원인을 남긴다.

### 3.2. 2단계 — ALB 도입

ALB는 HTTPS 443(ACM 인증서)과 HTTP 80(301 승격) listener를 갖췄다. DNS 전환 전에 `--resolve`로 도메인만 ALB IP로 강제해 두 AZ 모두에서 검증했다.

| 검증 | 결과 |
|---|---|
| `GET /` | 302 → `/verification/login#returnTo=/`, TLS 검증 통과 |
| `GET /api/restaurants?page=1&size=10` | 200 |
| `GET /api/creators` | 200 |
| `http://` 접근 | 301 → HTTPS |

같은 요청을 기존 medium 인스턴스에도 보내 응답이 동일한 것을 확인한 뒤 전환했다.

### 3.3. 3단계 — Blue-Green과 DNS 전환

CI `workflow_dispatch`의 `deployment_target=codedeploy` 경로로 배포를 실행했다. 이 경로는 이 배포가 **최초 실행**이다. 그 이전 성공 사례(`d-G97LKY0BK`, 2026-08-18)는 로컬 admin 자격증명과 미커밋 트리로 수행한 리허설이라 CI의 OIDC role 권한과 커밋된 리비전을 검증하지 못했다.

DNS는 [route53.tf](../../infra/production/terraform/route53.tf)의 `initial_blue_verified`를 `true`로 올려 alias record로 전환했다. 기존 apex A record는 Terraform 관리 밖에 있었으므로 `terraform import`로 state에 넣고 in-place 변경으로 처리했다. `allow_overwrite`를 도입하지 않은 이유는 그 속성이 이후 모든 apply에서 무단 덮어쓰기를 허용하기 때문이다.

전환 후 실제 도메인 검증 결과는 3.2절 표와 같고, 접속 IP가 ALB로 바뀐 것을 확인했다.

### 3.4. 배포 경로 단일화와 push 경로 실증

기존 인스턴스를 종료하려면 CI가 먼저 그것을 참조하지 않아야 한다. `main` push 배포가 하드코딩된 인스턴스 ID로 SSM `send-command`를 보내고 있었기 때문이다. instance 경로와 경로 선택 입력을 제거하고 CodeDeploy 하나만 남겼으며, push 이벤트에는 inputs가 없으므로 application·deployment group·revision bucket에 운영 자원 이름을 기본값으로 두었다.

`v0.7.1` 승격이 만든 `main` push가 그 경로로 배포됐다. 배포 `d-EGYARQWBK`가 성공했고 revision key의 run ID와 커밋이 승격 merge commit과 일치했다. **입력 없이 도는 push 배포가 CodeDeploy로 나간 첫 사례다.**

## 4. 실제 전환에서 드러난 결함

4.1과 4.2는 **로컬 admin 자격증명과 동거 Redis 구성에서는 재현되지 않는다.** 리허설의 검증 범위가 실제 경로보다 좁았다는 것이 공통 원인이다. 4.4는 성격이 다르다. 리허설의 문제가 아니라 **토폴로지 변경이 깬 전제를 외부 설정에 반영하지 않은 것**이다.

### 4.1. 배포 역할에 revision 등록 권한이 없었다

CI 첫 실행이 `CreateDeployment`에서 실패했다.

```
AccessDeniedException: not authorized to perform:
codedeploy:RegisterApplicationRevision on resource: ...application:masiton-prod-codedeploy
```

`CreateDeployment`에 revision을 실어 보내면 CodeDeploy가 그 revision을 application에 먼저 등록한다. 등록·조회 권한의 리소스 타입은 deployment group이 아니라 **application**이라, deployment group ARN으로 좁힌 기존 statement가 덮지 못했다. 리허설은 admin 자격증명이라 이 경계를 통과했다.

조치는 [iam.tf](../../infra/production/terraform/iam.tf)에 application ARN 범위의 `codedeploy:RegisterApplicationRevision`·`codedeploy:GetApplicationRevision` statement를 추가한 것이다.

### 4.2. `/masiton/redis/host`가 `127.0.0.1`로 남아 있었다

권한 수정 후 배포는 `AfterInstall` hook에서 실패했다. 인스턴스의 dependency health가 `redis: DOWN`이었고 백엔드 로그는 `Unable to connect to 127.0.0.1/<unresolved>:6379`였다.

전용 Redis 인스턴스는 만들어졌지만 그 사설 IP를 애플리케이션에 알리는 `/masiton/redis/host` 파라미터가 동거 Redis 시절 값 그대로였다. 새 ASG 인스턴스는 로컬 Redis를 설치하지 않으므로 붙을 상대가 없다.

**`REQUIRE_SHARED_REDIS=true` 가드는 이 상황을 막지 못한다.** 이 가드는 값이 *비어 있을 때만* 기동을 실패시키는데, 값이 있긴 했기 때문이다. 값이 로컬 루프백을 가리키는 경우는 검사 대상이 아니었다.

조치는 두 가지다.

- `terraform-redis` 레이어를 apply해 `aws_ssm_parameter.redis_host`가 실제 사설 IP를 갖도록 했다. 이 파라미터는 원래 Terraform이 소유(`manage_host_parameter` 기본값 `true`)하고 있었으므로 코드 변경 없이 apply만으로 교정됐다.
- Redis 보안 그룹의 6379 ingress에 **기존 medium 인스턴스의 보안 그룹을 함께 추가**했다. 파라미터가 공유 자원이라 medium도 다음 재기동 때 전용 Redis로 붙게 되는데, ingress에 신규 ASG SG만 있으면 그 시점에 rollback 경로가 깨진다.

### 4.3. 배포 알람 게이트의 순환 의존

`fleet_dependency_redis` 알람은 `treat_missing_data = "breaching"`이고, 이 알람이 감시하는 `FleetDependencyRedis`(`Environment=asg`) 지표는 배포 리비전에 포함된 `health-metrics.sh`가 올린다. 즉 **지표를 올려줄 리비전을 배포하려는데 그 지표가 없다고 배포가 막히는** 순환이 성립한다.

[codedeploy.tf](../../infra/production/terraform/codedeploy.tf)가 이 상황을 위해 `deployment_alarms_enabled`를 두고 있다. 최초 seeding에서만 `initial_alarm_seeding=true`, `deployment_alarms_enabled=false`, `deployment_auto_rollback_enabled=false`를 같은 명령행에 넘겨 한 사이클을 완화하고, 지표 발생과 알람 `OK` 전환을 확인한 뒤 곧바로 `initial_alarm_seeding=false`·`deployment_alarms_enabled=true`·`deployment_auto_rollback_enabled=true`로 되돌린다. `terraform.tfvars`는 수정하지 않는다. Redis 복구 모드에서는 이 완화를 사용하지 않는다.

### 4.4. 아웃바운드 공인 IP가 바뀌어 YouTube API 키가 거부됐다

DNS 전환 뒤 관리자 AI 화면에서 502가 발생했다. `POST /api/admin/ai/video-extractions`와 `POST /api/admin/video-registration-previews`이며, 백엔드는 `EXTERNAL_SERVICE_ERROR`를 남겼다. 두 경로 모두 YouTube 검증을 거친다.

단일 EC2 시절 아웃바운드 출구는 EIP 하나로 고정돼 있었고 외부 API 키의 IP 제한이 그 전제 위에 있었다. **ASG 인스턴스는 배포마다 새로 만들어져 공인 IP가 매번 바뀐다.** 토폴로지가 그 전제를 깼는데 키 설정은 그대로였다.

조치로 Google API 키의 애플리케이션(IP) 제한을 해제했다. API 제한(`YouTube Data API v3`)은 유지해 키가 다른 Google API로 쓰이지 않게 했다. Kakao 키에는 IP 제한이 없어 영향이 없었고 확인 호출도 정상이었다.

**IP 허용 목록을 요구하는 공급자가 추가되면 다시 부딪힌다.** 아웃바운드 IP 고정은 NAT Gateway가 표준이지만 [비용 검토](deployment-hardening-impact-review.md) 5절이 예산 초과(E3)로 판정한 항목이라 별도 결정이 필요하다.

## 5. 유휴 환경 정리

[Blue-Green 유휴 환경 정리 절차](blue-green-cleanup-runbook.md)에 따라 배포 성공 후 유휴 ASG 2개를 정리했다. 정리 대상은 어제 리허설이 만든 환경과 4.2절에서 실패한 배포가 만든 환경이다.

판정 근거는 runbook이 요구하는 세 가지를 모두 대조했다.

- target group에 healthy로 등록된 인스턴스가 `<blue-instance-id>` 하나뿐이다
- 그 인스턴스의 ASG membership이 `CodeDeploy_masiton-prod-deployment-group_d-HBU3LWSBK`다
- deployment group이 다음 배포의 복사 원본으로 참조하는 ASG도 같다

Terraform이 소유한 `masiton-prod-blue-asg`(desired 0)는 후보에서 제외했다. 정리 후 target group 등록 상태와 알람 4종이 모두 그대로인 것을 확인했다.

## 6. 기존 인스턴스 정리와 남은 작업

### 6.1. 정리 결과

기존 t4g.medium은 2026-08-19에 종료했다. 종료 전에 nginx 접근 로그를 확인해 DNS 전환 이후 도착한 요청이 전부 `status: 444`(도메인이 아닌 IP 직접 접근을 server_name 불일치로 끊은 것)임을 확인했다. 정상 응답은 한 건도 없었다.

동거 Redis 종료와 `masiton-tls-renew.timer` 해제는 따로 수행하지 않았다. 인스턴스를 통째로 종료했으므로 그 단계는 인스턴스를 남길 때만 의미가 있다.

| 자원 | 처리 |
|---|---|
| 인스턴스 | 종료. 루트 볼륨 30GB는 `DeleteOnTermination`으로 함께 삭제 |
| Elastic IP | release. **미사용 EIP는 과금되므로 종료와 함께 회수해야 한다** |
| 최종 스냅샷 | 종료 전 생성 후 대조를 마치고 삭제 |
| 기존 EC2 보안 그룹 | 참조 규칙을 모두 제거한 뒤 삭제 |

보안 그룹은 참조가 남아 있으면 지워지지 않는다. 제거 대상은 세 곳이었다.

- Redis SG의 6379 ingress와 SSM endpoint client 규칙 — `terraform-redis` 레이어가 소유하므로 tfvars에서 SG를 빼고 apply했다
- **RDS SG의 5432 ingress** — M2 자원 생성 때 수동으로 만든 규칙이라 Terraform 관리 밖이었고 CLI로 제거했다. 같은 SG의 운영 ASG 규칙(`manage_rds_ingress_rule`이 소유)은 유지해야 한다. 지우면 다음 배포가 Flyway 연결 timeout으로 실패한다

**계정 전체 보안 그룹을 훑어 참조를 찾은 뒤에 삭제했다.** 관련 있어 보이는 SG만 확인했을 때는 RDS 규칙이 드러나지 않았다.

배포 성공 후 유휴 환경 2개도 [runbook](blue-green-cleanup-runbook.md)에 따라 정리했다.

정리를 마친 뒤 `codedeploy_termination_enabled`를 `true`로 적용해 유휴 환경 자동 종료를 활성화했다. 선행 조건인 "deployment group의 원본 ASG가 seed ASG가 아니라 replacement ASG일 것"은 배포 `d-EGYARQWBK`로 충족됐다. 이후 배포는 성공 15분 뒤 original 인스턴스와 ASG가 자동 종료되며, 그 15분이 rollback 가능 시간의 상한이다. **첫 자동 종료가 실제로 일어나는지는 다음 배포에서 확인해야 한다.**

### 6.2. 남은 작업

| 항목 | 내용 |
|---|---|
| 아웃바운드 IP 고정 | 4.4절 참조. IP 허용 목록을 요구하는 공급자가 추가되면 다시 필요해진다 |
| `REQUIRE_SHARED_REDIS` 가드 보완 | 4.2절 참조. 값이 로컬 루프백일 때도 실패시킬지 결정한다 |
| 진단 메시지 개선 | `app-deploy.sh`의 dependency 실패 메시지가 실제 DOWN 항목 대신 mail을 지목한다. YouTube 어댑터는 upstream 상태 코드를 남기지 않는다 |

## 7. 이 기록이 확인하지 않은 것

- **비용 실측.** 인스턴스 하향과 Redis 분리의 실제 청구액은 다음 청구 주기에 확인한다. [비용·일정 영향 검토](deployment-hardening-impact-review.md) 5절의 산정은 공개 단가 기준이다.
- **t4g.small의 실사용 메모리와 CPU 크레딧 거동.** 같은 검토 4.1절이 추정으로 남긴 항목이며 이번 전환에서 실측하지 않았다.
- **전환 후 장시간 관찰.** 이 문서는 전환 직후 시점의 기록이다.
