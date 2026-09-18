---
id: ADR-EXT-005
title: 등록 후 Kakao 장소 주기 재검증과 안전 보정
status: Proposed
decision_date: 2026-09-18
last_reviewed: 2026-09-18
owners:
  - 이우람
related_requirements:
  - NFR-EXTERNAL-002
  - NFR-INTEGRITY-003
related_documents:
  - ext-001-reference-verification.md
  - ../../05-specs/api/admin/restaurant-place-revalidation-api.md
  - ../../05-specs/data/table-definitions.md
  - ../../08-planning/issue-377-kakao-place-revalidation.md
supersedes: []
superseded_by: null
---

# ADR-EXT-005 등록 후 Kakao 장소 주기 재검증과 안전 보정

## 1. 상태

`Proposed`. Issue #377 구현과 코드 리뷰를 위한 제안이며, 운영에서 Worker 또는 관리자 수동 실행을 활성화하기 전 담당자 승인과 quota 확인이 필요하다.

## 2. 결정 제안

등록된 활성 Restaurant를 설정된 주기로 작은 batch 단위 재검증한다. Kakao Local 호출은 claim transaction 밖에서 수행하고, 결과 반영·상태 전이·감사 INSERT는 execution ID와 lease를 조건으로 한 짧은 CAS transaction에서 처리한다. 정상·검토 완료 상태는 다음 주기의 due 시각을 보존하며, `RETRY_EXHAUSTED`만 자동 주기에서 제외한다.

- 같은 Kakao place ID와 URL이 확인된 경우에만 비교 결과를 신뢰한다.
- 상호명, 전화번호, 같은 자치구의 도로명주소, 완전한 좌표 쌍은 자동 보정할 수 있다.
- place ID/URL 불일치, 자치구 변경, 모호한 검색 결과는 기존 값을 유지하고 `REVIEW_REQUIRED`로 남긴다.
- 매칭 결과가 없으면 `MATCH_NOT_FOUND`로 남긴다.
- 429·5xx·timeout은 기존 값을 변경하지 않고 `RETRY_SCHEDULED`와 bounded backoff를 기록하며, 상한 도달 시 `RETRY_EXHAUSTED`로 운영 확인 대상이 된다.
- 관측값·이전 값·적용 값을 JSONB append-only 감사로 보존한다. Kakao place ID는 관리자 API 응답과 로그에 노출하지 않는다.

## 3. 기존 ADR과의 관계

ADR-EXT-001의 MVP 범위는 등록 시점 확인과 자동 주기 동기화를 제외한다. 이 제안은 Issue #377을 후속 확장으로 다루며, 기존 MVP 외부 호출 금지를 코드 기본값 `RESTAURANT_PLACE_REVALIDATION_ENABLED=false`로 보존한다. Proposed 상태에서 운영 설정을 활성화하지 않으며, 수동 관리자 실행도 같은 flag로 차단한다.

## 4. 운영·검증 조건

실제 Kakao API를 테스트에 사용하지 않고 WireMock으로 정상·변경·매칭 실패·429·5xx·timeout을 검증한다. 운영 활성화 전에 Kakao quota, 호출 주기, batch 상한, retry/backoff, stale lease 회복과 PostgreSQL claim 계획을 측정·승인한다. 외부 호출 실패나 lease CAS 실패는 Restaurant 본문을 변경하지 않아야 한다.
