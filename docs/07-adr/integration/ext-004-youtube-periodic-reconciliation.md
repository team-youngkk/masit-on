---
id: ADR-EXT-004
title: 활성 YouTube 감시 채널의 주기적 누락 보정
status: Accepted
decision_date: 2026-09-16
owners:
  - 이우람
related_requirements:
  - FR-AIEXTRACT-004
  - FR-AIEXTRACT-006
  - NFR-COST-001
related_documents:
  - ../adr-backlog.md
  - ext-003-ai-extraction-async-reliability.md
  - ../../04-product/prd/admin/ai-video-information-extraction.md
  - ../../05-specs/api/admin/ai-video-extraction-api.md
  - ../../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../../08-planning/third-expansion-scope-and-terminology.md
  - ../../troubleshooting/pr-385-youtube-channel-backfill-review.md
supersedes: []
superseded_by: null
---

# ADR-EXT-004 활성 YouTube 감시 채널의 주기적 누락 보정

## 1. 결정과 근거

Accepted. 2026-09-16 PR 작성자가 자동 보정을 채택하고 운영 주기를 필수 설정으로 두는 방향을 승인했다. [이슈 #375](https://github.com/team-youngkk/masit-on/issues/375)와 FR-AIEXTRACT-004의 누락 보정 요구를 유지하며 API·데이터 계약의 수동 실행 한정을 해제한다.

ADR-AUTO-001의 일반 수집·동기화 보류 중 **관리자가 활성화하고 구독 검증을 마친 YouTube 채널의 누락 영상 식별자 보정**만 분리해 승인한다. 원본 영상·전체 자막 수집, 전체 채널 무차별 감시, 정식 데이터 주기 동기화, 자동 전체 재추출은 승인하지 않는다. 기존 Spring Scheduler·PostgreSQL·Redis를 사용하며 새 라이브러리·서비스는 추가하지 않는다.

## 2. 실행·중지 계약

- `YOUTUBE_BACKFILL_ENABLED=false`가 기본값이다. 활성화하려면 `YOUTUBE_BACKFILL_RUN_INTERVAL_SECONDS`를 `1..2147483647` 범위의 정수 초로 명시해야 한다. 기본 `0`은 미설정이며 활성 상태에서는 기동·파일 배포 사전검사를 거부한다. 실제 값은 채널 수·quota·허용 지연을 확인한 운영 설정으로 정한다.
- 전용 스케줄러는 30초 polling마다 도래 채널을 최대 20개 접수한 뒤 기존 Worker의 한 페이지를 처리한다. 최초 `enabled=true/ACTIVE` 채널은 즉시 대상이다. 이후 마지막 실행의 종결 갱신 시각에서 설정 주기가 지나면 대상이 된다. DB에 저장된 갱신 시각을 기준으로 판단하므로 프로세스 재기동은 주기를 초기화하지 않는다.
- 채널에 `QUEUED/RUNNING` 실행이 있으면 추가 생성하지 않는다. 도래 채널은 오래 기다린 순으로 처리하고 Watch 행을 `FOR UPDATE SKIP LOCKED`로 잠근다. 수동 시작도 같은 Watch 잠금을 사용하며 활성 실행 partial unique가 최종 중복 방지 경계다.
- 성공한 실행의 다음 주기는 첫 페이지에서 시작해 새 누락 영상을 확인한다. `FAILED` 또는 페이지·영상 상한 중지는 다음 주기에 같은 run의 저장 Cursor에서 재개한다. 페이지·스캔 구간 카운트와 마지막 오류는 초기화하지만 영상별 원장·신규·재사용 누계는 유지한다. 실패 즉시 반복 호출하지 않는다.
- `STOPPED/MANUAL`은 자동 접수 대상에서 제외한다. 관리자의 명시적 시작으로 새 실행을 만들어야 자동 주기 대상에 다시 들어간다. Watch 비활성·미검증은 생성·claim·영상 접수 모두 차단한다. 지속 중지는 Watch 비활성화로 제어한다.
- polling·대기 실행·페이지 처리·quota 차단 때문에 설정 주기만으로 완료 시간 SLA가 보장되지는 않는다. 초기 적립 및 상한 Cursor 진행은 여러 실행 구간이 걸릴 수 있다. 운영자는 채널 수·신규 영상량·페이지 상한·지연 지표와 quota를 함께 확인한다.

## 3. 비용·원자성

외부 조회는 자동 접수 트랜잭션 밖에서 수행한다. 기존 provider·백필 quota 원자 예약과 fail-closed, 페이지·영상 상한, BACKFILL 우선순위를 유지한다. Job·원장·누계는 동일 트랜잭션에 저장하고 Webhook과 기존 Job 멱등성 키로 수렴한다. 영상 원문이나 비밀정보를 저장하지 않는다.

자동화는 운영 설정을 검증하고 실행을 예약할 수 있는 기능 승인이다. 이 변경만으로 실제 운영 flag·주기·quota를 변경하거나 외부 API를 호출하지 않는다. API key, quota, 주기가 빠지면 활성화하지 않는다.

## 4. 대안과 선택 이유

수동 실행으로 요구사항을 축소하면 알림 누락이 관리자 조작 전까지 복구되지 않아 #375의 목적을 잃는다. 별도 Queue나 범용 Batch는 기존 DB 실행·lease 경계로 해결할 수 있는 범위를 넘는다. 기존 Worker 앞에 제한된 자동 접수 트랜잭션을 추가하는 방식으로 결정했다.

## 5. 검증과 재검토

정상 첫 실행·주기 경계·완료 후 새 실행, 실패/상한 Cursor 재개, 수동 중지·비활성·미검증 제외, 두 스케줄러 동시 접수의 1건 수렴을 PostgreSQL에서 검증한다. 운영 주기 누락·0·음수 거부, Compose와 파일 기반·SSM 실행 경로 전달도 검증한다.

운영 담당자는 #375에서 실제 주기·quota를 설정할 때 보정 지연·실패·차단 건수와 실시간 처리 영향을 확인한다. 20개 접수·단일 페이지 Worker로 허용 지연을 만족하지 못하면 처리량과 최신 영상 우선 전략을 별도 근거와 함께 재검토한다.
