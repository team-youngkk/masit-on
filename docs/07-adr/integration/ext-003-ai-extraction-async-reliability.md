---
id: ADR-EXT-003
title: AI 추출 비동기 작업과 단일 EC2 복구 경계
status: Accepted
decision_date: 2026-08-10
owners:
  - 이우람
related_requirements:
  - FR-AIEXTRACT-001
  - FR-AIEXTRACT-002
  - FR-AIEXTRACT-004
  - FR-AIEXTRACT-005
  - FR-AIEXTRACT-006
  - BR-AIEXTRACT-003
  - BR-AIEXTRACT-005
  - NFR-RELIABILITY-005
  - NFR-EXTERNAL-005
  - NFR-AVAILABILITY-003
  - NFR-COST-001
related_documents:
  - ../../02-analysis/third-expansion-domain-boundaries.md
  - ../../02-analysis/third-expansion-workstreams.md
  - ../../04-product/prd/admin/ai-video-information-extraction.md
  - ../../08-planning/third-expansion-baseline-review.md
  - ../../08-planning/third-expansion-evaluation-strategy.md
  - ../integration/ext-001-reference-verification.md
  - ../architecture/arch-002-external-ports-adapters.md
  - ../data/data-001-postgresql.md
  - ../adr-backlog.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-EXT-003 AI 추출 비동기 작업과 단일 EC2 복구 경계

## 1. 상태

Accepted. 애플리케이션 내부 Worker를 사용하고 n8n·외부 Queue·Spring Batch를 도입하지 않는 기준과 실행 수치는 2026-08-10 확정했다. 단일 EC2의 실제 처리량은 3차 확장 최종 부하 게이트에서 증명한다.

## 2. 결정 요약

AI 추출 요청은 HTTP 요청이나 YouTube Webhook 수신 안에서 AI 호출과 정식 등록을 끝내지 않고, 영속 작업 상태를 기록한 뒤 비동기 Worker가 처리한다. 초기 운영 토폴로지에서는 PostgreSQL을 작업 상태의 기준 저장소로 사용하고, 애플리케이션 내부의 제한된 Worker를 사용한다. 별도 메시지 브로커·n8n·범용 배치 플랫폼·Redis 분산 락은 이 ADR만으로 도입하지 않는다.

작업은 멱등성 키와 lease/claim 상태로 중복 실행을 통제한다. 프로세스 재기동 또는 Worker 장애로 lease가 만료된 작업은 제한된 재시도 또는 명시적 실패로 전환하며, 무한 재시도하지 않는다. 기존 수동 등록과 공개 탐색은 AI Worker 장애와 격리한다.

## 3. 배경

AI 호출은 외부 지연·quota·실패가 있고 관리자 요청과 같은 HTTP 수명 안에 두면 요청 timeout과 단일 EC2 Thread Pool을 함께 소모한다. 반대로 초기부터 Kafka·SQS·별도 Worker 인스턴스·Redis 락을 추가하면 현재 단일 EC2 운영 규모와 비용 경계를 넘어선다.

2차 확장 이후 단일 EC2에서 비동기 작업을 감당할 수 있는지는 아직 측정되지 않았고, 3차 확장 최종 게이트에서 용량을 검증하기로 했다. 이 미측정은 실행 정책 미결정이 아니라 완료 증거의 공백이다.

## 4. 결정 문제

작업 상태를 잃지 않으면서 단일 EC2의 운영 복잡도·비용·장애 전파를 제한하는 초기 실행 경계를 어떻게 둘 것인가.

## 5. 결정

### 5.1. 요청과 실행 분리

- 관리자 요청은 유효성·중복 확인·작업 생성과 상태 조회용 식별자 반환까지만 수행한다.
- 외부 AI 호출은 DB 트랜잭션 안에서 수행하지 않는다.
- 작업 상태는 `QUEUED`·`RUNNING`·`SUCCEEDED`·`FAILED`, 결과 완전성은 `COMPLETE`·`PARTIAL`을 사용한다.
- 동일 영상·입력 해시·모델·Prompt·Schema 조합은 멱등성 키로 수렴시킨다.
- Webhook 수신 작업과 관리자 신규 영상 추가 작업은 `WEBHOOK`·`ADMIN` 유입 경로를 기록하되 같은 멱등성 키와 후보 경계를 공유한다.
- Webhook 수신기는 영상 식별·작업 등록·응답만 수행하고 AI 제공자 호출은 Worker로 위임한다.

### 5.2. 초기 Worker

- PostgreSQL의 영속 작업 상태를 기준으로 애플리케이션 내부 제한 Worker가 처리한다.
- 한 작업은 claim/lease를 획득한 Worker만 실행한다.
- lease 만료·프로세스 재기동 시 `RUNNING` 작업을 재시도 가능 상태로 되돌릴 수 있어야 한다.
- 인스턴스당 Worker 1개, polling 5초, lease 120초, heartbeat 30초를 사용한다. Gemini는 연결 5초·응답 90초·시도 120초 timeout, 최대 2회 재시도(총 3회), backoff 5초·30초를 적용한다. 정책·입력·Schema 오류는 재시도하지 않는다.
- 별도 메시지 브로커·n8n·Spring Batch·Redis 분산 락은 3차 확장에 포함하지 않는다. Worker 한계가 확인될 때만 별도 ADR로 재검토한다.

