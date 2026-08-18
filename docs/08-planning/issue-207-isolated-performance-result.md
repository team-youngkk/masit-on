---
related_documents:
  - second-expansion-performance-verification.md
  - issue-207-natural-language-load-model.md
  - third-expansion-final-gate-result.md
  - ../../perf/seed/README.md
---

# GitHub issue #207 격리 성능 검증 결과

## 1. 결론

2026-08-15 KST에 `ap-northeast-2`의 기존 VPC 안에서 운영과 분리된 EC2·RDS·Redis·WireMock·k6 환경으로 검증했다. 운영 EC2, RDS, Redis, 외부 Kakao·YouTube·Gemini API에는 부하를 보내지 않았고 운영 quota·secret·snapshot도 사용하지 않았다.

- 공개 조회 정상 부하 20 RPS: NFR-PERFORMANCE-006의 p95·5xx·dropped 기준 통과.
- 공개 조회 최대 부하 80 RPS: 200 응답, 5xx 0%, dropped 0건의 관찰 결과.
- 자연어 계약 모드: 30건/분, measured 429 0건, p95 24.9ms. 계약 관찰 통과.
- 자연어 throughput 모드: 20/80 RPS 모두 단일 client rate-limit에 의해 429가 발생했다. throughput은 성능 인증이 아니라 포화 거동 관찰 결과다.
- 코스 정상 내부 관측 p95 17.0ms, 외부 호출 포함 p95 13.9ms; 최대 프로필도 각각 11.7ms, 10.3ms였고 502·429·5초 초과·dropped는 0건이었다.

throughput 결과를 NFR 성능 인증으로 해석하지 않는다. 단일 loadgen 주소만 사용했기 때문에 자연어 요청 제한이 먼저 포화됐다.

## 2. 재현 기준

| 항목 | 값 |
|---|---|
| 검증 시각 | 2026-08-15 13:29:13 ~ 14:36:46 KST, 개별 실행 시작·종료는 SSM 기록 기준 |
| Git HEAD | `81014959cc72618c4bc5c8d39ec77d866fa63f60` |
| 앱 이미지 | ECR digest `sha256:a9aa18087c1cac92d62a53441d77d351c648770cd3f1fcdcf56d9cc50ba1a97d`; 이미지 태그 commit `5f251e2b9a03f660f1a44d7d94bfa1b2c465bd16` |
| k6 | v2.1.0, linux/arm64, SHA-256 `191fa8d89512a4e5083f3fabcb4c3828af9f5b9eee016de8443f6473c029ffb5` |
| 스크립트 해시 | `normal-load-public-read.js`: `D9BBBE8357B97DF6A2FB894CBE4C7A0778D4215640FE5D64BA1BA7CB94D2703B`; `third-expansion-load.js`: `FC087C0F5F3089DC04D9D462F1846FFD9B49EE09C1FFD28EA3179ED08600C23B` |
| fixture Run ID | `20260815-01` |
| 공개 조회 대상 | `/api/restaurants/popular`, `/api/curations`, `/api/curations/{id}` |
| 자연어 대상 | `POST /api/restaurants/natural-language-search` |
| 코스 대상 | `POST /api/restaurants/course-routes`; 정상 WireMock stub과 계약이 맞는 3개 stop ID 사용 |

주요 측정 SSM command ID는 공개 조회 정상 `<public-normal-command-id>`, 공개 조회 최대 `<public-max-command-id>`, 코스 정상 internal `<course-normal-internal-command-id>`, 코스 정상 external `<course-normal-external-command-id>`, 코스 최대 internal `<course-max-internal-command-id>`, 코스 최대 external `<course-max-external-command-id>`, 자연어 contract `<natural-contract-command-id>`, 자연어 throughput 정상 `<natural-throughput-normal-command-id>`, 자연어 throughput 최대 `<natural-throughput-max-command-id>`다. Fixture cleanup command ID는 `<fixture-cleanup-command-id>`다.

현재 작업 브랜치의 기존 변경(`.github/workflows/performance.yml`, `perf/k6` 등)은 보존했으며 이 검증을 위해 코드·운영 설정을 수정하지 않았다.

## 3. 격리 환경

기존 VPC `vpc-05441ae76eaa1131c`와 SSM을 재사용하고 NAT Gateway는 만들지 않았다. 앱은 `t4g.medium` EC2, loadgen은 별도 `t4g.small` EC2, DB는 private subnet의 PostgreSQL 17.10 `db.t4g.micro` Single-AZ RDS였다. RDS·앱·loadgen 전용 SG를 사용했고 앱 8080은 loadgen SG에서만, RDS 5432는 앱 SG에서만 허용했다.

