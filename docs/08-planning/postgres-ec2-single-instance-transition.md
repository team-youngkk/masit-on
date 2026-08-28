---
related_documents:
  - ../../infra/production/README.md
  - deployment-hardening-impact-review.md
  - post-cutover-runtime-baseline.md
  - ../07-adr/data/data-001-postgresql.md
  - ../07-adr/data/data-002-database-placement.md
---

# PostgreSQL EC2·단일 앱 EC2 전환 목표와 비용

## 현재 전환 목표

RDS를 제거하고 PostgreSQL을 별도 EC2로 옮긴다. 앱과 Redis는 동거시키지 않으며, 앱은 단일 `t4g.small`, Redis는 전용 인스턴스로 유지한다. 최종 목표는 ALB·ASG·CodeDeploy를 제거하고 앱 EC2의 EIP를 Route53 A record가 가리키는 구성이다.

이 문서는 비용과 리소스 경계를 기록하는 계획 문서다. 현재 저장소 구현은 기존 경로를 보존한 병행 준비 단계다. PostgreSQL EC2의 AMI, 데이터 volume, 백업·복구·모니터링은 운영 owner가 확정한 뒤 별도 Terraform 레이어와 ADR로 추가해야 한다. 현재 운영 모듈은 PostgreSQL EC2를 생성하지 않고 SG와 SSM JDBC URL만 소비한다.

## 현재 적용 단계

- ALB·ASG·CodeDeploy 리소스와 기존 RDS ingress는 첫 plan의 파괴를 막기 위해 유지한다.
- 단일 앱 EC2·EIP·direct app SG의 80/443 ingress·SSM 배포 IAM·직접 health alarm을 병행으로 준비한다. Redis 레이어에는 legacy app SG와 direct app SG를 모두 허용한다.
- `direct_traffic_enabled=false`에서는 Route53이 기존 ALB를 계속 가리킨다. 앱 EC2를 import하고 SSM 배포 및 외부 smoke를 검증한 뒤에만 `true`로 전환한다.
- DNS 전환과 PostgreSQL endpoint 전환이 안정화된 뒤에야 legacy ALB·ASG·CodeDeploy·RDS를 삭제하는 정리 plan을 별도로 만든다.

## 월 비용 추정

서울 리전(ap-northeast-2), 월 730시간, 2026-08-18 저장소 추정표와 같은 환율(1 USD=1,470원)을 사용한다.

| 구성 | 월 추정 |
| --- | ---: |
| 앱 EC2 `t4g.small` | `$15.18` |
| 앱 root EBS gp3 30 GiB | `$2.74` |
| 앱 EIP/public IPv4 | `$3.65` |
| PostgreSQL EC2 `t4g.small` + gp3 30 GiB | `$17.92` |
| 전용 Redis `t4g.nano` + gp3 8 GiB | `$4.53` |
| Route53·ECR·CloudWatch 등 공용 항목 | `$3.70` |
| 합계 | **`$47.72`** |

PostgreSQL 데이터 volume을 별도 gp3 20 GiB로 추가하면 약 `$1.82`가 더해져 **`$49.54/월`**이다. 따라서 Redis를 앱과 분리한 상태에서도 목표 구성은 `$50/월` 아래로 계산된다.

위 계산은 ALB `$22.27`, RDS 및 RDS volume `$20.87`, Redis SSM interface endpoint `$9.49`를 포함하지 않은 값이다. 데이터 전송, NAT gateway, 추가 EBS snapshot, CloudWatch 사용량, 공인 IPv4의 실제 과금 조건은 별도 확인한다.

## Redis SSM endpoint 조건

계산상 Redis SSM interface endpoint `$9.49`도 제거해야 `$50/월` 아래가 된다. 하지만 현재 전용 Redis는 재기동할 때 `redis-render-conf.sh`가 `/masiton/redis/password`를 SSM에서 읽어 `/run` tmpfs에 설정 파일을 만든다. endpoint를 먼저 삭제하면 Redis 재기동이 실패한다.

따라서 다음 조건을 모두 만족하기 전에는 endpoint 제거를 완료로 판정하지 않는다.

- 비밀번호를 user data·로그·명령행에 노출하지 않는 대체 전달 경로가 있다.
- 인스턴스 교체와 재부팅 후에도 같은 경로가 재현된다.
- Redis 설정 렌더링 계약 테스트와 재부팅 검증을 통과한다.
- 실패 시 Redis가 인증 없이 기동하지 않고, 앱 health alarm이 장애를 감지한다.

## 전환 확인 항목

- `/masiton/db/url`이 PostgreSQL EC2 endpoint를 가리킨다.
- 병행 전환 중에는 PostgreSQL EC2 SG가 legacy app SG와 direct app SG의 5432를 모두 허용한다. legacy 정리 plan에서 legacy app SG rule을 제거한다.
- direct app SG가 Redis SG의 6379만 참조하고, SSM endpoint client 목록에도 포함된다.
- Route53 A record가 ALB가 아닌 EIP를 가리킨다.
- CI가 `PRODUCTION_INSTANCE_ID`를 비운 채 성공하지 않는다.
- 앱·PostgreSQL·Redis 지표가 단일 EC2 alarm에서 정상으로 수집된다.
- 실제 ALB·ASG·CodeDeploy·RDS 삭제는 plan 검토와 데이터 보존 확인 뒤 별도 실행한다.