### 5.3. 장애와 기능 격리

- AI 제공자 timeout·429·5xx는 작업 실패 유형으로 기록하고 기존 수동 등록 흐름을 계속 사용할 수 있게 한다.
- AI Worker가 중단되어도 Restaurant·Creator·Video·Visit 공개 조회와 기존 관리자 수동 등록을 중단하지 않는다.
- 작업 재시도는 같은 후보 결과를 중복 정식 저장하지 않도록 AI 후보 경계와 기존 등록 명령의 멱등성을 함께 사용한다.
- 비용 hard stop 또는 quota 초과 시 신규 AI 작업을 차단하고 관리자에게 수동 등록 fallback을 제공한다.
- 초기 데이터 적립 작업은 `BACKFILL` 우선순위로 두고 실시간 신규 영상 작업보다 낮게 처리한다. 관리자가 초기 적립 범위를 나누어 실행·일시정지할 수 있어야 한다.
- 입력 원문·자막·비밀정보를 작업 로그에 기록하지 않는다. 로그에는 작업 식별자, 상태, 오류 분류와 시각만 남긴다.
- 관리자 보완 텍스트는 Worker 재시작 복구에 필요한 동안만 암호화된 임시 입력으로 저장하고, 작업 종료 후 24시간 이내 삭제한다. Webhook 작업에는 보완 텍스트 임시 입력을 만들지 않는다.

## 6. 고려한 선택지

- **HTTP 동기 처리**: 구현은 단순하지만 외부 지연이 관리자 요청·단일 EC2 자원을 점유하고 재기동 복구가 어렵다.
- **외부 메시지 브로커와 독립 Worker**: 확장성과 격리는 좋지만 현재 단일 EC2·비용·운영 범위를 크게 넓힌다.
- **PostgreSQL 작업 상태 + 제한된 애플리케이션 Worker**: 영속 상태·복구·멱등성을 확보하면서 구성요소를 최소화한다. 단일 EC2 용량 한계를 반드시 측정해야 한다.

## 7. 트레이드오프

초기 Worker는 수평 확장과 처리량이 제한되고, DB polling·claim 구현이 필요하다. 그러나 현재 운영 규모에서 새 브로커를 선제 도입하지 않고 작업 유실·중복·기능 전체 장애를 통제할 수 있다. 용량 검증에서 한계가 확인되면 별도 Queue/Worker ADR을 새로 작성한다.

## 8. 검증 방법과 실행 게이트

- 재기동 중 `RUNNING` 작업의 lease 복구와 중복 claim을 검증한다.
- 외부 timeout·429·5xx·Schema 실패·비용 hard stop을 주입한다.
- 정상 처리·부분 결과·재시도 초과·관리자 폐기와 수동 등록 fallback을 검증한다.
- 단일 EC2에서 Worker 동시성, DB 부하, AI 호출량, 공개 조회 격리를 측정한다.
- 2차 정상 50명·20 RPS와 최대 200명·80 RPS 승계 측정 결과를 함께 확인한다.
- [NFR-RELIABILITY-005](../../01-requirements/non-functional-requirements.md#nfr-reliability-005-ai-비동기-작업-복구), [NFR-AVAILABILITY-003](../../01-requirements/non-functional-requirements.md#nfr-availability-003-ai모델외부-api-장애-격리), [NFR-COST-001](../../01-requirements/non-functional-requirements.md#nfr-cost-001-ai임베딩mobility-호출-비용-상한)의 목표와 수치를 계약 기준으로 사용한다. 실제 부하 결과는 최종 완료 게이트 증거로 추가한다.

## 9. 확정 운영 규칙

- 작업 상태는 `QUEUED/RUNNING/SUCCEEDED/FAILED`, 완전성은 `COMPLETE/PARTIAL`, 시도 이력과 lease를 데이터 계약에 저장한다.
- Webhook·관리자 추가·초기 적립은 같은 멱등 키를 사용하고, 초기 적립은 `BACKFILL` 우선순위로 실시간 작업보다 낮게 처리한다.
- 종료 시 신규 claim을 중단하고 30초 drain 후 종료한다. lease 만료 작업은 재기동 후 재처리하며, 정식 등록은 기존 고유 제약과 명령 멱등성으로 중복을 막는다.
- quota·비용 hard stop에서는 신규 작업을 차단하고 수동 등록 fallback을 제공한다. 자동 모델 전환·자동 전체 재처리는 하지 않는다.
- 초기에는 단일 인스턴스·Worker 1개로 운영하고, 다중 인스턴스는 DB lease/claim으로 안전성을 확보한 뒤 최종 부하 증거와 별도 운영 승인 후에만 늘린다.
- 작업·Snapshot·시도·검수 이력은 1년 보존 후 정리하며 원본 영상·자동 수집 전체 자막·전체 응답은 저장하지 않는다. 관리자 보완 텍스트 암호문은 작업 종료 후 24시간 이내 삭제한다.