WireMock은 arm64 호환을 위해 `wiremock/wiremock:3.13.2`로 실행했다. 매핑과 요청 경로는 동일하며 WireMock은 8081로 실행됐다. Redis는 `redis:8.8-alpine`, `maxmemory=256mb`, `maxmemory-policy=noeviction`으로 실행했다.

앱은 `local` profile로 실행하고 Kakao·YouTube·Mobility base URL을 `127.0.0.1:8081`로 지정했다. Gemini와 AI worker는 비활성화했고 JWT·AES·DB 비밀번호는 모두 격리용 합성 비밀이었다.

## 4. Fixture

기본 `perf/seed`는 restaurant 1,000, creator 200, video 5,000, visit 10,000, member 1,000, favorite 20,000, curation 5, curation_restaurant 100건을 적재했다. 이 seed의 restaurant 좌표는 NULL이므로 코스용으로 재사용하지 않았다.

코스용으로 `PERF-COURSE-20260815-01-*` marker의 좌표 포함 restaurant 5건을 격리 RDS에만 추가했다. WireMock 정상 응답의 sections 2개에 맞춰 실제 코스 실행에는 그중 3개 ID를 사용했다. 5개 ID를 사용한 사전 시도는 stub의 부분 응답으로 502가 되어 별도 실패 증거로 남겼고, 재시도하지 않고 원인을 수정했다.

측정 종료 후 course fixture와 seed를 삭제했고 다음 marker 잔여를 모두 0건으로 확인했다.

`course_fixture_remaining=0`, `seed_restaurant_remaining=0`, `seed_creator_remaining=0`, `seed_video_remaining=0`, `seed_member_remaining=0`, `seed_admin_remaining=0`

## 5. k6 결과

### 5.1 공개 조회

| 프로필 | 요청률/VU | popular p95 | curation list p95 | curation detail p95 | 측정 표본 | 5xx | dropped | 결과 |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| 정상 | 20 RPS / 50 | 19.9ms | 12.2ms | 10.1ms | 6,001 | 0.000% | 0 | NFR 기준 통과 |
| 최대 관찰 | 80 RPS / 200 | 18.9ms | 10.2ms | 7.9ms | 24,001 | 0.000% | 0 | 관찰 결과 |

두 실행 모두 200 아닌 응답은 0건이었다. 인기 endpoint에는 query parameter를 붙이지 않았다.

### 5.2 자연어

| 모드 | 프로필 | p95 | 측정 요청 | 200 | 429 | 429 이외 비정상 | 5xx | dropped | 해석 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| contract | 30건/분, VU 5 | 24.9ms | 301 | 301 | 0 | 0 | 0.000% | 0 | 계약 관찰 통과 |
| throughput | 20 RPS / 50 VU | 20.4ms* | 6,001 | 300 | 6,842 | 0 | 0.000% | 0 | 단일 client rate-limit 포화 관찰 |
| throughput | 80 RPS / 200 VU | 20.8ms* | 24,001 | 300 | 28,442 | 0 | 0.000% | 0 | 단일 client rate-limit 포화 관찰 |

`*` throughput p95는 전체 요청이 아니라 200 응답만의 Trend다. throughput 실행은 성능 인증이 아니다.

### 5.3 코스

| 프로필 | metric mode | 요청률/VU | p95 | 200 표본 | 502 | 429 | 5초 초과 | dropped | 결과 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| 정상 | internal | 5 RPS / 26 | 17.0ms | 601 | 0 | 0 | 0 | 0 | 통과 |
| 정상 | external | 5 RPS / 26 | 13.9ms | 600 | 0 | 0 | 0 | 0 | 통과 |
| 최대 관찰 | internal | 10 RPS / 51 | 11.7ms | 601 | 0 | 0 | 0 | 0 | 관찰 결과 |
| 최대 관찰 | external | 10 RPS / 51 | 10.3ms | 601 | 0 | 0 | 0 | 0 | 관찰 결과 |

각 코스 실행의 예산은 preflight 1 + warmup 200 + measured 600 = 801건으로 격리 quota 1,000 미만이었다. 내부·외부 프로필 사이에는 격리 Redis의 `restaurant:course-route:quota:2026-08` key만 초기화했으며 운영 Redis에는 접근하지 않았다.

## 6. 인프라·외부 연동 증거

