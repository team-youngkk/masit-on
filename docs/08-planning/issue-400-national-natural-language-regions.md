---
status: review-requested
api_contract_status: approved
architecture_status: Proposed
issue: 400
related_documents:
  - README.md
  - ../01-requirements/functional-requirements.md
  - ../04-product/prd/discovery/natural-language-restaurant-discovery.md
  - ../05-specs/api/discovery/natural-language-restaurant-discovery-api.md
  - ../05-specs/api/common/region-contract.md
  - ../07-adr/architecture/arch-005-natural-language-filter-interpretation.md
  - ../07-adr/architecture/arch-006-national-natural-language-region-interpretation.md
  - third-expansion-evaluation-strategy.md
  - third-expansion-test-matrix.md
  - dynamic-natural-language-tag-dictionary.md
  - ../troubleshooting/pr-169-natural-language-search-review.md
  - ../troubleshooting/pr-176-natural-language-review.md
  - ../troubleshooting/pr-213-natural-language-filter-reset-review.md
  - ../troubleshooting/pr-371-dynamic-tag-dictionary-review.md
---

# 전국 자연어 지역 해석 구현 계획 — 이슈 #400

## 1. 범위와 승인 상태

2026-10-11 사용자가 [#400](https://github.com/team-youngkk/masit-on/issues/400)의 문서화 후 구현·PR 작성을 요청했다. #394의 활성 전국 지역 마스터와 조회 범위를 재사용해 FR-NLSEARCH-001~003·FR-RESTAURANT-003을 확장한다. API·지역 응답 계약은 아래 승인 기록으로 확정됐으며, 아키텍처는 새 [ADR-ARCH-006](../07-adr/architecture/arch-006-national-natural-language-region-interpretation.md)의 문서·CI 확인 후 최종 승인 대기다. 기존 팀 승인이나 과거 운영 평가 완료를 이 변경의 승인·검증 증거로 사용하지 않는다.

| 결정 대상 | 상태 | 근거와 남은 조건 |
|---|---|---|
| P2 regionCode·직접 district 호환·충돌·PARTIAL/FAILED API 응답 | 승인 완료 | [김인안 2026-10-11 승인](https://github.com/team-youngkk/masit-on/pull/403#issuecomment-6107809352); 변경 요청 없음 |
| P2 아키텍처 기록 방식 | 새 ADR 분리 결정 완료 | [이우람 2026-10-12 결정](https://github.com/team-youngkk/masit-on/pull/403#issuecomment-6116173635); ADR-ARCH-005 §11 확정 대신 ADR-ARCH-006 작성 |
| 새 ADR 최종 승인 | Proposed·승인 대기 | 새 ADR·인덱스·추적표·본 계획의 갱신과 수정 커밋 CI 확인 후 이우람 승인; 기존 P1·Golden V1은 보존 |

문서의 계약과 인수 조건을 먼저 작성한 뒤 구현한다. 새 의존성·마이그레이션·지역 마스터 변경·LLM·외부 호출은 추가하지 않는다. #398 코스·#399 지도·#401 전국 통합 인수는 별도 작업이다.

## 2. 지역 해석 계약

- Restaurant Application이 소유한 output Port로 활성 시·도와 활성 부모 아래 시·군·구 snapshot을 읽는다. 인프라 Adapter가 기존 마스터를 제공한다. 표시명과 확인된 시·도 별칭을 사용하며 마스터 후보를 하드코딩한 서울 25구 목록으로 대체하지 않는다.
- 부모·자식 이름을 함께 인식하면 해당 부모 안에서만 자식을 찾는다. 하위 이름만 주어지면 활성 후보가 정확히 한 개일 때만 확정한다. `중구`·`강서구` 등 동명 이름을 서울로 임의 귀속하지 않는다. 서로 맞지 않는 부모·자식도 미해결이다.
- 기존 서울 동네 별칭은 기존 서울 지역에 귀속할 수 있으나 동명 자치구의 모호성을 우회할 수 없다. 비자치구는 기존 2단계 마스터 귀속 규칙을 따른다. 폐지 이름의 주소 정규화 규칙을 검색 별칭으로 무조건 적용하지 않는다.
- 자연어 확정 지역은 `appliedConditions.regionCode`로 반환하며 `district`는 `null`이다. 직접 `filters.regionCode` 또는 레거시 `filters.district`가 같은 지역 차원의 자연어 조건보다 우선한다. 직접 district 적용 시 regionCode는 null이다. 동일 유효 지역이면 충돌을 표시하지 않는다. 전국 지역 충돌·미해결의 필드는 `regionCode`이며 `DIRECT_FILTER_WON`·`UNRESOLVED_VALUE`를 재사용한다. 기존 district 호환 필드는 허용한다.
- 직접 두 지역 필터의 동시 지정·미등록·비활성 코드는 `400 INVALID_FIELD_VALUE`다. 의심 입력으로 조회를 생략해도 직접 지역·카테고리·불투명 유튜버 ID·태그 검증을 수행한다. 의심 입력은 유효한 직접 지역이 있어도 `FAILED`·빈 적용 조건·빈 결과다.
- 미등록·비활성·틀린 지역은 미해결로 남기며 맛집 이름 조건으로 바꾸지 않는다. 다른 유효 조건이 있으면 `PARTIAL`, 없으면 `FAILED`·빈 목록이다. 유효한 지역에 맛집이 없으면 `APPLIED` 또는 `PARTIAL`·정상 빈 목록이다. 전체 목록 대체는 금지한다.
- 시·도는 자신과 활성 자식, 하위는 자신만 조회한다. 세종은 자신, 제주는 제주시·서귀포시를 포함한다. 이름·카테고리·유튜버·동일 Visit의 태그 AND·정렬·페이지·공개 상태는 기존 의미를 유지한다.
- 지역 사전 DB 장애는 `NATURAL_LANGUAGE_UNAVAILABLE` 503으로 처리한다. 서울 정적 사전이나 stale snapshot으로 대체하지 않는다. 지역 cache는 새로 도입할 필요가 없다. 동적 태그 사전의 30초 TTL·동시 갱신·만료 실패 503·stale/seed 대체 금지는 그대로 유지한다.

## 3. 버전과 평가 자산

전국 지명·동명 후보·충돌 판정은 알고리즘 변경이므로 운영 응답은 `parserVersion: P2`다. 기존 240건 Golden V1의 문장·정답·분할·버전과 과거 평가 기록은 수정하지 않는다. 역사적 P1 경로의 회귀로 격리하고, P2는 별도 전국 Golden 보충 자산을 추가한다. P1 점수를 P2 정확도나 최신 운영 품질로 보고하지 않는다. 태그 데이터 변경 자체는 알고리즘 버전 증가 사유가 아니다.

| 인수 사례 | 기대 결과 |
|---|---|
| `서울 마포구 한식`, `부산 해운대구 한식`, `경기도 수원시 한식` | 활성 해당 하위 regionCode와 한식 AND, P2 |
| `서울 중구`, `부산 중구` | 각 부모 아래 서로 다른 지역 코드 |
| `중구`, `강서구` | 동명 활성 후보 미해결, 다른 조건 없으면 FAILED·빈 결과 |
| 유일한 하위 이름, 시·도 단독 | 하위 자신 또는 상위 자신+활성 자식 범위 |
| `세종 맛집`, `제주 맛집`, `제주 서귀포시` | 세종 자신, 제주 상위 범위, 서귀포 하위 범위 |
| 미등록·비활성·잘못된 이름, 부모·자식 불일치 | UNRESOLVED, 이름 검색이나 전체 목록으로 대체하지 않음 |
| 자연어 지역과 동일 직접 코드/서울 district | 직접 필터 적용, conflict 없음 |
| 서로 다른 직접 지역, 미해결 지역+직접 지역 | 직접 필터 적용과 regionCode 충돌 안내 |
| 동시 직접 regionCode/district, 비활성/미등록 직접 코드 | 400, 의심 입력 경로도 동일 검증 |
| 의심 입력+유효 직접 코드 | FAILED·빈 적용 조건·빈 목록, 직접 필터는 검증 |
| 지역+이름/음식/opaque 유튜버/동일 Visit 태그 | 기존 AND 의미와 공개 결과 유지 |
| 지역 사전 DB 실패, 태그 만료 갱신 실패 | 503, 정적·stale·seed 대체 금지 |
| 서울 기존 동네 별칭, Golden V1·동적 태그 경계 | 역사적 P1 회귀 및 P2 명시 사례로 별도 확인 |

평가자는 EVAL-NL-001~007의 조건·안전·우선·공개·복구·태그를 별도로 판정한다. 모든 필수 사례를 통과해야 하며 평균 점수로 모호 지역 오적용을 상쇄하지 않는다. 새로운 정답 승인과 평가 결과는 PR의 실측 검증으로 기록한다.

## 4. 화면과 회귀 경계

화면 적용 요약의 지역명은 공개 `/api/regions` 트리에서 코드→사용자용 표시명으로 해석한다. 이름 확인 실패 시 안전한 일반 지역 안내를 표시하고 원시 코드·내부 ID를 그대로 노출하지 않는다. 지역 충돌 안내에도 같은 라벨을 사용한다. URL 직접 지역 변경은 문장·태그 선택·이전 결과를 초기화하고 페이지 이동만으로 초기화하지 않는다.

PR #169의 의심 입력·조회 없는 직접 검증·동일 값 비충돌·opaque Creator ID, PR #176의 코드 비노출·조건 경계·평가 분리, PR #213의 화면 상태 초기화, PR #371의 동적 태그 경계 회귀를 유지한다. 실제 Kakao·YouTube API는 테스트에서 호출하지 않는다.

## 5. 순서와 완료 조건

1. 요구사항·PRD·API·지역 계약·독립 ADR-ARCH-006과 추적표·평가 조건을 문서화한다.
2. 지역 Port/Adapter 공급, P2 해석·직접 필터 병합·실패 경로를 구현한다.
3. 화면 지역 라벨과 충돌 표시를 반영한다.
4. parser·Service·MockMvc·PostgreSQL 범위·UI 회귀, 기존 Golden V1·ArchUnit과 컴파일을 실행하고 독립 리뷰를 수행한다. 실행하지 못한 항목은 명시한다.
5. 저장소 PR 템플릿으로 develop 대상 PR을 작성하고 `Closes #400`, 사용자 assignee·변경 범위 라벨·소유자 리뷰 요청·실측 검증 결과를 기록한다.

1~5절은 계약·인수 계획이며 실측 구현·검증 결과는 6절에서 구분한다. 계획 작성이나 자동화 통과만으로 운영 완료를 선언하지 않는다.

## 6. 구현·검증 기록과 이번 결정 반영

[PR #403](https://github.com/team-youngkk/masit-on/pull/403)의 기존 구현은 승인된 API 계약과 일치한다. 이번 결정은 ADR 분리·승인 상태와 링크를 바로잡는 문서 변경이며 코드의 조건 해석·응답 변경을 요구하지 않는다.

develop 동기화 커밋 `9d643a4` 기준 [CI](https://github.com/team-youngkk/masit-on/actions/runs/38127809534)는 백엔드·프론트·RSA 검사·Terraform 계약 검사에서 통과했다. 기존 WireMock 12건의 로컬 전체 빌드 HTTP 연결 오류는 단독 재실행과 해당 CI에서 재현되지 않았고, 최초 환경 원인을 특정한 것은 아니다. 이번 문서 수정 커밋의 CI는 별도로 확인한다. #401 실제 백엔드·브라우저 통합 인수와 실사용 부하·운영 완료는 이 기록으로 대체하지 않는다.
