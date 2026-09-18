---
related_documents:
  - ../05-specs/api/admin/restaurant-place-revalidation-api.md
  - ../05-specs/data/table-definitions.md
  - ../05-specs/data/migration-plan.md
  - ../07-adr/integration/ext-001-reference-verification.md
  - ../07-adr/integration/ext-005-kakao-place-periodic-revalidation.md
---

# Issue #377 Kakao 장소 재검증 구현 계획

## 범위

등록 후 활성 Restaurant를 Kakao Local로 재검증하고, 안전한 필드 변경은 자동 보정하며 동일성 충돌·매칭 실패는 관리자 확인 상태로 남긴다. 외부 실패·quota 초과는 기존 유효 데이터를 유지하고 재시도 상태와 감사 이력을 기록한다.

## 상태와 판정

| Kakao 결과 | 저장 상태 | Restaurant 변경 |
|---|---|---|
| 동일 장소·변경 없음 | `VERIFIED` | 없음 |
| 동일 place ID/URL·안전 필드 변경 | `AUTO_CORRECTED` | name·phone·좌표 쌍·동일 구 주소만 |
| ID/URL 불일치·자치구 변경·모호한 결과 | `REVIEW_REQUIRED` | 없음 |
| 검색 결과 없음 | `MATCH_NOT_FOUND` | 없음 |
| 429·5xx·timeout | `RETRY_SCHEDULED` / `RETRY_EXHAUSTED` | 없음 |

## 안전 경계

Worker claim과 완료 반영은 lease·execution ID CAS로 분리한다. 외부 호출 중 DB transaction을 유지하지 않는다. 감사 INSERT와 상태 변경은 같은 transaction에서 처리하고, append-only 감사 행의 UPDATE/DELETE를 DB trigger로 거부한다. 운영 Worker는 설정상 기본 비활성화이며 ADR-EXT-005 승인과 quota 확인 후 활성화한다.