- RDS CloudWatch 최근 관측 최대 CPU: 약 4.49%; `DatabaseConnections`는 10; freeable memory는 약 145MiB 이상이었다. ReadIOPS 최근 최대는 약 1.72, WriteIOPS는 약 18.08이었다.
- 앱 확인 시 PostgreSQL `pg_stat_activity`는 11개(앱 pool 10개 포함), RDS `max_connections`는 79였다. Hikari 전용 actuator metric은 노출하지 않아 직접적인 active/idle pool metric은 수집하지 못했다.
- Redis: `total_commands_processed=114990`, `total_error_replies=6`, 수집 시점 `instantaneous_ops_per_sec=0`, used memory 1.69MiB / maxmemory 256MiB, `noeviction`. `total_error_replies`는 수집 시점 누적값이며 애플리케이션 오류로 단정하지 않는다.
- WireMock health는 healthy v3.13.2였다. 요청 저널에는 `/v1/directions` 3,212건이 기록됐고 각 응답 definition은 200이었다. 실제 Kakao·YouTube·Gemini endpoint 호출은 없었다.

## 7. 결과 파일 해시

결과 파일은 loadgen에만 보존했고 저장소에는 커밋하지 않았다. 아래는 각 `summary.txt`와 `summary.json`의 SHA-256이다.

| 결과 | summary.txt | summary.json |
|---|---|---|
| public-read/normal | `679872ae14870adf65b1006fa098a0b74b885e70b6f2c9f5e621d984dc487b53` | `aee5eb4fddd69f5118ca7970479134437dc786768e9f1483fb6d7e499dc2fff4` |
| public-read/max | `bf2734c2b0bfa5d09f236e5413d19694fe70c9799985f47602bb1d3036daf44e` | `48e77cac2ffb059f20aa32440e2dc29b67c9aff3885f4c41c5286a9e057a9154` |
| course/normal-internal (5-stop 부분 응답 시도, 무효) | `fbf091afb90e9482584a5e798ad6f83082ed0bb8efd432c603a684aa03b1e235` | `3de6c67d27f8916ebc0d9b6a0f99bf4731ed34a5913ee197533a3426e93dee64` |
| course/normal-internal-valid | `f9fff6f3365b810bcd195da7cac85360d903e7e28d6084c313452b4acc36bfea` | `bc2c8802af60bb9e53a630d040a8661d09c5b3c568ad9f93fa29d46ade06b3f0` |
| course/normal-external | `a8d3380c823f96ebe4d6b0a9ec19ef3e9056ea317b03a03fd3cad9db0de62590` | `b17137266c9ea1c7e2a66c5e39482fe67ef9e236a143f70daac808474cbc7e6d` |
| course/max-internal | `2c95c53810945a8be67ebb723c5684a6ed2686ea4f865befe8bce8cb8a2ad0a3` | `83717b0e079662d429d6ee79d9e0bff38c6dfbf078b8041a026be52f7c57fd2d` |
| course/max-external | `f3cea071abc74e41bcd1734b5eb58480605cd219fe4731d9b84b5e306f9b130a` | `0278ef58c320ed4e691b5c36543071780e03fbe72201aa58383ecfcdc4f28e30` |
| natural-language/contract-normal | `57efc5c310f44a56e74b5763511f4d36e42fc250fed18642d76ef1ab2e4b7c33` | `53cbd2cd8f0608f4cf5f703441ddc8f8566ad4e49de60e6e0d67a8bb20809386` |
| natural-language/throughput-normal | `56802b4980e7c0bd595d38b3cc65341fbe96e0a58a38d84703d0ac8de2ebcf18` | `2d10af309d0ed3664f9df16d9c7f238c16348c0ca3992db1d16d74da2c4e0fba` |
| natural-language/throughput-max | `f3f7be3b2905f79d80236d9d2ea8478633a49c4634a9896206196574bf138479` | `93b377a3f65d329755417687ade9f92bb772e7f8957a44d99b39bed023551c46` |

## 8. AWS 정리 결과

RunId `20260815-01`의 앱 EC2 `<performance-app-instance-id>`, loadgen EC2 `<performance-loadgen-instance-id>`는 `terminated`다. RDS `masiton-perf-207-20260815-01`와 subnet group, 전용 SG 3개, IAM role/profile, SSM SecureString parameter, 전용 CloudWatch log group은 삭제했다. 임시 root EBS volume도 잔여 없음으로 재확인했다.

운영 리소스인 `<production-app-instance-id>`, `masiton-db`, 운영 SG·parameter·budget·ECR image에는 변경을 가하지 않았다.

## 9. 남은 제약과 후속 결정

이번 결과를 위해 사용자가 추가로 결정할 사항은 없다. 다만 자연어 throughput을 성능 인증으로 승격하려면 단일 client rate-limit을 피할 수 있는 여러 source address/loadgen 구성이 필요하다. 그 경우에도 운영 환경이 아닌 별도 환경에서 source 수, 비용 상한, 측정 기준을 먼저 정해야 한다.
