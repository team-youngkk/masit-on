---
related_documents:
  - ../08-planning/deployment-hardening-cutover-record.md
  - ../08-planning/blue-green-cleanup-runbook.md
  - ../07-adr/platform/deploy-005-asg-blue-green-rollout.md
  - ../07-adr/data/data-005-redis-refresh-token.md
  - ../../infra/production/README.md
  - ../../infra/production/terraform-redis/README.md
---

# 운영 전환 트러블슈팅: ALB·Blue-Green 트래픽 전환에서 드러난 결함 3건

## 1. 개요

| 항목 | 내용 |
|---|---|
| 대상 | `develop`·`main`의 운영 배포 경로와 AWS 운영 자원 |
| 작성자 | w00lam |
| 처리 일자 | 2026-08-19 |
| 범위 | 단일 EC2 → ALB + ASG Blue-Green 트래픽 전환, CI CodeDeploy 경로 최초 실행, 기존 인스턴스 정리 |
| 주 문제 유형 | 배포 / 인프라 / 외부 연동 |
| 기존 기록 | [PR #228 ASG replacement 배포](pr-228-asg-replacement-deployment-review.md), [PR #253 CodeDeploy 단일 경로 전환](pr-253-codedeploy-only-contract-test-review.md), [배포 고도화 전환 기록](../08-planning/deployment-hardening-cutover-record.md)을 확인했다. 세 결함 모두 리뷰가 아니라 **실제 전환 실행에서** 드러났으므로 새 기록으로 남긴다. |

이 저장소는 공개되어 있다. 보안 그룹 ID, EC2 인스턴스 ID, 사설 IP는 `<...>` 자리표시자로 마스킹했다. 공인 IP는 이미 회수·교체된 값만 적는다.

**이 기록이 PR 리뷰 기반이 아닌 첫 문서다.** 기존 기록은 모두 `pr-<번호>-` 규약을 따르지만, 이번 문제는 코드 리뷰가 아니라 운영 전환 중에 드러났다. 파일명을 `ops-<날짜>-`로 두고 [인덱스](README.md)에 `운영 전환` 절을 새로 만들었다.

## 2. 문제 처리 결과

| # | 문제 | 문제 유형 | 판단 | 처리 결과 | 근거/검증 |
|---|---|---|---|---|---|
| 1 | CI 배포 역할에 `codedeploy:RegisterApplicationRevision`이 없어 `CreateDeployment`가 AccessDenied | 인프라 / 배포 | 수정 필요 | application ARN 범위 statement 추가 ([#252](https://github.com/team-youngkk/masit-on/pull/252)) | 수정 후 배포 `d-B98S5GTBK`가 `CreateDeployment`를 통과 |
| 2 | `/masiton/redis/host`가 `127.0.0.1`로 남아 신규 ASG 인스턴스의 Redis dependency가 DOWN | 인프라 / 데이터베이스 | 수정 필요 | `terraform-redis` apply로 파라미터를 전용 Redis 사설 IP로 교정하고, 기존 EC2 SG를 Redis 6379 ingress에 추가 | 배포 `d-HBU3LWSBK` 성공, dependency health 세 항목 UP |
| 3 | 아웃바운드 공인 IP가 바뀌어 IP 제한이 걸린 YouTube API 키가 거부됨 | 외부 연동 / 인프라 | 수정 필요 | Google API 키의 애플리케이션(IP) 제한을 해제하고 API 제한은 유지 | 인스턴스에서 YouTube Data API 호출 `http=200` 확인 |
| 4 | 알람 지표를 올리는 리비전을 배포해야 하는데 그 지표 결측이 배포를 막는 순환 | 배포 | 설계된 완화 사용 | 한 사이클만 `-var deployment_alarms_enabled=false`로 배포하고 지표 발생 후 기본값 복원 | 알람 `masiton-prod-fleet-dependency-redis` OK 전환 확인 |

## 3. 문제 현상과 발생 조건

- **오류 메시지**
  - `AccessDeniedException: not authorized to perform: codedeploy:RegisterApplicationRevision on resource: ...application:masiton-prod-codedeploy`
  - `AfterInstall` hook 실패, `백엔드 mail dependency 확인 실패: HTTP 503`
  - `io.lettuce.core.RedisConnectionException: Unable to connect to 127.0.0.1/<unresolved>:6379`
  - `POST /api/admin/ai/video-extractions` → 502, 백엔드 `business error: code=EXTERNAL_SERVICE_ERROR`
- **발생 환경**: `main` 기준 CI `workflow_dispatch` CodeDeploy 경로, EC2/On-Premises Blue-Green, 전용 Redis 분리 직후, ALB alias 전환 전후.
- **재현 조건**: 로컬 admin 자격증명이 아닌 **CI OIDC role**로 배포할 때, 그리고 **로컬 Redis가 없는 신규 ASG 인스턴스**에서 기동할 때.
- **실제 결과**: CI 경로 최초 배포 2회 연속 실패. 전환 후 관리자 AI 화면에서 502 5건.
- **기대 결과**: 승인 후 배포가 replacement 환경을 만들고 dependency health 세 항목이 UP인 상태로 target group에 등록되어야 한다.
- **영향 범위**: 배포 파이프라인 전체. 전환 후 관리자 AI 영상 등록·미리보기 기능. 공개 조회 API와 로그인은 영향 없었다(`/api/restaurants`·`/api/creators` 200 유지).

## 4. 근본 원인

1. **권한 경계를 리소스 타입 단위로 검증하지 않았다.** `CreateDeployment`는 deployment group ARN으로 좁혔는데, 같은 호출이 유발하는 revision 등록의 리소스 타입은 **application**이다. 두 ARN이 다르다는 사실이 정책 설계에 반영되지 않았다.
2. **리허설의 자격증명과 실행 경로가 실제와 달랐다.** 2026-08-18 리허설(`d-G97LKY0BK`)은 로컬 admin 자격증명과 미커밋 트리로 수행했다. admin은 1번 경계를 통과하고, 로컬 트리는 CI가 만드는 revision과 다르다. **성공한 리허설이 CI 경로를 증명하지 못했다.**
3. **공유 파라미터의 소유자와 소비자가 어긋났다.** `/masiton/redis/host`는 `terraform-redis`가 소유(`manage_host_parameter` 기본 `true`)하는데 해당 apply가 완료되지 않아 동거 Redis 시절 값이 남았다. 신규 ASG는 로컬 Redis를 설치하지 않으므로 그 값으로는 붙을 대상이 없다.
4. **가드가 "값 없음"만 검사하고 "값이 무의미함"은 검사하지 않았다.** `REQUIRE_SHARED_REDIS=true`는 endpoint가 비어 있을 때만 기동을 실패시킨다. `127.0.0.1`은 비어 있지 않아 통과했고, 실패는 기동이 아니라 배포 hook의 dependency health 단계까지 밀렸다.
5. **ASG 인스턴스는 배포마다 새로 만들어져 아웃바운드 공인 IP가 매번 바뀐다.** 단일 EC2 시절에는 EIP 하나가 고정 출구였고 외부 API 키의 IP 제한이 그 전제 위에 있었다. 토폴로지가 그 전제를 깼는데 키 설정은 그대로였다.
6. **오류 메시지가 원인을 가렸다.** `app-deploy.sh`는 dependency health 응답이 200이 아니거나 mail이 UP이 아닐 때 같은 문장(`백엔드 mail dependency 확인 실패`)을 출력한다. 실제 DOWN 항목은 redis였다. 또 `YouTubeVideoVerificationAdapter`는 upstream 상태 코드를 로그에 남기지 않아 502의 외부 원인을 로그만으로는 특정할 수 없었다.

## 5. 확인 및 시도

- CodeDeploy 배포 이력을 조회해 성공 사례가 `rehearsal-local-v2` 리비전 1건뿐임을 확인했다. CI 실행 기록에서 `workflow_dispatch`가 2026-08-12 이후 없었고 `main` push는 `DEPLOYMENT_TARGET=instance`로 돌고 있었다.
- 실패한 인스턴스의 `scripts.log`를 SSM으로 읽어 `AfterInstall` 실패 지점을 특정했다. dependency health 응답 본문을 직접 조회해 `db: UP, mail: UP, redis: DOWN`을 얻었다. **메시지가 지목한 mail은 정상이었다.**
- Redis 보안 그룹 규칙을 조회해 6379 ingress가 신규 ASG SG에 열려 있음을 확인했다. 즉 네트워크가 아니라 endpoint 값 문제로 좁혀졌고, `/masiton/redis/host`가 `127.0.0.1`임을 확인했다.
- 502는 nginx 접근 로그를 `$.status >= 500`으로 필터링해 대상 엔드포인트를 특정하고, 컨테이너 로그에서 `EXTERNAL_SERVICE_ERROR`를 확인했다. 두 엔드포인트 모두 YouTube 검증 경로였고 Kakao 경로에서는 5xx가 없었다.
- 인스턴스에서 YouTube·Kakao API를 직접 호출해 아웃바운드 IP와 응답을 대조했다.

## 6. 최종 해결

1. `iam.tf`에 application ARN 범위의 `codedeploy:RegisterApplicationRevision`·`codedeploy:GetApplicationRevision`을 추가했다.
2. `terraform-redis`를 apply해 `/masiton/redis/host`를 전용 Redis 사설 IP로 교정하고, 같은 apply에서 기존 EC2 SG를 Redis 6379 ingress에 추가했다. 파라미터가 공유 자원이라 기존 인스턴스가 재기동될 때 rollback 경로가 깨지지 않게 하려는 조치였다.
3. Google API 키의 애플리케이션(IP) 제한을 해제했다. **API 제한(`YouTube Data API v3`)은 유지**해 키가 다른 Google API로 쓰이지 않게 했다.
4. 알람 순환은 `deployment_alarms_enabled`를 한 사이클만 `-var`로 완화하고 지표 발생 후 기본값으로 되돌렸다. `terraform.tfvars`는 수정하지 않았다.

## 7. 검증

| 항목 | 결과 |
|---|---|
| CI CodeDeploy 배포 (`workflow_dispatch`) | `d-HBU3LWSBK` Succeeded |
| CI CodeDeploy 배포 (`main` push, 입력 없음) | `d-EGYARQWBK` Succeeded. 기본값 3종으로 동작 확인 |
| dependency health | `db`·`mail`·`redis` 모두 UP |
| `FleetDependencyRedis` (`Environment=asg`) | 지표 발생, 알람 OK 전환 |
| 전환 후 외부 응답 | `/` 302 로그인 게이트, `/api/restaurants`·`/api/creators` 200, HTTP 301 승격. 전환 전 기존 인스턴스 응답과 동일 |
| YouTube API 키 | 인스턴스에서 `http=200` |
| Kakao API 키 | 인스턴스에서 `http=200`. IP 제한 없음 확인 |

**검증하지 못한 것:** YouTube 키의 실패 원인이 IP 제한이라는 것은 **정황 증거**다. 제한 해제 전에 응답 본문의 `reason`을 수집하지 않았고, 확인 호출은 해제 이후에 실행했다. 시점 일치(아웃바운드 IP 변경 이후 발생), 경로 한정(YouTube만 실패, Kakao 정상), 해제 후 정상화의 세 가지로 판단했다.

## 8. 재발 방지 및 다음 확인

- **리허설은 실제 자격증명과 실제 산출물로 한다.** 로컬 admin으로 통과한 배포는 CI role 권한을 증명하지 못한다. 최소한 revision을 CI가 만든 것과 같은 방식으로 패키징하고 배포 role로 실행해야 한다.
- **`REQUIRE_SHARED_REDIS` 가드를 값 검사로 확장할지 결정한다.** endpoint가 루프백이면 공유 Redis가 아니므로 기동을 실패시키는 편이 배포 hook까지 끌고 가는 것보다 진단이 빠르다.
- **`app-deploy.sh`의 dependency 실패 메시지에 실제 DOWN 항목을 넣는다.** 지금은 mail을 지목해 30분을 잘못된 방향으로 쓰게 만들었다.
- **`YouTubeVideoVerificationAdapter`가 upstream 상태 코드를 남기게 한다.** 본문에 키가 포함되지 않으므로 상태 코드와 `reason`은 로그로 남길 수 있다.
- **아웃바운드 IP 고정 여부를 결정한다.** 이번에는 IP 제한 해제로 우회했지만, IP 허용 목록을 요구하는 공급자가 추가되면 다시 부딪힌다. NAT Gateway는 [비용 검토](../08-planning/deployment-hardening-impact-review.md) 5절에서 예산 초과(E3)로 판정된 항목이다.
- **Terraform state 밖의 자원을 정리 목록에 포함한다.** M2 시기에 수동 생성한 규칙은 코드에 없어 plan에 나타나지 않는다. 인스턴스를 없앨 때는 그 인스턴스의 SG를 참조하는 규칙을 계정 전체에서 조회해야 한다.

## 9. 도입 전후 비교 지표

| 지표 | 전환 전 | 전환 후 |
|---|---|---|
| 앱 인스턴스 | t4g.medium 1대(EIP 고정) | t4g.small 1대(ASG, 배포마다 교체) |
| Redis | 앱 인스턴스 동거 | t4g.nano 전용 인스턴스, 데이터 EBS 분리 |
| 배포 경로 | SSM `send-command`로 고정 인스턴스 | CodeDeploy Blue-Green, push 경로 기본값 |
| 배포 중 중단 | 컨테이너 교체 구간 존재 | replacement 등록 후 original 해제 |
| 아웃바운드 공인 IP | EIP 고정 | 인스턴스마다 변동 |
| 유휴 환경 정리 | 해당 없음 | 성공 15분 뒤 CodeDeploy 자동 종료. 실패·중단된 배포만 runbook 수동 정리 |

## 10. 남은 사항

- 기존 인스턴스 정리는 완료했다(인스턴스 종료, EIP release, 최종 스냅샷 생성 후 삭제). 루트 볼륨은 `DeleteOnTermination`으로 함께 삭제됐다.
- 기존 EC2 SG 정리는 완료했다. **보안 그룹 삭제는 참조를 계정 전체에서 찾아야 한다.** Redis SG와 SSM endpoint 규칙을 `terraform-redis` apply로 지운 뒤에도 삭제가 막혔고, 계정의 모든 보안 그룹을 훑어 **RDS SG의 5432 ingress**가 남아 있는 것을 찾았다. 그 규칙은 M2 자원 생성 때 수동으로 만든 것이라 Terraform state에 없었다. 관련 있어 보이는 SG만 확인했을 때는 드러나지 않았다.
- 유휴 환경 자동 종료(`codedeploy_termination_enabled`)를 활성화했다. 첫 자동 종료 관측은 다음 배포에서 확인한다.
- 전환 후 장시간 관찰과 비용 실측은 이 기록의 범위 밖이다.
