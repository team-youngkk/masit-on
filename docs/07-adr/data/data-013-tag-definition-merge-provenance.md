---
id: ADR-DATA-013
title: 태그 정의 병합 경로와 VisitTag provenance
status: Accepted
decision_date: 2026-09-09
owners:
  - 김인안
related_requirements:
  - FR-ADMIN-007
  - BR-ADMIN-011
related_documents:
  - ../../01-requirements/functional-requirements.md
  - ../../01-requirements/business-rules.md
  - ../../05-specs/api/admin/tag-definition-api.md
  - ../../05-specs/data/third-expansion-ai-video-data-contract.md
  - ../../08-planning/tag-definition-merge.md
  - ../adr-index.md
  - ../adr-traceability.md
supersedes: []
superseded_by: null
---

# ADR-DATA-013 태그 정의 병합 경로와 VisitTag provenance

## 1. 상태

Accepted

## 2. 결정 요약

중복 태그 병합은 원본 정의를 물리 삭제하거나 용어를 대상에 복사하지 않는다. 원본별 단일 append-only 병합 경로를 남기고 원본을 `DEPRECATED`로 전환한다. 자연어 사전은 원본 용어를 경로의 최종 `ACTIVE` 대상으로 해석한다. VisitTag가 중복되어 한 행을 제거할 때는 제거되는 원본과 유지되는 대상의 변경 전 snapshot을 별도 append-only provenance에 보존한다.

## 3. 배경과 문제

VisitTag에는 AI 신뢰도·근거·추출기 버전·후보 snapshot과 관리자 보정 출처가 있다. 단순히 정의 FK를 일괄 변경하면 같은 방문의 `(visit_id, tag_definition_id)` unique와 충돌하고, 충돌한 원본 행을 삭제하면 근거를 잃는다. 용어를 대상 별칭으로 복사하면 직접 별칭 20개 상한과 전역 용어 소유권도 흔들린다.

## 4. 고려한 선택지

- 원본 정의와 VisitTag를 물리 삭제하고 대상에 다시 생성
- 원본 용어를 대상 정의로 이동하고 대상 VisitTag만 유지
- 병합 경로와 VisitTag 변경 전 provenance를 별도 ledger에 보존

## 5. 결정

- `tag_definition_merge`는 원본·대상, 정의 snapshot, 버전, fingerprint, 사유·행위자·시각과 영향 건수를 원본별 한 번 기록한다.
- 대상 연결이 없는 방문은 원본 VisitTag의 정의 FK만 바꿔 행과 근거를 보존한다.
- 대상 연결이 이미 있으면 기존 대상 행을 유지하고 원본·대상 snapshot을 기록한 뒤 원본 행을 삭제한다.
- 원본 용어는 원본 소유로 유지하며 자연어 조회에서 최종 활성 대상으로 해석한다.
- 두 ledger는 append-only이며 자동 역병합을 제공하지 않는다.

## 6. 선택 근거

병합 뒤 검색 코드는 하나로 수렴하면서도 AI와 관리자 판단의 역사적 사실을 재현할 수 있다. 용어 소유권과 직접 별칭 상한을 바꾸지 않으므로 기존 생성·수정 계약과도 충돌하지 않는다.

## 7. 트레이드오프

중복 제거된 VisitTag는 현재 테이블에서 사라지고 provenance 조회가 필요하다. 자연어 사전 조회에는 병합 경로 재귀 해석이 추가된다. 대신 일반 검색과 VisitTag unique 구조를 유지하고 수동 복구 자료를 완전하게 남긴다.

## 8. 강제 규칙

- 같은 유형의 서로 다른 `ACTIVE` 정의만 병합한다.
- 원본·대상 version과 미리보기 fingerprint를 잠금 뒤 재검증한다.
- 관리자 방문 태그 교체는 기존·요청 정의를 먼저 공유 잠금한 뒤 Visit을 잠그고 version을 재검증하여 병합과의 잠금 순서를 통일한다.
- VisitTag 변경, provenance, 원본 비활성화, 상태 감사와 병합 감사를 한 트랜잭션에서 처리한다.
- 병합 원본을 재활성화하거나 ledger를 수정·삭제하지 않는다.
- 복구는 snapshot을 근거로 검증된 전진 변경으로 수행한다.

## 9. 검증 방법

- 이동과 중복 제거에서 식별자·근거·양쪽 snapshot·건수를 검증한다.
- stale version/fingerprint, 상태·유형·자기 병합과 두 번째 병합을 충돌로 검증한다.
- V12→V13 전진 적용과 append-only DB 제약을 PostgreSQL Testcontainers로 검증한다.
- 원본 용어가 최종 활성 대상 코드로 해석되는지 자연어 사전 통합 테스트한다.

## 10. 재검토 조건

병합 취소를 제품 기능으로 제공하거나 병합 경로가 운영상 과도하게 길어지면 역연산 계약, 승인 흐름, 경로 압축과 provenance 복원 정책을 새 ADR로 결정한다.
